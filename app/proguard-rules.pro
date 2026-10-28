# Reglas R8 / ProGuard para la app del ecualizador.

# Coil y OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Media (androidx.media / MediaSessionCompat)
-keep class android.media.** { *; }
-keep class androidx.media.** { *; }

# Reglas generales
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# La app no usa reflexión sobre sus propias clases; se conservan por seguridad
-keep class com.ecualizador.app.data.Song { *; }
