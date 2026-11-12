# Proguard rules for DietTracker
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
