# Plan de Implementación: Medusa a Nivel Profesional (Google Play Store)

Este documento detalla la hoja de ruta técnica, de producto y legal para transformar **Medusa** en un reproductor multimedia comercial y de referencia en Android TV, con monetización y prestaciones prémium.

---

## 🧭 Resumen de Fases y Estado

```
┌─────────────────────────────────────────────────────────────┐
│ FASE 1: Limpieza Técnica y Actualizador Firebase OTA       │ ✅ [Completada]
├─────────────────────────────────────────────────────────────┤
│ FASE 2: Experiencia TV-First y Blindaje AndroidManifest    │ ✅ [Completada]
├─────────────────────────────────────────────────────────────┤
│ FASE 3: Sistema de Monetización (Medusa Pro)               │ ✅ [En curso: Conexión UI]
├─────────────────────────────────────────────────────────────┤
│ FASE 4: Pulido de Lanzamiento y Pruebas Cerradas (Play)     │ 🚀 [Prioridad Actual]
├─────────────────────────────────────────────────────────────┤
│ FASE 5 (Post-Lanzamiento v2.0): Función Social Comunitaria │ ⏸️ [Pospuesta a v2.0]
└─────────────────────────────────────────────────────────────┘
```

---

## 📌 Fase 1: Limpieza Técnica y Actualizador Firebase OTA (Completada)

### 1.1. Sustitución del actualizador de GitHub por Firebase Hosting OTA
* **Problema resuelto:** Repositorio privado de GitHub inaccesible sin token para usuarios finales.
* **Solución aplicada:**
  * Endpoint activo en `https://medusa-fd388.web.app/api/version.json`.
  * Consulta periódica y manual contra Firebase Hosting.
  * Comparación de versión por `versionCode` (entero nativo) mediante `PackageInfoCompat.getLongVersionCode()`.
  * Sistema de **Kill-Switch / Actualización Obligatoria** (`minSupportedVersionCode` y `forceUpdate: true` bloquean la app con diálogo no descartable hasta actualizar).
  * Soporte dual: descarga directa de APK vía `FileProvider` e integración con Firebase App Distribution.

### 1.2. Selector de Canal de Actualización en Ajustes
* En `SettingsScreen`:
  * Variante Beta: Muestra `Canal: Beta Privada (Firebase OTA)` con comprobador y descargador nativo.
  * Variante Play: Muestra `Canal: Google Play Store` con cumplimiento estricto de directivas de Google Play.

---

## 📺 Fase 2: Experiencia TV-First y Blindaje de Manifiesto (Completada)

### 2.1. Bloqueo de Orientación y Configuración Leanback
* En `AndroidManifest.xml` (`MainActivity`):
  * `android:screenOrientation="sensorLandscape"` bloqueando la app en horizontal para pantallas grandes de TV.
  * `android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"` blindando la reproducción contra desconexiones de mando Bluetooth o cambios de fuente HDMI.
  * `android:launchMode="singleTask"` evitando instancias duplicadas al invocar desde canales del launcher de Android TV.

### 2.2. Manejo Universal y Antifallo del Botón Atrás (D-Pad Back)
* **HomeScreen:**
  * Si el usuario ha bajado por las filas de contenido, pulsar **Atrás** hace scroll suave directo al inicio (`listState.animateScrollToItem(0)`) en vez de cerrar la aplicación.
  * Estando arriba del todo, requiere pulsar Atrás dos veces en un intervalo de 2 segundos ("Pulsa Atrás de nuevo para salir").
* **PlayerScreen:**
  * Si aparece la tarjeta de "Siguiente Episodio", pulsar Atrás descarta la cuenta atrás para permitir ver los créditos tranquilamente.
  * Mantiene la jerarquía estricta: Diálogo Reanudar $\rightarrow$ Modales de Audio/Subtítulos $\rightarrow$ Siguiente Episodio $\rightarrow$ Overlay HUD $\rightarrow$ Salir al detalle.
* **SearchScreen:**
  * Pulsar Atrás limpia la consulta activa (`onClear()`) al instante en lugar de borrar letra por letra.
* **MainActivity (Diálogo de Actualización):**
  * Descarta el diálogo opcional limpiamente con Atrás. Si es obligatoria, cierra la app de forma segura.

---

## 💎 Fase 3: Sistema de Monetización (Medusa Pro)

### 3.1. Arquitectura Técnica de Google Play Billing
* Integración de `com.android.billingclient:billing-ktx:7.1.1`.
* Producto único in-app: `medusa_pro_lifetime` (Pago único vitalicio de 4,99 €).
* `BillingManager` singleton con reconexión automática y verificación de licencias.
* Estado reactivo local en `UserPreferencesRepository` (`isProUser: StateFlow<Boolean>`).
* Diálogo adaptado a Android TV `MedusaProDialog` con foco D-pad y soporte de restauración de compras.

