# CULINARY 数据库并发与事务安全方案

**文档编号**：E02
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**关联决策**：D-003、D-009

---

## 1. 运行环境假设

| 项 | 约束 |
|---|---|
| 进程模型 | **单进程**；WorkManager 默认同进程；不使用 `android:process` |
| 数据库 | Android 平台 SQLite（Room `SupportSQLiteDatabase`） |
| 连接 | 一个 `CulinaryDatabase` 单例；Room 管理连接池 |
| 写者 | **应用级单写者**：所有写事务串行 |
| 读者 | 多个并发读；WAL 下读不阻塞写、写不阻塞读 |
| 多设备 | 无同步；每台设备独立数据库 |
| 多用户 | 单用户；无并发用户会话 |
| 备份/恢复 | 备份获取写锁；恢复关闭数据库并替换文件 |

**明确不支持：** 多进程同时打开同一数据库、第二个 `SQLiteDatabase.openDatabase` 连接、外部进程直接改 DB 文件。若未来引入，必须重新设计并新增 ADR。

## 2. 为什么选择“WAL + 单写者”

SQLite 的并发模型是：

- 同一时刻只允许一个写事务；
- WAL 模式允许写者与读者并发；
- 多个写者会争抢写锁，容易出现 `SQLITE_BUSY`，且重试逻辑复杂。

CULINARY 是单用户、低频写入（一次计划确认、一次收货、一次调整），写入量远小于读取量。**单写者队列 + WAL** 可以在不牺牲读取并发的前提下，把写冲突降到最低，并让事务顺序可预测。

不采用“读写分离”的原因：

- 单机 SQLite 没有独立的读库/写库；
- WAL 已经提供读并发；
- 手工再开一个连接做“读库”会引入一致性、关闭顺序和锁竞争问题；
- 本项目没有高并发报表需求。

## 3. 数据库配置

### 3.1 Room 构建

```kotlin
@Provides
@Singleton
fun provideDatabase(
    context: Context,
    writeExecutor: ExecutorService,
    readExecutor: ExecutorService,
): CulinaryDatabase =
    Room.databaseBuilder(context, CulinaryDatabase::class.java, "culinary.db")
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryExecutor(readExecutor)          // 固定大小读线程池
        .setTransactionExecutor(writeExecutor)   // 单线程写执行器
        .addMigrations(*AllMigrations)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
                db.execSQL("PRAGMA busy_timeout = 5000")
                db.execSQL("PRAGMA synchronous = NORMAL")
                // 记录实际 journal_mode，验证 WAL 生效
            }
        })
        .build()
```

**说明：**

- `setTransactionExecutor` 用单线程执行器，使 Room 的事务提交顺序固定；应用层 `Mutex` 再保证不并发进入。
- `setQueryExecutor` 用固定大小（建议 4）线程池；读多时不会挤占写线程。
- `allowMainThreadQueries()` **禁止**。
- `fallbackToDestructiveMigration()` **禁止**。
- `setAutoCloseTimeout` 不使用：自动关闭会让连接/事务生命周期不可预测。
- 自定义 Executor 必须由 App 在进程结束时关闭；不要每次打开 DB 新建线程池。

### 3.2 必须验证的 PRAGMA

启动自检记录以下实际值（不是只写配置）：

| PRAGMA | 期望 | 失败处理 |
|---|---|---|
| `journal_mode` | `wal` | 记录警告；若设备不支持 WAL，降级为 DELETE 并限制并发写 |
| `foreign_keys` | `1` | 阻止写入；视为配置错误 |
| `synchronous` | `1`（NORMAL） | 记录实际值 |
| `busy_timeout` | `5000` | 记录实际值；驱动可能按连接生效，需在目标 API 验证 |
| `user_version` | 当前 schema | 由迁移守卫检查 |

> **重要：** Android 的 SQLite 连接池可能让 `PRAGMA` 只在执行它的连接上生效。`foreign_keys` 必须用 `setForeignKeyConstraintsEnabled(true)` 这类连接级 API；`busy_timeout` 需在 API 26/33/36 上实测。不要因为写了 PRAGMA 就假设所有连接都生效。

### 3.3 同步模式取舍

- `synchronous=NORMAL`：WAL 下崩溃/断电可能丢失最后一个事务，但数据库保持一致。适合个人应用，性能更好。
- `synchronous=FULL`：更强持久性，写入更慢。若用户对“刚保存就断电”极敏感，可评估切换；本阶段默认 NORMAL，并通过“保存后立即备份”降低风险。
- 任何切换必须做写入性能与断电测试，不凭感觉。

## 4. 连接与写队列

### 4.1 单例

- `CulinaryDatabase` 由 Hilt `@Singleton` 提供。
- 不允许在 Repository/Feature 内 `Room.databaseBuilder`。
- 不允许 `database.close()` 后继续使用；恢复/退出流程统一管理生命周期。

