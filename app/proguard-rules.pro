-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Coil
-dontwarn coil.**

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.grow.gallery.**$$serializer { *; }
-keepclassmembers class com.grow.gallery.** {
    *** Companion;
}
-keepclasseswithmembers class com.grow.gallery.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# MediaStore
-keep class android.provider.MediaStore** { *; }

# Media3
-keep class androidx.media3.** { *; }
