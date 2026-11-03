package com.culinary.probe.domain

/** 探针用领域端口：故意不带任何 Hilt/Android 注解。 */
interface Greeter {
    fun greet(): String
}
