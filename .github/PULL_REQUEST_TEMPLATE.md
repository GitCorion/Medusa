## Resumen

<!-- Qué cambia y por qué. Si hay un issue, enlázalo. -->

## Tipo de cambio

- [ ] Corrección de error
- [ ] Nueva función
- [ ] Cambio de interfaz o identidad visual
- [ ] Reproducción o rendimiento
- [ ] Refactor sin cambio de comportamiento
- [ ] Documentación
- [ ] Traducciones

## Plan de pruebas

<!-- Comandos ejecutados y dispositivo utilizado. -->

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

- Dispositivo:
- Versión de Android:
- Versión de Jellyfin (si aplica):

## Checklist

- [ ] He leído `CONTRIBUTING.md`.
- [ ] Las pruebas pasan y he añadido una prueba para el comportamiento nuevo.
- [ ] No he roto las invariantes de `docs/PLAYBACK.md`.
- [ ] No he introducido `Modifier.blur()` ni `RenderEffect`.
- [ ] No he añadido escalado por tarjeta ni atenuación de vecinos.
- [ ] El foco sigue siendo visible con D-pad y BACK.
- [ ] He mantenido `values/strings.xml` y `values-en/strings.xml` sincronizados.
- [ ] He actualizado la documentación o el estudio si hacía falta.
- [ ] No hay secretos, tokens, IPs privadas ni datos personales en el diff.
- [ ] Si hay cambios visuales, adjunto capturas antes/después.

## Capturas

<!-- Arrastra aquí las imágenes, o elimina esta sección si no aplica. -->
