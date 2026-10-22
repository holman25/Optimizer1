# Status Claro

MVP Android nativo para preparar videos antes de subirlos como Estados de WhatsApp.

## Decisiones del MVP

- Kotlin + Jetpack Compose.
- Media3 Transformer: procesamiento local y acelerado por hardware cuando el teléfono lo permite.
- Salida MP4 con video H.264 y audio AAC.
- Perfiles adaptativos Equilibrado (720p) y Alta calidad (1080p), con bitrate controlado.
- Perfil Acción (1080p, bitrate mayor) para material con movimiento rápido.
- Encuadre completo, salida vertical 9:16 con recorte centrado o fondo cinematográfico desenfocado.
- El modo cinematográfico compone dos capas de video y una pista de audio independiente para mayor compatibilidad.
- Interfaz Material 3 clara, accesible y compatible con modo oscuro.
- Sin cuentas, backend, analítica ni subida de archivos.

## Abrir y ejecutar

1. Abre esta carpeta con Android Studio.
2. Confía en la sincronización de Gradle.
3. Conecta un Android con depuración USB o crea un emulador API 23+.
4. Ejecuta `app`.

La app comparte el MP4 optimizado usando el selector del sistema; desde WhatsApp el usuario elige **Mi estado**.

## Siguiente experimento

Comparar en teléfonos reales los resultados de perfiles 720p y 1080p, con videos de movimiento alto, texto fino y poca luz. WhatsApp puede cambiar su procesamiento sin aviso, por eso los ajustes se calibran con pruebas, no con promesas de calidad sin pérdida.
