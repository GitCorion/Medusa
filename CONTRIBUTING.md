# Contribuir a Medusa TV

Gracias por querer mejorar el cliente. Este documento explica cómo preparar el entorno, qué
reglas no se deben romper y qué esperamos de un pull request. Si algo no está claro, abre un issue
antes de invertir horas en un cambio grande.

## Antes de empezar

- Busca en los issues abiertos y cerrados; puede que el tema ya esté discutido.
- Para cambios pequeños (textos, un bug claro, una prueba) abre directamente un PR.
- Para cambios grandes (nueva pantalla, refactor de reproducción, cambio de identidad visual) abre
  primero un issue con el problema y la propuesta.
- No incluyas tokens, contraseñas, IPs privadas, capturas de tu servidor ni claves de TMDB/Trakt en
  issues, PRs o commits.

## Entorno

Requisitos:

- JDK 17.
- Android SDK 36 (`compileSdk` y `targetSdk` 36).
- Android Studio reciente o Gradle desde la terminal.
- Un dispositivo o emulador Android TV para probar el foco con mando.

```bash
git clone https://github.com/GitCorion/Medusa.git
cd Medusa
./gradlew :app:assembleDebug
```

Si vas a probar reproducción, necesitas un servidor Jellyfin accesible. No hace falta configurar
TMDB ni Trakt para trabajar en la interfaz.

### Claves y secretos

- `keystore.properties`, `*.keystore` y `*.jks` están ignorados. Nunca los subas.
- La clave de TMDB se introduce en Ajustes; no se embebe en el APK.
- `TRAKT_CLIENT_ID` es opcional y se inyecta en build-time. El `TRAKT_CLIENT_SECRET` se configura en Ajustes y no se embebe.
- Si encuentras un secreto versionado, no lo copies en el issue: repórtalo siguiendo
  [`SECURITY.md`](SECURITY.md).

## Reglas de arquitectura

1. **La fachada manda.** Los ViewModels hablan con `MediaRepository`, no con Retrofit ni DAOs.
   Si añades una fuente de datos, crea un colaborador y delégalo desde la fachada.
2. **Una sola lógica de reproducción.** No dupliques la resolución de URLs en el ViewModel, en la
   pantalla o en los tests. Usa `JellyfinPlaybackSourceResolver` y las pruebas existentes.
3. **Room sin atajos.** Cualquier cambio de esquema necesita migración, exportación de esquema y
   prueba en `AppDatabaseMigrationTest`. No uses `fallbackToDestructiveMigration` como solución.
4. **Credenciales cifradas.** Usa `CryptoUtils`; no escribas tokens en claro en DataStore.
5. **Trabajo de fondo acotado.** Los workers deben respetar la concurrencia limitada y pausar
   cuando la app está visible o reproduciendo.
6. **Red centralizada.** Las URLs base viven en `ApiConfig`; no repartas literales por el código.
7. **Serialización de Jellyfin.** Usa `NetworkClientFactory.jellyfinJson` y
   `createJellyfinService`. `encodeDefaults = true` es obligatorio para el DeviceProfile.

## Reglas visuales

La identidad está documentada en [`DESIGN.md`](DESIGN.md). En resumen:

- Cero `Modifier.blur()` y cero `RenderEffect`.
- Cero escalado por tarjeta dentro de carruseles y cero atenuación de vecinos.
- Un solo foco activo: borde de 1,5 dp en tarjetas, dock de iconos de 18 dp y cero escala por tarjeta.
- El título y los metadatos de una tarjeta viven fuera del póster.
- Nada de botones "Volver" en pantalla cuando corre en TV.
- La navegación usa un dock de iconos; los chips mantienen su radio de 8 dp. No inventes
  subrayados ondulados, sonar, constelaciones ni metáforas de interacción propias.
- Nada de serif editorial, mayúsculas decorativas, separadores con punto medio ni flechas pegadas
  al texto.
- Respeta `rememberReducedMotion()`.

Si cambias un token, cámbialo en `ui/theme/Color.kt` y actualiza `DESIGN.md` y
`docs/ESTUDIO.md`. Si cambias un componente visual, añade una captura al PR.

## Pruebas

Antes de pedir revisión:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Si tocas Room:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Si tocas reproducción, añade o actualiza pruebas en `PlayerResolutionTest` y ejecuta también las
pruebas golden de serialización. Los tests deben llamar al código real, no a una copia de la
lógica.

Si tocas la interfaz, prueba en un televisor o emulador de 1080p con mando:

- Recorrido completo con D-pad y BACK.
- Foco visible en cada pantalla.
- Sin recortes horizontales en carruseles.
- Movimiento reducido activado.
- Tablet si el cambio afecta a la capa táctil.

## Traducciones

La app mantiene dos recursos:

- `app/src/main/res/values/strings.xml` (español).
- `app/src/main/res/values-en/strings.xml` (inglés).

Añade la cadena en ambos o el build fallará o mostrará texto sin traducir. Mantén el mismo
`name` y el mismo número de argumentos.

## Commits

Usamos un estilo convencional en español:

```text
feat(player): saltar el recap con Media Segments
fix(ui): mantener el foco al volver de la ficha
docs(readme): documentar la instalación por ADB
refactor(data): separar la caché de portada del repositorio local
```

Un commit debe explicar **qué** cambia y, si no es obvio, **por qué**. Evita mezclar refactors
grandes con cambios de comportamiento en el mismo commit.

## Pull request

La plantilla del PR te pedirá:

- Resumen y motivación.
- Tipo de cambio.
- Plan de pruebas (comandos y dispositivo).
- Checklist de reglas visuales y de reproducción.
- Capturas si hay cambios de interfaz.
- Enlaces a issues relacionados.

Un PR pequeño, con una prueba que falla antes y pasa después, se revisa mucho más rápido que uno
grande sin contexto.

## Diseño y documentación

El estudio visual vive en:

- `docs/index.html` y `docs/assets/estudio/` (sitio publicado en GitHub Pages).
- `docs/ESTUDIO.md` (versión larga en Markdown).
- `DESIGN.md` (tokens y reglas).
- `PRODUCT.md` (contexto de producto).

Si propones un cambio de identidad, incluye el antes y el después, el coste de rendimiento y cómo
se comporta a tres metros. "Se ve más moderno" no es un argumento suficiente.

## Código de conducta

Sé respetuoso. No se toleran ataques personales, discriminación ni acoso. Las discusiones técnicas
se centran en el código y en el usuario, no en la persona.
