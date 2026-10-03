# kotlinx.serialization: keep generated serializers of the data classes loaded from bundled JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.pogoascent.** {
    *** Companion;
}
-keepclasseswithmembers class com.pogoascent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pogoascent.**$$serializer { *; }
