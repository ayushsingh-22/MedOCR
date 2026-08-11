# Keep kotlinx.serialization models — field names are used as JSON keys.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.medocr.app.data.**$$serializer { *; }
-keepclassmembers class com.medocr.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.medocr.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
