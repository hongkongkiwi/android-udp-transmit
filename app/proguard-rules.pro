# Add project specific ProGuard rules here.

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Keep all model classes
-keep class com.udptrigger.domain.** { *; }
-keep class com.udptrigger.ui.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin
-keep class kotlin.reflect.** { *; }
-keep interface kotlin.reflect.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum com.udptrigger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
