package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.AppMetadataEntity
import com.culinary.core.database.entity.BackupRecordEntity
import com.culinary.core.database.entity.ChangeLogEntity
import com.culinary.core.database.entity.ImageAssetEntity
import com.culinary.core.database.model.BackupKindDb
import com.culinary.core.database.model.BackupStatusDb
import com.culinary.core.database.model.ChangeActionDb
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ImageRoleDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 横切表：图片主图唯一、日志只追加、元数据与备份约束。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossCuttingConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var images: ImageAssetDao
    private lateinit var changeLog: ChangeLogDao
    private lateinit var backups: BackupRecordDao
    private lateinit var metadata: AppMetadataDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    CulinaryDatabase::class.java,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        images = database.imageAssetDao()
        changeLog = database.changeLogDao()
        backups = database.backupRecordDao()
        metadata = database.appMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rejects a duplicate image relative path`() {
        runBlocking { images.insert(image(id = IMAGE_ID, path = "images/a.webp", primary = false)) }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { images.insert(image(id = SECOND_IMAGE_ID, path = "images/a.webp", primary = false)) }
        }
    }

    @Test
    fun `allows only one active primary image per owner`() {
        runBlocking { images.insert(image(id = IMAGE_ID, path = "images/a.webp", primary = true)) }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { images.insert(image(id = SECOND_IMAGE_ID, path = "images/b.webp", primary = true)) }
        }
    }

    @Test
    fun `allows a new primary image after the previous one is soft deleted`() {
        runBlocking {
            images.insert(image(id = IMAGE_ID, path = "images/a.webp", primary = true))
            images.softDelete(IMAGE_ID, at = NOW)
            images.insert(image(id = SECOND_IMAGE_ID, path = "images/b.webp", primary = true))
            assertThat(images.count()).isEqualTo(2)
        }
    }

    @Test
    fun `rejects updating a change log row`() {
        runBlocking { changeLog.insert(changeLog()) }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE change_log SET change_reason = 'tampered' WHERE id = '$LOG_ID'",
            )
        }
    }

    @Test
    fun `rejects deleting a change log row`() {
        runBlocking { changeLog.insert(changeLog()) }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM change_log WHERE id = '$LOG_ID'",
            )
        }
    }

    @Test
    fun `upserts app metadata by key`() {
        runBlocking {
            metadata.put(AppMetadataEntity(key = "install_id", value = "a", updatedAt = NOW))
            metadata.put(AppMetadataEntity(key = "install_id", value = "b", updatedAt = NOW + 1))
        }

        runBlocking {
            assertThat(metadata.count()).isEqualTo(1)
            assertThat(metadata.find("install_id")?.value).isEqualTo("b")
        }
    }

    @Test
    fun `rejects a backup record with an invalid schema version`() {
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                backups.insert(
                    BackupRecordEntity(
                        id = BACKUP_ID,
                        kind = BackupKindDb.MANUAL,
                        fileName = "culinary-1.zip",
                        createdAt = NOW,
                        sizeBytes = 1024,
                        sha256 = "0123456789abcdef",
                        schemaVersion = 0,
                        appVersionName = "0.1.0",
                    ),
                )
            }
        }
    }

    @Test
    fun `stores backup records with a valid status`() {
        runBlocking {
            backups.insert(
                BackupRecordEntity(
                    id = BACKUP_ID,
                    kind = BackupKindDb.MANUAL,
                    fileName = "culinary-1.zip",
                    createdAt = NOW,
                    sizeBytes = 1024,
                    sha256 = "0123456789abcdef",
                    schemaVersion = 1,
                    appVersionName = "0.1.0",
                    status = BackupStatusDb.VERIFIED,
                ),
            )
        }

        runBlocking { assertThat(backups.count()).isEqualTo(1) }
    }

    private fun image(
        id: String,
        path: String,
        primary: Boolean,
    ) = ImageAssetEntity(
        id = id,
        ownerType = ImageOwnerTypeDb.INGREDIENT,
        ownerId = OWNER_ID,
        role = if (primary) ImageRoleDb.PRIMARY else ImageRoleDb.PROCESS,
        relativePath = path,
        mimeType = "image/webp",
        byteSize = 2048,
        sha256 = "0123456789abcdef",
        createdAt = NOW,
    )

    private fun changeLog() =
        ChangeLogEntity(
            id = LOG_ID,
            entityType = "INGREDIENT",
            entityId = OWNER_ID,
            action = ChangeActionDb.UPDATE,
            afterJson = """{"name":"黄油"}""",
            createdAt = NOW,
        )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val OWNER_ID = "11111111-1111-4111-8111-111111111111"
        const val IMAGE_ID = "22222222-2222-4222-8222-222222222222"
        const val SECOND_IMAGE_ID = "23232323-2323-4232-8232-232323232323"
        const val LOG_ID = "33333333-3333-4333-8333-333333333333"
        const val BACKUP_ID = "44444444-4444-4444-8444-444444444444"
    }
}
