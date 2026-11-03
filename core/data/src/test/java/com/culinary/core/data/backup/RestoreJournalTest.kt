package com.culinary.core.data.backup

import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreJournalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val file: File by lazy { File(temporaryFolder.root, "restore.journal") }
    private val store: RestoreJournalStore by lazy { RestoreJournalStore(file) }

    @Test
    fun `round trips a journal with operations`() {
        val journal = journal()

        val written = store.write(journal)
        val read = store.read()

        assertThat(written).isInstanceOf(Outcome.Success::class.java)
        assertThat(read).isInstanceOf(Outcome.Success::class.java)
        assertThat((read as Outcome.Success).value).isEqualTo(journal)
    }

    @Test
    fun `writes atomically without leaving a temp file`() {
        store.write(journal())

        val leftovers =
            temporaryFolder.root
                .listFiles()
                ?.filter { it.name.endsWith(".tmp") }
                .orEmpty()
        assertThat(leftovers).isEmpty()
        assertThat(file.isFile).isTrue()
    }

    @Test
    fun `reports a missing journal as not found`() {
        val result = store.read()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-NF-001")
    }

    @Test
    fun `reports a corrupt journal instead of guessing`() {
        file.writeText("{not json")

        val result = store.read()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-004")
    }

    @Test
    fun `clears the journal idempotently`() {
        store.write(journal())

        assertThat(store.clear()).isInstanceOf(Outcome.Success::class.java)
        assertThat(store.clear()).isInstanceOf(Outcome.Success::class.java)
        assertThat(file.exists()).isFalse()
    }

    private fun journal() =
        RestoreJournal(
            restoreId = "restore-1",
            phase = RestorePhase.DB_CLOSING,
            operations =
                listOf(
                    RestoreOperation(
                        id = "op-1",
                        kind = RestoreOperationKind.MOVE,
                        source = "databases/culinary.db",
                        target = "rollback/restore-1/culinary.db",
                        state = RestoreOperationState.DONE,
                    ),
                ),
            stagingDir = "cache/restore-staging/restore-1",
            rollbackDir = "filesDir/rollback/restore-1",
            startedAt = "2026-11-04T01:30:00Z",
            updatedAt = "2026-11-04T01:30:01Z",
        )
}
