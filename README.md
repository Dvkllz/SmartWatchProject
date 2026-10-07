# Watch SOS — MVP Wear OS

Aplicación nativa Kotlin/Compose para activar una emergencia con una pulsación de dos segundos, registrar audio y ubicación, enviarlos a un backend local y finalizar la alerta. Los contactos son simulados: **no se contacta a servicios de emergencia ni se envían SMS**.

## Arquitectura

```
Wear Compose → EmergencyViewModel → EmergencyService (foreground)
                                      ├── AudioRecorderManager
                                      ├── LocationTracker (Flow)
                                      └── EmergencyRepository → Retrofit → Express
                                            └── sesión persistida      ├── JsonEmergencyRepository
                                                                     └── NotificationService
```

Estados: `IDLE → STARTING → ACTIVE → STOPPING → FINISHED`. `ERROR` conserva los datos pendientes cuando falla el cierre. La UI diferencia una emergencia local de una confirmada por el servidor.

## Requisitos

- Android Studio compatible con AGP 8.13.2, Android SDK 36 y Build Tools 35.0.0.
- JDK 17 o 21; no usar Java 25 con este Gradle.
- Gradle 8.14.4 incluido mediante wrapper; Kotlin 2.1.20; Compose 1.8.2; Wear Compose 1.5.0.
- Node.js 22 o superior y npm.
- Emulador Wear OS con Google APIs/Google Play services, API 30 o superior. Se recomienda API 35 para comprobar las restricciones actuales del servicio. `minSdk=30`, `targetSdk=35`, `compileSdk=36`.

## Backend

```powershell
cd C:\AppSmartWatch\backend
npm.cmd ci
npm.cmd test
npm.cmd start
```

El servidor escucha en `127.0.0.1:3000`. `GET http://localhost:3000/health` comprueba su estado. La app del emulador usa `http://10.0.2.2:3000/`, alias del loopback del equipo anfitrión. **localhost dentro del reloj apunta al reloj**, no al PC.

Las variables y límites están documentados en `backend/.env.example`; se puede copiar como `.env`. Para un reloj físico, usar una dirección accesible desde su red y establecer `HOST` deliberadamente. No publicar este servidor de demostración en Internet: no incorpora autenticación de usuarios. Antes de producción hacen falta identidad verificable, autorización por emergencia, HTTPS, política de retención y un proveedor real de notificaciones.

## Abrir y compilar la app

1. Abrir **C:\AppSmartWatch** con Android Studio y sincronizar Gradle.
2. Seleccionar JDK 17/21 en Settings → Build Tools → Gradle.
3. Configurar `local.properties` con `sdk.dir=C\:/ruta/al/Android/Sdk` si Studio no lo genera (el carácter `:` debe escaparse).
4. En Build Variants elegir `demoDebug` para la demostración o `liveDebug` para sensores reales.
5. Ejecutar el módulo `wear-app` en un dispositivo Wear OS.

```powershell
.\scripts\build.ps1
.\gradlew.bat :wear-app:assembleLiveDebug :wear-app:testDemoDebugUnitTest :wear-app:lintDemoDebug
```

El script de Windows utiliza el JDK existente en `C:\AppAndroid\.tools` únicamente si `JAVA_HOME` no está configurado. En otro equipo configurar `JAVA_HOME`. La caché de compilación del script queda en `.gradle-user` dentro de este proyecto.

El proyecto ya incluye APK de prueba en `wear-app/build/outputs/apk/demo/debug/` y `wear-app/build/outputs/apk/live/debug/`. El informe de pruebas y los comandos del emulador local están en [docs/TESTING.md](docs/TESTING.md).

Para otro servidor: `./gradlew :wear-app:assembleLiveDebug -PSOS_BASE_URL=https://sos.example.com/`. La URL debe terminar en `/`. HTTP está permitido solo en builds debug; release rechaza tráfico en claro y necesita una URL HTTPS.

## Crear el emulador

1. Android Studio → Device Manager → Create Virtual Device → **Wear OS**, perfil circular pequeño.
2. Descargar una imagen Wear OS API 30+ con servicios de Google y arrancarla.
3. En Extended Controls → Location se puede enviar una posición GPS. Activar Ubicación en ajustes del reloj.
4. Para audio real, comprobar los controles de micrófono del emulador y el permiso de micrófono del sistema anfitrión. Su funcionamiento depende de la imagen y del host; validar captura real en un reloj físico.

`demoDebug` usa una posición simulada cuando no consigue una posición real y genera segmentos WAV de prueba. La pantalla y los metadatos distinguen simulación de captura real. **El audio demo no graba conversaciones**. `liveDebug` desactiva ambas simulaciones y usa `MediaRecorder` (AAC mono en contenedor MPEG-4, `.m4a`).

## Permisos

Antes de activar el SOS, la app solicita micrófono y ubicación (aproximada aceptada). Android 13+ solicita también notificaciones, cuya denegación no impide iniciar el servicio. Los permisos se vuelven a comprobar al activar. Si se deniegan, la UI permite reintentar o abrir ajustes. La falta temporal de GPS o micrófono se muestra sin presentar datos simulados como reales.

