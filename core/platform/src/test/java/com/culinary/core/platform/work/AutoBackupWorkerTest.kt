package com.culinary.core.platform.work

import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.platform.AutoBackupRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Worker 返回语义：成功、retry、failure、取消、输入/输出 Data。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupWorkerTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @After
    fun tearDown() {
        AutoBackupWorkerDependencies.installForTest(null)
    }

    @Test
    fun `returns success only with archive path and record id`() {
        val runner = FakeRunner(AutoBackupRunResult.Success("backup-1", "/tmp/backup-1.zip", "record-1"))
        AutoBackupWorkerDependencies.installForTest(runner)

        val result = runBlocking { worker(workDataOf(AutoBackupWorker.KEY_BACKUP_ID to "backup-1")).doWork() }

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertThat(output.getString(AutoBackupWorker.KEY_BACKUP_ID)).isEqualTo("backup-1")
        assertThat(output.getString(AutoBackupWorker.KEY_ARCHIVE_PATH)).isEqualTo("/tmp/backup-1.zip")
        assertThat(output.getString(AutoBackupWorker.KEY_RECORD_ID)).isEqualTo("record-1")
        assertThat(runner.request?.backupId).isEqualTo("backup-1")
    }

    @Test
    fun `passes the target directory from input data`() {
        val runner = FakeRunner(AutoBackupRunResult.Success("backup-2", "/tmp/a.zip", "record-2"))
        AutoBackupWorkerDependencies.installForTest(runner)

        runBlocking {
            worker(
                workDataOf(
                    AutoBackupWorker.KEY_BACKUP_ID to "backup-2",
                    AutoBackupWorker.KEY_TARGET_DIRECTORY to "/tmp/target",
                ),
            ).doWork()
        }

        assertThat(runner.request).isEqualTo(AutoBackupRequest("backup-2", "/tmp/target"))
    }

    @Test
    fun `maps a retryable result to retry`() {
        AutoBackupWorkerDependencies.installForTest(
            FakeRunner(AutoBackupRunResult.Retry("backup-3", "insufficient_storage")),
        )

        val result = runBlocking { worker().doWork() }

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }

    @Test
    fun `maps a non-retryable result to failure with a reason`() {
        AutoBackupWorkerDependencies.installForTest(
            FakeRunner(AutoBackupRunResult.Failure("backup-4", "permission_denied")),
        )

        val result = runBlocking { worker().doWork() }

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertThat(output.getString(AutoBackupWorker.KEY_REASON)).isEqualTo("permission_denied")
        assertThat(output.getString(AutoBackupWorker.KEY_BACKUP_ID)).isEqualTo("backup-4")
    }

    @Test
    fun `maps cancellation to failure and never success`() {
        val runner = FakeRunner(AutoBackupRunResult.Success("backup-5", "/tmp/a.zip", "record-5"))
        runner.error = CancellationException("cancelled")
        AutoBackupWorkerDependencies.installForTest(runner)

        val result = runBlocking { worker(workDataOf(AutoBackupWorker.KEY_BACKUP_ID to "backup-5")).doWork() }

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertThat(output.getString(AutoBackupWorker.KEY_REASON)).isEqualTo("cancelled")
        assertThat(output.getString(AutoBackupWorker.KEY_BACKUP_ID)).isEqualTo("backup-5")
    }

    @Test
    fun `fails when the runner cannot be resolved`() {
        AutoBackupWorkerDependencies.installForTest(null)

        val result = runBlocking { worker().doWork() }

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertThat(output.getString(AutoBackupWorker.KEY_REASON)).isEqualTo("runner_unavailable")
    }

    private fun worker(input: Data = Data.EMPTY): AutoBackupWorker =
        TestListenableWorkerBuilder<AutoBackupWorker>(context)
            .setInputData(input)
            .build()

    private class FakeRunner(
        private val result: AutoBackupRunResult,
    ) : AutoBackupRunner {
        var request: AutoBackupRequest? = null
        var error: Throwable? = null

        override suspend fun run(request: AutoBackupRequest): AutoBackupRunResult {
            this.request = request
            error?.let { throw it }
            return result
        }
    }
}
