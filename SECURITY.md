# Seguridad

## Versiones soportadas

Recibimos informes sobre la última versión publicada en
[GitHub Releases](https://github.com/GitCorion/Medusa/releases) y sobre la rama `main`. Las
versiones anteriores pueden no recibir correcciones.

| Versión | Soporte |
|---|---|
| 2.3.x | Sí |
| Anteriores | Solo si el fallo sigue presente en la última versión |

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usa
[GitHub Security Advisories](https://github.com/GitCorion/Medusa/security/advisories/new) para
enviar el informe de forma privada.

Incluye, si puedes:

- Versión de la app y `versionCode`.
- Modelo de dispositivo y versión de Android.
- Versión de Jellyfin, si aplica.
- Pasos para reproducir y el impacto esperado.
- Una prueba de concepto mínima, sin datos reales.
- Logs **redactados**. Nunca incluyas tokens, contraseñas, IPs privadas ni capturas de tu
  biblioteca.

Responderemos en la medida de lo posible y coordinaremos la divulgación contigo. No prometemos un
plazo fijo, pero no ignoraremos un informe.

## Alcance

Nos interesan especialmente:

- Cifrado y almacenamiento de credenciales (Android Keystore, AES/GCM).
- Validación TLS, pins de certificados autofirmados y rechazo de CAs de usuario.
- Actualizaciones OTA: origen permitido, SHA-256, tamaño máximo, caducidad del manifiesto y
  kill-switch.
- Construcción de URLs de Jellyfin, subrutas, IPv6 y parámetros sensibles.
- Exposición de datos en logs, backups o ficheros temporales.
- Inyección en los catálogos JSON o en el manifiesto de versión.
- Permisos del manifiesto y componentes exportados.

Fuera de alcance:

- Vulnerabilidades del servidor Jellyfin, de Trakt, de TMDB o de la red del usuario.
- Ataques que requieran un dispositivo rooteado o acceso físico desbloqueado.
- Ingeniería social o denegación de servicio contra infraestructura de terceros.
- Contenido al que el usuario tenga acceso en su propio servidor.

## Garantías de la app

- Sin Firebase, analítica, publicidad, crash reporting ni Advertising ID.
- Credenciales cifradas con AES/GCM y una clave del Android Keystore. Si el Keystore falla, la
  escritura falla; no se guarda un token en claro.
- TLS con las CAs del sistema, excluyendo las instaladas por el usuario. Un certificado
  autofirmado solo se acepta con un pin SHA-256 explícito.
- OTA limitada a GitHub, con SHA-256 obligatorio, 150 MB de tamaño máximo, manifiesto con
  caducidad de siete días y kill-switch global.
- Los logs de red redactan tokens y parámetros sensibles.
- La app no aloja contenido ni tiene servidores propios de usuario.

## Investigación responsable

- Prueba solo contra tu propio servidor y tu propio dispositivo.
- No accedas a datos de terceros, no los modifiques y no interrumpas servicios.
- Da un margen razonable antes de publicar detalles.
- Si el fallo afecta a usuarios reales, prioriza la coordinación con nosotros.

Gracias por ayudar a que el cliente sea más seguro.