### 4.2 写队列

```kotlin
interface TransactionRunner {
    suspend fun <T> write(block: suspend TransactionScope.() -> T): Outcome<T>
}

class RoomTransactionRunner @Inject constructor(
    private val db: CulinaryDatabase,
    private val dispatchers: DispatcherProvider,
    private val writeMutex: Mutex,
) : TransactionRunner {

    override suspend fun <T> write(
        block: suspend TransactionScope.() -> T,
    ): Outcome<T> = withContext(dispatchers.io) {
        writeMutex.withLock {
            try {
                val result = db.withTransaction {
                    block(RoomTransactionScope(db))
                }
                Outcome.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.Failure(DbErrorMapper.map(e))
            }
        }
    }
}
```

**规则：**

1. `Mutex` 只包住写事务，不包读取。
2. 只有 Use Case 调用 `TransactionRunner.write`；Repository 方法不自行开事务。
3. 禁止嵌套 `write`：Kotlin `Mutex` 不可重入，嵌套会死锁。
4. 事务内不调用 `withContext(Dispatchers.IO)` 做文件/网络；直接在事务线程执行 SQL。
5. 事务内不弹 UI、不等待用户输入。
6. 事务内不获取其他锁（文件锁、图片锁），避免锁顺序反转。

### 4.3 为什么 Repository 不自己开事务

如果每个 Repository 方法都开事务，Use Case 组合多个 Repository 时会出现：

- 嵌套事务（Room 支持，但语义复杂）；
- 部分提交（一个 Repository 成功、另一个失败）；
- 锁持有时间不可控；
- 重试边界不明确。

因此：**事务边界在 Use Case，Repository 只提供原子操作片段。**

## 5. 事务边界规范

| 场景 | 事务 |
|---|---|
| 单条档案保存 | 一个写事务（Use Case） |
| 发布配方 | 一个写事务：版本状态 + 组成 + 当前指针 + 日志 |
| 确认计划 | 一个写事务：状态 + 快照 + 需求 + 来源 + 预留 + 流水 |
| 收货过账 | 一个写事务：收货单 + 明细 + 批次 + 流水 + 采购明细 + 状态 |
| 库存调整 | 一个写事务：条件更新余额 + 流水 |
| 备份 | 不是数据库事务；获取写锁 + checkpoint + 文件复制 |
| 恢复 | 关闭 DB + 文件替换 + 重新打开 + 迁移 |
| 批量导入 | 一个大事务或分批 + 最终切换；优先 staging 后原子替换 |

### 5.1 事务内允许

- 读取当前状态并做条件校验。
- 多条 INSERT/UPDATE/DELETE。
- 写 `change_log`。
- 调用纯领域计算（不 I/O）。

### 5.2 事务内禁止

- 网络请求。
- 图片解码/压缩。
- 大文件复制（备份流程例外，且不是 SQL 事务）。
- 用户交互。
- 长循环（超过 1000 行的大批量写要分批）。
- 记录大量日志到文件（日志走内存队列/异步）。

## 6. 锁的获取与释放

### 6.1 应用级锁

| 锁 | 范围 | 获取顺序 |
|---|---|---|
| `writeMutex` | 所有写事务 | 最外层，先获取 |
| SQLite 写锁 | 事务期间 | 由 Room/SQLite 管理 |
| 行级条件更新 | 单条 UPDATE | 事务内 |

**锁顺序：** `writeMutex → SQLite 事务 → 行更新`。禁止反向。

### 6.2 不使用的锁

- 不为“读”加全局锁；WAL 已提供读并发。
- 不用 `synchronized` 包 suspend 函数。
- 不用 `ReentrantLock` 跨协程挂起点。
- 不用文件锁做数据库互斥。

### 6.3 释放

- `writeMutex.withLock` 的 `finally` 自动释放；异常/取消都释放。
- SQLite 事务由 Room `withTransaction` 保证提交/回滚。
- 禁止手动 `beginTransaction` 后忘记 `endTransaction`；统一用 `withTransaction`。
- 恢复/关闭数据库前必须等待所有写事务完成（`writeMutex` 获取 + 短暂持有）。

## 7. 并发写入控制

### 7.1 条件更新（防超卖/负库存）

```sql
UPDATE inventory_batch
SET quantity_on_hand = quantity_on_hand + :onHandDelta,
    quantity_reserved = quantity_reserved + :reservedDelta,
    revision = revision + 1,
    updated_at = :now
WHERE id = :batchId
  AND quantity_on_hand + :onHandDelta >= 0
  AND quantity_reserved + :reservedDelta >= 0
  AND quantity_reserved + :reservedDelta <= quantity_on_hand + :onHandDelta
  AND revision = :expectedRevision;
```

