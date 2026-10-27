# Keep the app's small API stable for same-signature, non-calling device validation.
# Dependencies, including unused material icons, are still shrunk and optimized.
-keep,allowoptimization class com.alberto.velo.** { *; }
# AndroidJUnitRunner shares this tiny tracing API with the optimized target APK.
-keep class androidx.tracing.Trace { *; }
# The separate instrumentation APK calls Kotlin's public facade classes directly.
-keep class kotlin.** { *; }
