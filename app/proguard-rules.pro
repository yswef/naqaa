# Keep the stack traces readable in field reports without exposing class names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Components referenced only from the manifest are kept automatically by the Android
# Gradle plugin. JSON parsing of the bundled content relies on org.json, which ships
# with the platform and therefore needs no keep rule.
-dontwarn org.jetbrains.annotations.**
