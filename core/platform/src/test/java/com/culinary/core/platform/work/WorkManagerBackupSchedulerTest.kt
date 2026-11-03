package com.culinary.core.platform.work

import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** WorkManager 调度骨架：唯一周期任务、取消与立即任务入队。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerBackupSchedulerTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var scheduler: WorkManagerBackupScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        scheduler = WorkManagerBackupScheduler(context)
    }

    @Test
    fun `schedules a unique periodic backup`() {
        scheduler.schedulePeriodicBackup()

        val infos =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.PERIODIC_WORK_NAME)
                .get()

        assertThat(infos).hasSize(1)
        assertThat(infos.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `does not duplicate an already scheduled periodic backup`() {
        scheduler.schedulePeriodicBackup()
        scheduler.schedulePeriodicBackup()

        val infos =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.PERIODIC_WORK_NAME)
                .get()

        assertThat(infos).hasSize(1)
    }

    @Test
    fun `cancels the periodic backup`() {
        scheduler.schedulePeriodicBackup()

        scheduler.cancelPeriodicBackup()

        val infos =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.PERIODIC_WORK_NAME)
                .get()
        assertThat(infos.single().state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `enqueues an immediate backup and returns its id`() {
        val result = scheduler.enqueueImmediateBackup()

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val id = (result as Outcome.Success).value
        val info = WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(id)).get()
        assertThat(info).isNotNull()
        assertThat(info?.id).isEqualTo(UUID.fromString(id))
        // 测试调度器会同步执行一次性任务，因此可能是 ENQUEUED 或已 SUCCEEDED。
        assertThat(info?.state).isAnyOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.SUCCEEDED,
        )
    }
}
