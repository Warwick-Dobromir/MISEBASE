package com.culinary.core.data.kitchen

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 种子 JSON 读取端口；生产读 assets，测试直接读工程文件。 */
fun interface KitchenSeedReader {
    suspend fun read(): String
}

/** 生产实现：核心库资产会合并进 app assets。 */
class AndroidAssetKitchenSeedReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : KitchenSeedReader {
        override suspend fun read(): String =
            withContext(Dispatchers.IO) {
                context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }

        private companion object {
            const val ASSET = "kitchen_seed_v1.json"
        }
    }
