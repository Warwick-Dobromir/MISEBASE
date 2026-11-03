package com.culinary.crash

import com.culinary.core.common.Clock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局未捕获异常写入（E03 §8）。
 *
 * 行为：把线程、异常类型与堆栈写入 `filesDir/crash-logs/crash-<millis>.txt`，
 * 然后**继续调用原有 handler**，不吞掉崩溃、不阻止系统终止进程。
 * 写入自身失败时绝不递归、绝不再抛异常。
 */
class CrashLogWriter(
    private val directory: File,
    private val clock: Clock,
) {
    fun install(previous: Thread.UncaughtExceptionHandler?): Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { thread, error ->
            try {
                write(thread, error)
            } catch (ignored: Throwable) {
                // 崩溃处理是最后一道边界：写日志失败时保持静默，随后仍交给系统处理。
            }
            previous?.uncaughtException(thread, error)
        }

    fun write(
        thread: Thread,
        error: Throwable,
    ): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            error("cannot create crash log directory")
        }
        val file = nextFile()
        val stackTrace =
            StringWriter()
                .also { writer ->
                    PrintWriter(writer).use { printer -> error.printStackTrace(printer) }
                }.toString()
        file.writeText(
            buildString {
                appendLine("time=${clock.now()}")
                appendLine("thread=${thread.name} (id=${thread.id})")
                appendLine("exception=${error::class.java.name}")
                appendLine("message=${error.message}")
                appendLine(stackTrace)
            },
            Charsets.UTF_8,
        )
        return file
    }

    private fun nextFile(): File {
        val base = "crash-${clock.now().toEpochMilli()}"
        var candidate = File(directory, "$base.txt")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base-$suffix.txt")
            suffix++
        }
        return candidate
    }
}
