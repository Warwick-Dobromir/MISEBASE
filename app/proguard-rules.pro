# 崩溃堆栈保留行号，便于诊断；其余交给 R8 默认规则与依赖的 consumer rules。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
