package com.culinary.core.data.di

import com.culinary.core.data.home.DefaultHomeSummaryReader
import com.culinary.core.data.home.HomeSummaryReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 首页只读汇总装配（MB-HOME-001）：app 只依赖端口，不接触 DAO。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeSummaryReader(impl: DefaultHomeSummaryReader): HomeSummaryReader
}
