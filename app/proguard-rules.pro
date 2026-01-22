# Add project specific ProGuard rules here.

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions

# Keep all model classes
-keep class com.udptrigger.domain.** { *; }
-keep class com.udptrigger.ui.** { *; }
-keep class com.udptrigger.data.** { *; }
-keep class com.udptrigger.util.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlin
-keep class kotlin.reflect.** { *; }
-keep interface kotlin.reflect.** { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.**

# DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# JSON serialization (org.json)
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum com.udptrigger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Crash Analytics
-keep class com.udptrigger.util.CrashAnalyticsManager$** { *; }
-keep class com.udptrigger.util.CrashAnalyticsManager$CrashReport { *; }
-keep class com.udptrigger.util.CrashAnalyticsManager$DeviceInfo { *; }
-keep class com.udptrigger.util.CrashAnalyticsManager$AppInfo { *; }

# Quick Hosts Manager
-keep class com.udptrigger.data.QuickHost { *; }
-keep class com.udptrigger.data.QuickHostsManager { *; }

# Presets Manager
-keep class com.udptrigger.data.Preset { *; }
-keep class com.udptrigger.data.PresetsManager { *; }

# Macros Manager
-keep class com.udptrigger.data.Macro { *; }
-keep class com.udptrigger.data.MacroStep { *; }
-keep class com.udptrigger.data.MacroManager { *; }

# Search Manager
-keep class com.udptrigger.data.SearchManager { *; }
-keep class com.udptrigger.data.SearchResult { *; }

# Deep Link Manager
-keep class com.udptrigger.util.DeepLinkManager { *; }
-keep class com.udptrigger.util.DeepLinkManager$DeepLinkResult { *; }
-keepclassmembers class com.udptrigger.util.DeepLinkManager$Action { *; }

# Data Deletion Manager
-keep class com.udptrigger.data.DataDeletionManager { *; }
-keep class com.udptrigger.data.DataCategory { *; }

# Backup Encryption
-keep class com.udptrigger.data.BackupEncryptionManager { *; }

# Network Monitor
-keep class com.udptrigger.domain.NetworkMonitor { *; }

# Sound Manager
-keep class com.udptrigger.domain.SoundManager { *; }

# QR Code Scanner (ML Kit)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Glance Widget
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