### 3.2. Propuesta de División: ¿Qué es Gratis vs. Qué es de Pago?

> **Objetivo:** Que la versión gratuita sea un reproductor de 10 estrellas (sin anuncios molestos ni recortes en calidad audiovisual), pero que los usuarios avanzados y entusiastas quieran pagar 4,99 € por pura comodidad, personalización y automatización.

| Categoría | Función | Versión Gratuita (Free) | Medusa Pro (4,99 € Vitalicio) |
| :--- | :--- | :---: | :---: |
| **Reproductor y Calidad** | Conexión ilimitada a Jellyfin / Emby | ✅ Totalmente gratis | ✅ Incluido |
| | Reproducción 4K, HDR, Dolby Vision, Passthrough de Audio | ✅ Sin límites | ✅ Incluido |
| | Selección de pistas de audio y subtítulos con sincronización | ✅ Sin límites | ✅ Incluido |
| | Memoria de reanudación y marcas de visto | ✅ Totalmente gratis | ✅ Incluido |
| **Automatización** | Salto de Intros y Créditos (Intro Skipper) | ⚪ Botón manual en pantalla | 👑 **Salto 100% automático (Manos libres)** |
| | Tarjeta "Siguiente Episodio" con cuenta atrás | ❌ No disponible | 👑 **Auto-play del siguiente episodio** |
| | Modo Noche / Realce inteligente de diálogos en sonido | ❌ No disponible | 👑 **Compresor dinámico de audio** |
| **Catálogo y Descubrimiento** | Filtrado por géneros estándar (Acción, Comedia, etc.) | ✅ Incluido | ✅ Incluido |
| | **Hubs de Marcas de Streaming** (Netflix, Disney+, Prime, etc.) | ❌ 1 o 2 de prueba | 👑 **Acceso ilimitado a todos los hubs** |
| | Búsqueda universal por actores, directores y títulos | ✅ Incluido | ✅ Incluido |
| **Servicios Externos** | Sincronización bidireccional con **Trakt.tv** | ❌ No disponible | 👑 **Scrobble en tiempo real y Watchlist** |
| **Personalización Visual** | Temas visuales de la interfaz | ⚪ Tema oscuro estándar | 👑 **Temas personalizados, acentos de color y efectos** |

---

## 🚀 Fase 4: Pulido de Lanzamiento y Pruebas Cerradas (Play Store)

1. **Conexión de Candados Pro en la UI:**
   * Mostrar el distintivo "PRO" sutil en los ajustes y hubs.
   * Al intentar activar el salto automático, cambiar de tema o entrar en hubs prémium, abrir `MedusaProDialog`.
2. **Generación de Ficheros de Lanzamiento:**
   * Compilación de `app-play-release.aab` con firma de producción (`release.keystore`).
3. **Ficha de Tienda y Material Gráfico:**
   * Archivo de metadatos completado: [`docs/PLAY_STORE_METADATA.md`](file:///Users/corion/AndroidStudioProjects/Tujelly/docs/PLAY_STORE_METADATA.md).
   * ✅ **7 capturas oficiales para Android TV (1920×1080), en español**, listas en `docs/store_assets/` (`tv_screenshot_01_home_…` → `tv_screenshot_07_settings_…`): portada, biblioteca, **Medusa (filtros)**, detalle, reproductor, búsqueda y ajustes. Tomadas con `adb screencap` desde el emulador *Android TV 1080p* (locale `es-ES`) apuntando a un **Jellyfin local (Docker)** poblado **solo con contenido Creative Commons / dominio público** (cortos de Blender: *Big Buck Bunny*, *Sintel*, *Tears of Steel*, *Elephants Dream*; clásicos de dominio público: *Nosferatu*, *The General*, *His Girl Friday*, *Night of the Living Dead*) para blindar contra rechazos por copyright.
   * ✅ **AAB firmado** para Play: `app/build/outputs/bundle/playRelease/app-play-release.aab` (~14,7 MB) con `./gradlew :app:bundlePlayRelease`.
4. **Google Play Console:**
   * Cuenta de desarrollador ($25 pago único a Google).
   * Creación de la app `Medusa` (tv.medusa.app).
   * Configuración de la pista de **Pruebas Cerradas** con los 20 evaluadores (añadir los correos de familia a la lista de "Licencias de Prueba" para que tengan Pro gratis automáticamente).

---

## ⏸️ Fase 5 (Post-Lanzamiento v2.0): Función Social en Servidor (Pospuesta)
* **Objetivo:** Permitir a los usuarios del mismo servidor Jellyfin compartir recomendaciones y notas rápidas con emojis.
* **Motivo de posposición:** Foco prioritario en publicar en Google Play y validar el modelo de monetización antes de añadir complejidad de base de datos social (Cloud Firestore).
