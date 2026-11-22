---
target: toda la aplicacion (ui/)
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/corion/AndroidStudioProjects/Tujelly/app/src/main/java/tv/medusa/app/ui"
timestamp: 2026-11-22T14-15-48Z
slug: app-src-main-java-tv-medusa-app-ui
---
# Crítica de diseño — Medusa TV (Tujelly)

Method: design review only (detector not applicable, native platform). Target: app/src/main/java/tv/medusa/app/ui

## Design Health Score

| # | Heurística | Nota | Problema clave |
|---|---|---|---|
| 1 | Visibilidad del estado del sistema | 3 | Sync, loading y progreso presentes; errores via Toast y foco no anunciado |
| 2 | Correspondencia sistema/mundo real | 3 | Convenciones de streaming solidas; jerga ("scrobble", "PRO") en Ajustes |
| 3 | Control y libertad del usuario | 3 | BACK multinivel, undo de foco en Descubrir; el kill-switch atrapa |
| 4 | Consistencia y estandares | 2 | Deriva doc/codigo, tres negros distintos, specs de foco cambiadas sin actualizar |
| 5 | Prevencion de errores | 2 | Pocas salvaguardas visibles; banner forzado no sortable |
| 6 | Reconocimiento antes que recuerdo | 2 | Nav superior por defecto solo iconos de 15dp; menu contextual oculto |
| 7 | Flexibilidad y eficiencia | 3 | 3 estilos de boton, 2 temas, teclado predictivo, long-press |
| 8 | Estetica y minimalismo | 4 | Lenguaje cinematografico propio, arte a sangre, cero cajones grises |
| 9 | Ayuda para recuperar errores | 2 | Errores con reintento, pero mensajes crudos y sin guia |
| 10 | Ayuda y documentacion | 1 | Sin ayuda contextual; solo pistas sueltas |
| | Total | 25/40 | Acceptable |

## Design Specificity Verdict

Autoria genuina, no interchangeables: negros abisales, cristal obsidiana, tipografia marfil, insignias ambar y gramatica de foco de 3 metros. El problema es que la personalidad documentada y la implementada han divergido: el foco ya no escala ni atenua y los tokens de DESIGN.md no son los que renderiza Color.kt. Scan determinista: no aplicable (nativo).

## Overall Impression

Producto cuidado con coherencia visual poco comun en clientes Jellyfin. Lo que lo frena es disciplina de contrato: las promesas del design system se quedaron en el documento, la accesibilidad es superficial en puntos clave (primer uso y carrusel principal) y la capa tactil se anadio sin verificar que los componentes TV ya fueran clickables.

## What's Working

1. Arte a sangre sin cajones grises: poster 2:3 y panoramicas 16:9 con titulo sobre el vacio y marquesina solo en foco.
2. Ingenieria de mando: sonidos de navegacion, focusRequesters explicitos, onKeyEvent direccional y BACK gestionado por Navigation Compose.
3. Disciplina de rendimiento: cero blur, cache de imagen, redimensionado en servidor y escalation de particulas en dispositivos pobres.

## Priority Issues

[P1] Lenguaje de foco roto: DESIGN.md promete scale 1.05, dim 0.65 y borde cian 2dp; el codigo tiene focusedScale 1.0f, sin dimming y Border.None + borde 1.5dp en el poster. KDoc de MediaCard.kt:79 obsoleto. Sugerido: document.

[P1] Onboarding y carrusel hero opacos: tiles sin Role.Checkbox/selected; flechas del hero con canFocus=false y auto-avance de 12s sin pausa (WCAG 2.2.2). Sugerido: harden.

[P1] "Ambar Cine" inalcanzable: SettingsScreen.kt:1254-1266 solo ofrece 2 temas; updateAccentColor nunca se llama. Sugerido: harden.

[P2] Carga cognitiva alta en Descubrir: filtro + chip + rail de tags + resultados + BACK dependiente de 3 zonas. 5 de 8 items del checklist fallan. Sugerido: distill.

[P2] Paleta real no documentada: Color.kt usa #F7F5F0/#A39E93, DESIGN.md usa #F8FAFC/#94A3B8, las pantallas hardcodean un tercer conjunto. Sugerido: colorize + document.

## Persona Red Flags

Jordan: nav solo iconos de 15dp, onboarding sin guia, sin ayuda, "Error: %1$s". Sam: sin semantica de seleccion, carrusel inalcanzable, contraste 3.53:1, Reduce Motion ignorado, targets 34dp. Riley: BACK con doble pulsacion sin feedback visible, banner forzado que atrapa, dialogos de ancho fijo, configChanges sin onConfigurationChanged. Espectador 10-foot: foco sin escala, dock alpha 0.55, nav a 12sp.

## Minor Observations

TvBackButton conserva focusedScale 1.08f; Shadow con blurRadius 14-18f en HeroBanner; tres negros distintos (#07070B, #070709, #06080D); mix de material3 y tv.material3.

## Questions to Consider

¿Recuperar un affordance de foco barato o documentar la decision de v2.3.12? ¿Ambar Cine es feature real o idea descartada? ¿Que paleta es canonica? ¿Soporte tablet real o declarar TV-only?
