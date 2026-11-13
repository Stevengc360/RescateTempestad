# Rescate Tempestad

**Rescate Tempestad** es un juego 2D para Android en el que pilotas un helicóptero durante operaciones de salvamento bajo condiciones meteorológicas adversas. Debes localizar supervivientes, desplegar una cuerda, recogerlos y regresar a la base antes de agotar el tiempo, el combustible o la resistencia del helicóptero.

> **Estado:** proyecto en desarrollo. La presencia de código y pruebas no implica que el APK esté validado o publicado en Google Play.

## Jugabilidad

- Seis misiones progresivas, desde «Primer rescate» hasta «Última llamada».
- Vuelo con inercia, consumo de combustible, daños y límite de tiempo.
- Cuerda flexible de 14 segmentos, recogida de personas y transporte con capacidad de hasta tres ocupantes.
- Viento, lluvia y tornado que dificultan la navegación.
- Sistema de puntuación por estrellas y persistencia de las partidas mediante `GameStore`.

## Tecnologías y estructura

| Elemento | Detalle |
| --- | --- |
| Plataforma | Android (mínimo API 26, objetivo API 36) |
| Lenguaje principal del juego | Java |
| Herramientas | Gradle Kotlin DSL; módulo Android `app/` |
| Motor | `app/src/main/java/com/stevengc360/rescatetempestad/GameEngine.java` |
| Vista | `GameView.java` y `MainActivity.java` |
| Guardado | `GameStore.java` |
| Pruebas | `app/src/test/java/com/stevengc360/rescatetempestad/` |

La versión indicada en `app/build.gradle.kts` es **0.2.1**.

## Abrir y probar

1. Clona el repositorio y ábrelo desde **Android Studio**.
2. Configura el SDK Android requerido y sincroniza Gradle.
3. Selecciona un emulador o dispositivo compatible y ejecuta el módulo `app`.
4. Para ejecutar las pruebas JVM, utiliza la tarea Gradle `:app:testDebugUnitTest`. Para generar un APK de depuración, utiliza `:app:assembleDebug`.

En cada `push` a `main`, GitHub Actions ejecuta la suite completa (`.github/workflows/unit-tests.yml`). Estado actual: **47 pruebas, 0 fallos** — incluyen bucles de estrés aleatorizados (`StressLoopTest`: 5 semillas × 6 misiones con checkpoint/restore cada 977 ticks).

La configuración de firma de publicación necesita un almacén de claves propio y variables de entorno. **No subas contraseñas, claves de firma ni archivos privados al repositorio.**

## Ficha de Google Play

| Elemento | Detalle |
| --- | --- |
| Política de privacidad | https://stevengc360.github.io/privacy-policy.html (sin recogida de datos, sin anuncios, sin permisos) |
| Público objetivo | 13+ (no dirigido a niños) |
| Gráfico destacado (1024×500) | `store-assets/feature-graphic-1024x500.png` |
| Icono (512×512) | `store-assets/icon-512.png` |
| Sugerencias visuales de mejora | `store-assets/visual-suggestions.png` |

## Comprobaciones pendientes

Comprobar controles táctiles y rendimiento en dispositivos físicos reales, y crear el AAB firmado de release (`:app:bundleRelease` con keystore propio) antes de subirlo a Google Play. La documentación describe lo observado en el código; no certifica esas comprobaciones.

## Repositorio relacionado

También existe [Rescate-Tempestad-Copiar-Desc](https://github.com/Stevengc360/Rescate-Tempestad-Copiar-Desc), con su propia documentación e historial. No se presupone que ambas versiones sean idénticas.
