# Metadatos para Google Play Console - Medusa

Este documento contiene todos los textos listos para copiar y pegar en la ficha de **Google Play Console** cuando vayas a publicar la app.

---

## 1. Datos Principales de la Ficha

* **Nombre de la aplicación (máx. 30 caracteres):**
  ```text
  Medusa: Cliente Jellyfin TV
  ```
  *(Longitud: 27 caracteres)*

* **Descripción corta (máx. 80 caracteres):**
  ```text
  Reproductor rápido y cinematográfico para servidores Jellyfin en Android TV.
  ```
  *(Longitud: 76 caracteres)*

---

## 2. Descripción Completa (máx. 4000 caracteres)

```text
Medusa es un reproductor cliente independiente de alto rendimiento diseñado específicamente para televisores inteligentes con Android TV y Google TV, optimizado para conectarse a tu propio servidor multimedia Jellyfin.

Disfruta de una experiencia visual fluida, cinemática y diseñada desde cero para el mando a distancia (100% control por D-Pad).

CARACTERÍSTICAS DESTACADAS:

🎬 REPRODUCCIÓN CINEMATOGRÁFICA AVANZADA
• Salto automático de intros y créditos (Intro Skipper) integrado con tu servidor.
• Tarjeta "Siguiente Episodio" con cuenta atrás automática estilo las grandes plataformas.
• Soporte nativo para formatos de vídeo y audio de alta fidelidad (Media3 + decodificación avanzada).
• Selección rápida de pistas de audio y subtítulos en tiempo real.

⚡ SINCRONIZACIÓN ULTRARRÁPIDA (DELTA SYNC)
• Motor de sincronización diferencial que actualiza catálogos de decenas de miles de títulos en milisegundos sin sobrecargar tu servidor.
• Inicio instantáneo de la app con acceso directo a "Continuar viendo" y últimas novedades.

✨ INTERFAZ 10-FOOT ELEVADA
• Navegación fluida por filas y categorías pensada exclusivamente para el mando a distancia.
• Fondos dinámicos en alta resolución que reaccionan al contenido seleccionado.
• Logotipos vectoriales transparentes (ClearLogo) para una presentación impecable de las portadas.
• Navegación por Hubs y Plataformas para encontrar lo que buscas en segundos.

🔗 INTEGRACIÓN CON TRAKT
• Vincula tu cuenta de Trakt para sincronizar tu historial de visualización, listas de seguimiento y colecciones.

🔒 MÁXIMA PRIVACIDAD Y SEGURIDAD
• Sin intermediarios ni recopilación de datos: la aplicación se comunica directa y exclusivamente entre tu televisor y tu servidor Jellyfin personal.
• Las credenciales se almacenan únicamente de forma local y cifrada en tu dispositivo.

---
NOTA LEGAL / DESCARGO DE RESPONSABILIDAD:
Medusa es una aplicación cliente de terceros desarrollada de forma independiente y no está afiliada, respaldada ni patrocinada por el proyecto Jellyfin ni por ninguna plataforma de streaming comercial. Medusa no incluye, aloja, distribuye ni facilita ningún tipo de contenido multimedia. El usuario es el único responsable de configurar y conectar su propio servidor personal con contenido propio o del que tenga los derechos correspondientes.
```

---

## 3. Categorización y Datos de la Ficha

* **Categoría de la aplicación:** `Reproductores y editores de vídeo` (o `Entretenimiento`).
* **Etiquetas:** `Jellyfin`, `Reproductor de vídeo`, `Android TV`, `Media Center`.
* **Correo electrónico de contacto:** *(Tu correo donde recibir dudas de usuarios)*.
* **URL de Política de Privacidad:**  
  `https://medusa-fd388.web.app/privacy.html` (o `https://medusa-fd388.web.app`)

---

## 4. Respuestas para los Cuestionarios de Google Play

### Declaración de Anuncios:
* **¿Tu aplicación contiene anuncios?**  
  👉 **NO**

### Clasificación de contenido (PEGI / IARC):
* Tipo de app: **Utilidades, productividad, comunicación u otras**.
* ¿Comparte la ubicación física del usuario?: **No**.
* ¿Permite a los usuarios interactuar o intercambiar mensajes?: **No**.
* ¿Contiene contenido sexual o violento en el código de la app?: **No** *(es solo un reproductor)*.
* Resultado habitual: **PEGI 3 / Para todos los públicos**.

### Seguridad de los datos (Data Safety):
* **¿Tu aplicación recopila o comparte datos de usuarios?**  
  👉 **No** (si usas solo almacenamiento local para el servidor Jellyfin).  
  *(Si integras Firebase Crashlytics para fallos técnicos, marcas únicamente: "Datos de diagnóstico de fallos anónimos para depuración técnica, no vinculados a la identidad del usuario")*.

---

## 5. Configuración del Producto Integrado (Compras Integradas en Play Console)

En Google Play Console ve a **Monetizar con Play** > **Productos integrados en la aplicación**:

* **ID de producto:**
  ```text
  medusa_pro_lifetime
  ```
  *(Debe coincidir exactamente con el configurado en la app)*

* **Nombre del producto:**
  ```text
  Medusa Pro Vitalicio
  ```

* **Descripción:**
  ```text
  Desbloqueo de por vida: salto automático manos libres de intros/créditos, explorador por plataformas y soporte de nuevas funciones.
  ```

* **Precio recomendado:**
  ```text
  4,99 €
  ```
* **Estado:** Activo.

### Modelo de Prueba Gratuita (7 Días):
* Los usuarios disponen de 7 días completos de acceso Pro al instalar por primera vez.
* Finalizados los 7 días, la app sigue funcionando al 100% como reproductor Jellyfin gratuito (con salto manual de intro); las funciones Pro (salto manos libres y carruseles por plataforma) muestran la opción de adquirir Medusa Pro Vitalicio.

---

## 6. Archivo de Producción (AAB) Generado

* **Ruta del bundle listo para subir a Play Console:**
  ```text
  app/build/outputs/bundle/playRelease/app-play-release.aab
  ```
* **Tamaño:** ~14 MB
* **Firma:** Release keystore configurado y verificado.

