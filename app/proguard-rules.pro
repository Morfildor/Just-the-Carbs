# R8 rules for the release build (§52).
#
# Kept deliberately small. Most libraries here ship their own consumer rules inside their
# artifacts (Retrofit, OkHttp, Room, Compose, ML Kit), and duplicating those by hand is how a
# project ends up keeping half its code for no reason. Only the gaps are covered below.
#
# The rules that matter are for reflection: R8 cannot see a class that is only ever constructed
# by a serializer or a proxy, so it strips it and the app fails at runtime, in release only,
# usually on a user's phone.

# ---- kotlinx.serialization -------------------------------------------------------------------
# The generated $$serializer classes are referenced reflectively via the Companion, so R8 has no
# static path to them. Without this the Open Food Facts response silently fails to parse in
# release while working perfectly in debug.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class app.justthecarbs.** {
    *** Companion;
}
-keepclasseswithmembers class app.justthecarbs.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.justthecarbs.data.remote.**$$serializer { *; }
-keep,includedescriptorclasses class app.justthecarbs.data.remote.** {
    *;
}

# ---- Retrofit --------------------------------------------------------------------------------
# The API is a dynamic proxy over an interface; the generic return types must survive or the
# converter cannot work out what to deserialize into.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface app.justthecarbs.data.remote.OpenFoodFactsApi
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# R8 full mode strips generic signatures from type parameters unless told otherwise.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---- ML Kit ----------------------------------------------------------------------------------
# ML Kit registers its components through Firebase's ComponentDiscovery, which instantiates each
# registrar reflectively via its no-argument constructor. R8 sees no caller for those constructors
# and removes them, producing:
#
#     NoSuchMethodException: com.google.mlkit.vision.barcode.internal.BarcodeRegistrar.<init> []
#
# The app still launches and the whole non-camera workflow still works, so this fails silently in
# release only — the scanner and OCR simply stop working on a user's phone. Caught by installing
# the minified build and reading logcat, not by any test.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.mlkit.**.internal.*Registrar {
    <init>();
}
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar {
    <init>();
}
# Registrar classes are named in AndroidManifest metadata, so their names must survive too.
-keepnames class com.google.mlkit.** { *; }

# ---- Room ------------------------------------------------------------------------------------
# Room's generated implementation is instantiated by name.
-keep class app.justthecarbs.data.local.JustTheCarbsDatabase_Impl { *; }

# ---- Domain ----------------------------------------------------------------------------------
# Enum values are looked up by name when reading them back out of the database
# (NutritionBasis.valueOf, ProductDataOrigin.valueOf, VerificationStatus.valueOf). Obfuscating
# the constant names would break every stored row.
-keepclassmembers enum app.justthecarbs.domain.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Release logging -------------------------------------------------------------------------
# Strip debug logging from the release binary. §35: do not log remote responses in release.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers so a Play Console crash report is readable, but hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
