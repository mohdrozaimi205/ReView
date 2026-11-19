# ReView R8 rules. Libraries (Room, Media3, Coil, WorkManager, Compose) ship their own consumer rules.
-keep class com.review.mediamanager.database.** { *; }
-keep class com.review.mediamanager.utilities.CleanupWorker { *; }
-dontwarn org.slf4j.**
