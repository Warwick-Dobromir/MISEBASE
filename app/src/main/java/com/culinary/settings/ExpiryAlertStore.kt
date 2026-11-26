package com.culinary.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InventoryExpiryAlert(
    val expiredProductCount: Int,
    val expiredBatchCount: Int,
    val archiveNames: List<String>,
    val scannedAtMillis: Long,
)

object ExpiryAlertStore {
    private lateinit var preferences: android.content.SharedPreferences
    private val _alert = MutableStateFlow<InventoryExpiryAlert?>(null)
    val alert: StateFlow<InventoryExpiryAlert?> = _alert.asStateFlow()

    fun initialize(context: Context) {
        if (!::preferences.isInitialized) {
            preferences = context.applicationContext.getSharedPreferences("inventory_expiry_alert", Context.MODE_PRIVATE)
            _alert.value = read()
        }
    }

    fun save(alert: InventoryExpiryAlert?) {
        check(::preferences.isInitialized) { "ExpiryAlertStore must be initialized before use" }
        if (alert == null) {
            preferences.edit().clear().apply()
        } else {
            preferences.edit()
                .putInt(KEY_PRODUCTS, alert.expiredProductCount)
                .putInt(KEY_BATCHES, alert.expiredBatchCount)
                .putStringSet(KEY_NAMES, alert.archiveNames.toSet())
                .putLong(KEY_SCANNED_AT, alert.scannedAtMillis)
                .apply()
        }
        _alert.value = alert
    }

    private fun read(): InventoryExpiryAlert? {
        val products = preferences.getInt(KEY_PRODUCTS, 0)
        val batches = preferences.getInt(KEY_BATCHES, 0)
        if (products <= 0 || batches <= 0) return null
        return InventoryExpiryAlert(
            expiredProductCount = products,
            expiredBatchCount = batches,
            archiveNames = preferences.getStringSet(KEY_NAMES, emptySet()).orEmpty().toList().sorted(),
            scannedAtMillis = preferences.getLong(KEY_SCANNED_AT, 0L),
        )
    }

    private companion object {
        const val KEY_PRODUCTS = "expired_products"
        const val KEY_BATCHES = "expired_batches"
        const val KEY_NAMES = "archive_names"
        const val KEY_SCANNED_AT = "scanned_at"
    }
}