- `rowsAffected == 1` 才继续写流水。
- `rowsAffected == 0`：区分“余额不足”与“版本冲突”，重新读取后再决定。
- 流水与余额在同一事务；流水写入失败则整个事务回滚。

### 7.2 乐观锁

- `inventory_batch.revision`、`production_plan.revision`、`purchase_order.revision` 用于检测并发修改。
- UI 编辑提交时带 `expectedRevision`；不匹配返回 `ConcurrentModification`，提示刷新。
- 不把 `revision` 用作安全令牌；只是冲突检测。

### 7.3 幂等

- 库存流水 `idempotency_key` 全局唯一。
- 收货过账、计划确认使用业务幂等键：
  - 收货：`receipt:<receiptId>:post`
  - 计划：`plan:<planId>:confirm:<revision>`
- 重复请求先查幂等键/状态，不重复写。
- 重试只重试“确定未提交”的事务；提交后异常不重试。

## 8. 超时、重试与锁冲突

### 8.1 超时

- 单个写事务目标 ≤ 500 ms；硬上限 5 s。
- 外层 `withTimeout(10_000)` 保护 UI 等待；SQLite 调用本身可能不可中断，取消后让当前语句结束，再释放锁。
- 超时返回 `DbError.Timeout`，提示重试；不自动无限重试。

### 8.2 可重试错误

| 错误 | 可重试 | 说明 |
|---|---|---|
| `SQLITE_BUSY` / `SQLITE_LOCKED` | 是 | 最多 3 次，50/150/400 ms + 抖动 |
| `SQLITE_BUSY_SNAPSHOT` | 是 | 重新读取后重试 |
| `SQLITE_CONSTRAINT` | 否 | 业务/数据错误 |
| `SQLITE_CORRUPT` / `SQLITE_NOTADB` | 否 | 进入恢复 |
| `SQLITE_FULL` | 否 | 磁盘满，提示清理 |
| `SQLITE_READONLY` | 否 | 存储/权限问题 |
| `CancellationException` | 不重试 | 直接抛出 |

### 8.3 重试实现

```kotlin
suspend fun <T> retryBusy(maxAttempts: Int = 3, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: SQLiteException) {
            if (!e.isBusy() || attempt == maxAttempts - 1) throw e
            delay(backoffMillis(attempt) + Random.nextLong(0, 50))
            attempt++
        }
    }
}
```

**注意：**

- 重试必须包住**整个事务**，不能在事务中间重试单条 SQL。
- 重试前重新读取状态，不能复用旧实体。
- 有副作用的外部操作（文件复制）不放在可重试事务内。

## 9. 读取并发

- DAO 的 `Flow` 查询在 Room 的读线程池执行；不获取 `writeMutex`。
- 单个查询是语句级一致；多查询组合可能看到不同提交点，UI 允许这种“最终一致”。
- 业务决策（计划确认、库存分配）必须在写事务内重新读取，不能依赖 UI 之前读到的值。
- 列表分页；不一次性加载全部。
- 不在主线程收集/转换大列表；用 `flowOn(Default)`。
- `distinctUntilChanged` 减少无意义重组。

### 9.1 读一致性等级

| 场景 | 一致性 | 实现 |
|---|---|---|
| 列表/详情展示 | 最终一致 | 独立查询/Flow |
| 编辑表单校验 | 读后写，需复核 | 写事务内重新校验 |
| 计划确认 | 强一致 | 单写事务内读取 + 写入 |
| 库存出库 | 强一致 | 条件 UPDATE + 流水 |
| 备份 | 时点一致 | 写锁 + checkpoint + 复制 |

## 10. 长事务防范

- 事务内不做文件/网络/图片。
- 批量写入按 500~1000 行分批；每批一个事务，记录进度；失败可从最后成功批次重试。
- 迁移中的大批回填：优先单事务（数据量小）；超过 10 万行改为 staging 表 + 分批 + 原子切换。
- 禁止“打开事务后等用户确认”。
- 监控：记录事务耗时；超过 1 s 记录 WARN，超过 5 s 记录 ERROR 并附操作类型/行数。

## 11. WAL 维护

- WAL 文件会增长；定期 `PRAGMA wal_checkpoint(TRUNCATE)`（后台、非主线程）。
- 备份前必须 checkpoint，确保主 DB 文件包含已提交数据。
- 不在每次写入后 checkpoint（性能差）。
- 监控 `-wal` 大小；异常增长时检查长读事务/未关闭 Cursor。
- 读事务/Cursor 必须及时关闭；Room 的 Flow 会自动管理，但手写 Cursor 要 `use {}`。
- WAL 模式下不要手动删除 `-wal`/`-shm` 文件。

## 12. 数据库损坏检测与恢复

### 12.1 检测

