# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities, embedded/relation data classes, and database/DAO interfaces
-keep class com.example.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep interface com.example.data.local.*Dao { *; }
-keep class com.example.data.local.**_Impl { *; }

# Keep CoroutineWorker constructor instantiated by WorkManager
-keep class com.example.worker.ExpiryReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Ignore missing compile-time annotations used by Google Tink / Security Crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**

