# R8 rules for the release build. Libraries that ship their own rules (Room, kotlinx.serialization,
# Compose, sqlite-bundled, coroutines) need nothing here.

# LiteRT-LM ships no consumer rules, and liblitertlm_jni.so looks up its Kotlin classes, fields
# and callback methods by name (config objects in, messages and benchmark info out). Renaming or
# removing any of them fails at runtime, not at build time, so the whole package stays as it is.
-keep class com.google.ai.edge.litertlm.** { *; }

# Readable crash stack traces: keep line numbers, hide the source file names. Play maps the
# rest back with the mapping file that the bundle carries.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