| 时机 | 检查 |
|---|---|
| 启动打开失败 | 捕获 `SQLiteDatabaseCorruptException` / `SQLiteException` |
| 每周后台 | `PRAGMA quick_check` |
| 备份前 | `PRAGMA quick_check`（或对备份文件 `integrity_check`） |
| 迁移/导入后 | `foreign_key_check` + `quick_check` |
| 异常崩溃后首次启动 | `quick_check` |

### 12.2 恢复

1. **不删除**损坏文件；移动到 `databases/quarantine/culinary-<timestamp>.db`（连同 `-wal`/`-shm`）。
2. 进入 Recovery 页面，展示：
   - 最近成功备份；
   - 损坏文件大小/时间；
   - 诊断日志导出。
3. 用户确认后从最近“已验证”备份恢复。
4. 若无备份，提供“导出损坏文件”供人工抢救；不自动清空。
5. 恢复后运行 `quick_check` 与关键表计数校验。

### 12.3 预防

- 不在事务中强杀进程；测试进程死亡场景。
- 不在 DB 文件所在目录做手工替换（除恢复流程）。
- 备份校验使用独立只读连接，验证 `integrity_check`。
- 设备存储不足时阻止大写入/迁移。

## 13. 备份/恢复与并发

### 13.1 备份

1. 获取 `writeMutex`（阻止新写）。
2. `PRAGMA wal_checkpoint(TRUNCATE)`。
3. 复制 `culinary.db` 到临时文件。
4. 计算 SHA-256，打开临时文件做 `quick_check`。
5. 释放写锁。
6. 打包图片/manifest。
7. 校验整包，原子 rename 为最终备份文件。

- 复制期间读可以继续，写等待；保持锁时间尽量短。
- 大库复制可能数秒，UI 显示“正在创建一致性备份”。
- 备份失败删除临时文件，不覆盖旧备份。

### 13.2 恢复

1. 停止新写（获取写锁并保持）。
2. 关闭 Room 数据库。
3. 将当前 DB/图片移动到 `pre_restore_<timestamp>/`。
4. 复制备份 DB 到正式路径。
5. 打开 Room，执行必要迁移。
6. `quick_check` + `foreign_key_check`。
7. 替换图片目录。
8. 成功：删除临时目录（保留最近一次预恢复备份）。
9. 失败：关闭 DB，回滚文件，重新打开，报告错误。

恢复期间应用进入专用恢复界面，禁止其他页面写入；必要时重启进程。

## 14. 反模式清单

| 反模式 | 后果 |
|---|---|
| 在 Composable 里 `db.query` | 主线程卡顿、生命周期泄漏 |
| 每个 Repository 方法自己开事务 | 部分提交、嵌套锁 |
| 用 `GlobalScope.launch` 写库 | 进程死亡/竞态 |
| 直接 `UPDATE inventory_batch SET quantity_on_hand=...` | 余额与流水不一致 |
| 用 `SELECT` 后再 `UPDATE` 而不带条件 | 并发丢更新 |
| 捕获 `SQLiteException` 后静默 | 数据错误不可见 |
| 用 `fallbackToDestructiveMigration` | 升级丢数据 |
| 多进程打开同一 DB | 锁与一致性不可控 |
| 事务内解码图片/发网络 | 长事务、ANR |
| 手动删 `-wal`/`-shm` | 数据库损坏 |

## 15. 并发测试计划

| 测试 | 方法 | 通过标准 |
|---|---|---|
| 并发调整 | 20 个协程同时调整同一批次 | 最终余额 = 初始 + Σdelta；流水数 = 成功数 |
| 并发预留 | 10 个计划同时预留同一批次 | 预留 ≤ 在手；无超卖 |
| 读写并发 | 持续读 Flow 时写入 | 读不阻塞写；无 `SQLITE_BUSY` 泄漏 |
| 重试 | 注入 2 次 BUSY | 第 3 次成功；无重复流水 |
| 超时 | 注入慢 SQL | 返回 Timeout；锁释放 |
| 进程死亡 | 事务提交前 `am force-stop` | 重启后 DB 一致；无半条流水 |
| 备份并发 | 写入同时备份 | 备份一致；写入等待后成功 |
| 恢复并发 | 写入时恢复 | 写入被阻止；恢复后数据正确 |
| 损坏 | 截断 DB 文件 | 进入 Recovery；不自动删除 |
| 迁移失败 | 注入错误迁移 | 回滚 + 恢复预迁移备份 |

## 16. 监控指标

- 写事务 P50/P95/P99 耗时。
- `SQLITE_BUSY` 次数/重试次数。
- WAL 文件大小。
- DB 文件大小、空闲页比例。
- `quick_check` 结果。
- 备份耗时/失败率。
- 启动打开 DB 耗时。

这些指标写入本地诊断日志，不上传网络。

## 17. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；WAL、单写者、事务、重试、损坏恢复与测试 | 架构组 |