El servicio declara los tipos `microphone` y `location`; se inicia desde la actividad visible después de obtener permisos. No se solicita ubicación en segundo plano ni se reinicia la captura desde el arranque. Se conserva una notificación mientras dura la emergencia. El modo demo omite el tipo location cuando la ubicación del sistema está apagada.

Si la ubicación del sistema estaba apagada al activar el SOS, esa sesión no inicia solicitudes GPS reales: en demo usa la alternativa simulada y en live informa la ausencia de ubicación. Activa ubicación antes del siguiente SOS para usar GPS. Así se respetan los requisitos del servicio de ubicación de Android 14+.

## Probar una emergencia

1. Arrancar el backend y abrir `SOS Demo` en el emulador.
2. Conceder permisos y mantener SOS durante dos segundos.
3. Comprobar `EMERGENCIA ACTIVA`, el indicador de audio demo y la confirmación del servidor.
4. Mantenerla al menos 35 segundos para observar ubicaciones y un segmento de audio mientras sigue activa.
5. Pulsar `FINALIZAR ALERTA`. Esperar la confirmación de cierre.
6. Copiar el ID del log del backend y consultar `GET /api/emergencies/{id}`. Debe contener `FINISHED`, `finishedAt`, `locations` y `audio`.
7. Los audios están en `backend/uploads/{id}/`; los registros en `backend/data/`. Los nombres los genera el servidor.
8. Probar también denegar permisos, apagar ubicación, detener/reiniciar backend durante la alerta, finalizar sin red y reintentar el cierre. Nunca mostrar `FINISHED` hasta que el servidor lo confirme.

Los logs Android usan la etiqueta `WatchSOS`; registrar eventos no requiere imprimir coordenadas ni contenido del audio. El notificador simulado del backend imprime usuario, hora, ubicación e ID exclusivamente en modo demo configurable.

## API REST

| Método | Ruta | Contenido |
|---|---|---|
| POST | `/api/emergencies` | `{userId, latitude, longitude, timestamp, status:"ACTIVE", simulated?}`; cabecera opcional `Idempotency-Key` (UUID) |
| POST | `/api/emergencies/:id/location` | `{latitude, longitude, timestamp, simulated?, sampleId?}` |
| POST | `/api/emergencies/:id/audio` | multipart `audio`; cabecera `X-Upload-Id` (UUID), campo `simulated` (`true`/`false`) |
| POST | `/api/emergencies/:id/finish` | `{timestamp}`; cierre idempotente |
| GET | `/api/emergencies/:id` | Emergencia con ubicaciones y metadatos de audio |

Se permite latitud/longitud `null` **juntas**, únicamente al crear una alerta sin GPS, para no retrasar el SOS. Una actualización requiere ambas coordenadas válidas. Las fechas deben ser ISO UTC. Creación, ubicaciones con `sampleId` y audio con ID de subida son idempotentes para tolerar respuestas perdidas. Una emergencia finalizada no acepta datos nuevos.

## Carpetas

```
wear-app/src/main/java/com/watchsos/
  ui/{screens,components,theme}/
  viewmodel/  model/  service/  audio/  location/
  data/{api,repository}/  util/
backend/
  src/{controllers,routes,services,repositories,models}/
  test/  uploads/  data/
scripts/
docs/
```

## Límites conocidos

- Es un MVP para pruebas; la conectividad, batería, permisos y disponibilidad de sensores afectan la entrega.
- La grabación se divide en segmentos con pequeñas pausas entre ellos. Los segmentos completos se suben durante la alerta; el último se sube al finalizar.
- Si Android mata el proceso o se fuerza el cierre, la captura se detiene. Al reabrir se ofrece finalizar/sincronizar la sesión pendiente, sin reiniciar silenciosamente el micrófono. El segmento que estaba abierto podría no recuperarse.
- No hay transmisión de audio en tiempo real ni entrega garantizada con la aplicación cerrada. El reintento de cierre pendiente es explícito desde la UI.
- El JSON del servidor está pensado para un solo proceso local; su repositorio es sustituible por una base de datos. No ejecutar varios procesos escribiendo al mismo directorio.
- En relojes sin GPS o sin Play services la ubicación real puede no estar disponible. La alerta se puede crear sin coordenadas y la UI avisa.
- La creación espera como máximo 8,5 segundos la primera lectura de ubicación; si no llega, envía coordenadas nulas y las actualiza posteriormente.
- La cola local guarda las últimas 2.000 ubicaciones pendientes y limita el audio local a aproximadamente 100 MB. El servidor limita cada archivo a 8 MB y cada emergencia a 100 MB de audio y 20.000 ubicaciones. Si se cambia el uso de la demo a sesiones extensas, hay que adaptar estas cuotas y la retención; un rechazo por cuota conserva el envío pendiente.
- Mantener pantalla y sensores activos consume batería; el servicio no garantiza ejecución indefinida bajo restricciones del fabricante.

## Referencias verificadas

- [Compose en Wear OS](https://developer.android.com/training/wearables/compose)
- [Ubicación en Wear OS y Fused Location Provider](https://developer.android.com/training/wearables/apps/location-detection)
- [Tipos y permisos de foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [MediaRecorder y limitaciones del emulador](https://developer.android.com/media/platform/mediarecorder)
- [Red del emulador](https://developer.android.com/studio/run/emulator-networking)
- [Compatibilidad AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
