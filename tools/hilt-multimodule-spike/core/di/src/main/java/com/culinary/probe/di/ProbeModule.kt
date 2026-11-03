package com.culinary.probe.di

import com.culinary.probe.domain.Greeter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 绑定放在 library 模块的 di 包，验证跨模块 Hilt 聚合。 */
@Module
@InstallIn(SingletonComponent::class)
object ProbeModule {
    @Provides
    @Singleton
    fun provideGreeter(): Greeter = DefaultGreeter()
}

internal class DefaultGreeter : Greeter {
    override fun greet(): String = "CULINARY Hilt probe OK"
}
