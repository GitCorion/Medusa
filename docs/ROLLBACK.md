# Reversión del cambio de credenciales gestionadas por el servidor

Este cambio es aditivo y *fail-open*. Los flags nuevos (`tmdbUseServer`,
`traktUseServer`, `traktServerRevision`) se guardan en DataStore con valores por
defecto `true`/`0`; el código antiguo los ignora, así que no hay migración de
Room ni de DataStore.

## 1. Mitigación rápida (sin recompilar)

- **TMDB**: en Ajustes, borra/modifica la caja *TMDB API key*. Al escribir tu
  propia clave se activa el modo local y se deja de usar el proxy del servidor.
- **Trakt**: pega tu token en *Token manual* (o haz QR de nuevo). Eso activa el
  modo Trakt local; el arranque no sobrescribirá tus credenciales con las del
  servidor y desvincular no borrará el vínculo ajeno.

Si el plugin no está instalado o no responde, la app ya funciona en modo local
automáticamente.

## 2. Reversión completa de la app

```bash
cd /opt/Medusa
git revert <sha-del-commit>          # o: git checkout <tag-anterior>
./gradlew :app:assembleDebug         # APK en app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Los flags nuevos quedan huérfanos en DataStore pero no afectan a la versión
antigua. Si se quiere limpieza total, desinstala la app (borra DataStore) y
vuelve a instalar; eso también borrará la configuración de Jellyfin, así que
solo hazlo si vas a reconfigurar.

## 3. Compilar para probar el cambio

Necesitas JDK 17 y Android SDK 36. Si el SDK no está en la variable de entorno:

```bash
export ANDROID_HOME=/home/dietpi/android-sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para probar el sync necesitas además el plugin `1.2.0.0` instalado en Jellyfin
y la clave TMDB rellena en su configuración. Comprobación mínima:

1. TV A: Ajustes → Trakt → vincular por QR. Debe aparecer `@ usuario` y
   «Sincronizado desde el servidor».
2. TV B (mismo usuario Jellyfin): abrir Ajustes. Debe aparecer el mismo
   `@ usuario` sin hacer QR.
3. Bajo la caja de TMDB debe leerse «Configurada por el servidor».
4. Escribir una clave propia en TV B → debe pasar a modo local y dejar de usar
   el proxy.
