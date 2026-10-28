# 🎚️ Ecualizador

**Ecualizador** es un reproductor de música y ecualizador de audio para Android, hecho con Kotlin y Jetpack Compose. Además de reproducir tu música local con efectos en tiempo real, incluye un **modo global** que aplica el ecualizador al audio de *otras* apps (Spotify, YouTube, VLC, etc.) sin necesidad de root, al estilo de Wavelet o el EQ de Poweramp.

## ✨ Características

- 🎵 **Biblioteca**: lee la música del dispositivo y muestra la portada de cada canción (Coil).
- ▶️ **Reproductor**: reproducción en segundo plano con servicio en primer plano y notificación multimedia.
- 🎛️ **Ecualizador**: EC de múltiples bandas (el número depende de tu dispositivo) con curva visual interactiva y ajuste por deslizadores.
- 📚 **Presets integrados**: Plano, Pop, Rock, Jazz, Clásico, Voz, Graves+ y Agudos+. También soporta configuración personalizada, que se guarda entre sesiones.
- 💥 **Bass Boost**: realce de graves ajustable.
- 📢 **Loudness Enhancer**: refuerzo de volumen de hasta +10 dB.
- 🌐 **Modo global**: escucha las sesiones de audio de otras apps (vía `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`) y les adjunta la cadena de efectos `Equalizer + BassBoost + LoudnessEnhancer`, gestionadas por un servicio en primer plano dedicado.

## 📱 Descarga

> [!NOTE]
> La app requiere **Android 8.0 (API 26) o superior**.

Descargá la última versión firmada desde la sección [**Releases**](https://github.com/Mishi04/Ecualizador/releases) de este repositorio y armoréla directamente en tu teléfono.

## 🏗️ Cómo compilar

Requerimientos:

- JDK 17
- Android SDK (compileSdk 35)

```bash
# Compilar APK de debug
./gradlew assembleDebug

# Compilar APK de release (requiere firma, ver abajo)
./gradlew assembleRelease
```

El build de release está configurado para firmar con un keystore definido en un archivo `keystore.properties` en la raíz del proyecto (ignorado por git, **no lo subas**):

```properties
storeFile=ruta/a/tu/keystore.jks
storePassword=tu-contraseña
keyAlias=tu-alias
keyPassword=tu-contraseña
```

Si el archivo no existe, el APK de release se firma con la clave de debug.

## 🗂️ Estructura del proyecto

```
app/src/main/java/com/ecualizador/app/
├── data/          # Modelo de datos, biblioteca de música y ajustes guardados
├── audio/         # Cadena de efectos, presets, modos de reproducción y globales
├── service/       # Servicio de reproducción y servicio de modo global
├── ui/
│   ├── screens/   # Pantallas: Biblioteca, Reproductor y Ecualizador
│   ├── components/# Componentes reutilizables (sliders verticales, curva EQ…)
│   └── theme/
└── MainActivity.kt
```

## 🛠️ Tecnologías

- [Kotlin](https://kotlinlang.org/) + [Jetpack Compose](https://developer.android.com/compose) con Material 3
- Coroutines / StateFlow
- `androidx.media` para el reproductor
- `android.media.audiofx` (Equalizer, BassBoost, LoudnessEnhancer)
- [Coil](https://coil-kt.github.io/coil/) para las portadas de álbum

## 📄 Licencia

Todavía sin definir. Si querés usar o contribuir al código, escribinos. 🙂

---

Hecho con 🎶 por [Mishi04](https://github.com/Mishi04).
