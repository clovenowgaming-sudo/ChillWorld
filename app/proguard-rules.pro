# Aturan ProGuard Chill World
# Debug build tidak pakai minify, ini cuma buat release.
-keep class com.vinz.chillworld.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn java.lang.invoke.StringConcatFactory
