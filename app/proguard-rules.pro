# ---- SecureVault R8 / ProGuard rules ----

# App data layer (Room entities/DAOs/converters) — kept so Room's generated code resolves.
-keep class id.ichio.securevault.data.** { *; }

# SQLCipher (net.zetetic): classes & native methods are loaded via JNI/System.loadLibrary,
# so R8 must not rename or strip them.
-keep class net.zetetic.** { *; }
-keepclasseswithmembernames class net.zetetic.** {
    native <methods>;
}
-dontwarn net.zetetic.**

# AndroidX SQLite support interfaces used by the SQLCipher factory.
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# Keep annotations and generic signatures (Room, Compose, coroutines rely on these).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
