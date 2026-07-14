# Keep DTOs/domain models for kotlinx.serialization reflection-based lookup.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclasseswithmembers class com.mobilecontrol.app.data.remote.dto.** {
    *;
}
-keepclasseswithmembers class com.mobilecontrol.app.domain.model.** {
    *;
}

-keep,includedescriptorclasses class com.mobilecontrol.app.**$$serializer { *; }
-keepclassmembers class com.mobilecontrol.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.mobilecontrol.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
