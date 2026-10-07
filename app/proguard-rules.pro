# Firestore maps documents manually (no reflection-based toObject), so no model keep rules are needed.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.pashe.app.**$$serializer { *; }
-keepclassmembers class com.pashe.app.** { *** Companion; }
-keepclasseswithmembers class com.pashe.app.** { kotlinx.serialization.KSerializer serializer(...); }
