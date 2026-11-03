package com.culinary.core.data.di

import javax.inject.Qualifier

/** 单线程写执行器（E02 §3.1）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WriteExecutor

/** 固定大小读线程池。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReadExecutor
