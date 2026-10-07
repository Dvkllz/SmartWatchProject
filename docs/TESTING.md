# Verificación del MVP

## Comandos reproducibles

```powershell
.\scripts\build.ps1 -Tasks ':wear-app:assembleDemoDebug',':wear-app:assembleLiveDebug',':wear-app:testDemoDebugUnitTest',':wear-app:lintDemoDebug'
cd backend
npm.cmd ci
npm.cmd test
```

Las pruebas de backend usan puertos efímeros y directorios temporales; no alteran las emergencias de la demo. Las pruebas Android de repositorio usan un transporte controlado para simular respuestas perdidas, disco lleno y recuperación de sesión. La captura de micrófono y la experiencia de permisos necesitan pruebas en dispositivo/emulador.

## Matriz manual

| Caso | Resultado esperado |
|---|---|
| Toque corto o soltar antes de 2 segundos | No inicia emergencia |
| Cancelar o denegar micrófono/ubicación | Mensaje y acceso a ajustes; sin cierre inesperado |
| Ubicación aproximada concedida | Permite SOS con la precisión disponible |
| Notificaciones denegadas | El servicio puede iniciar; la UI muestra su estado |
| GPS apagado en demo | Coordenadas simuladas claramente identificadas |
| GPS apagado en live | Alerta sin ubicación; nunca inventa coordenadas |
| Micrófono ocupado/no disponible | Advertencia; continúa intentando entregar la alerta y ubicación |
| Backend desconectado al activar | Alerta local y datos pendientes; sin confirmación falsa |
| Backend desconectado al finalizar | Captura detenida y cierre pendiente recuperable |
| Volver a abrir con cierre pendiente | Ofrece reintentar; conserva el ID y los archivos |
| Reintentar tras recuperar red | Envía audio/ubicaciones pendientes y confirma FINISHED |
| Cerrar o sacar la actividad de primer plano | La emergencia sigue en el servicio mientras Android permita ejecutarlo |
| Forzar cierre del proceso | Captura detenida; sesión recuperable al abrir; segmento incompleto no garantizado |
| Dos activaciones rápidas | Una sola sesión local |
| Pantalla circular pequeña | SOS y finalizar accesibles, contenido secundario desplazable |

## Emulador local preparado en este equipo

Durante el desarrollo se instaló un SDK de emulación en `.tools/android-sdk` y un AVD **WatchSosDemo** con Wear OS 5.1 / Android API 35, pantalla circular de 384 × 384 y densidad 320 dpi. Son herramientas locales excluidas de Git.

```powershell
# Arrancar con ventana (sin otro proceso del mismo AVD abierto)
.\scripts\start-emulator.ps1 -ShowWindow
# Instalar el APK
.\scripts\adb.ps1 -Arguments '-s','emulator-5556','install','-r','wear-app\build\outputs\apk\demo\debug\wear-app-demo-debug.apk'
# Abrir la app
.\scripts\adb.ps1 -Arguments '-s','emulator-5556','shell','am','start','-n','com.watchsos.demo/com.watchsos.MainActivity'
# Consultar eventos
.\scripts\adb.ps1 -Arguments '-s','emulator-5556','logcat','-s','WatchSOS:I'
# Detener solamente este emulador
.\scripts\adb.ps1 -Arguments '-s','emulator-5556','emu','kill'
```

El script desactiva audio del anfitrión para la demo sintética. Para probar la entrada del micrófono del PC, arrancar el AVD cerrado con `scripts/start-emulator.ps1 -ShowWindow -EnableMicrophone`, instalar el APK `liveDebug` y abrir **SOS** (`com.watchsos`). El interruptor configura la entrada de audio del AVD y añade `-allow-host-audio`. Conceder el permiso de micrófono si Windows lo solicita. El backend debe estar iniciado. Activar SOS, hablar durante al menos 35 segundos y finalizar; los archivos `.m4a` recibidos quedan en `backend/uploads/{emergencyId}/`. La recepción de un archivo no basta para comprobar sonido audible: reproducirlo y comprobar la voz. Si está en silencio, revisar Extended Controls → Microphone → Virtual microphone uses host audio input y el dispositivo de entrada predeterminado de Windows. Esto no valida el comportamiento de un reloj físico ni envía avisos reales a contactos. En otro equipo crear el AVD desde Android Studio siguiendo el README; `.tools` no forma parte del código fuente portable.

## Resultados automatizados

- Diez pruebas JUnit: orden de cierre, creación idempotente tras respuesta perdida, subida reintentable, cierre no confirmado, recuperación persistida, exclusión de sesiones simultáneas, persistencia con red lenta, errores de disco y formato/tamaño de WAV.
- Ocho pruebas HTTP de Express: flujo completo, idempotencia, conflictos de ID, inmutabilidad tras finalizar, validación, subidas inválidas, concurrencia y cuotas.
- APK `demoDebug` y `liveDebug` compilados.
- Lint de `demoDebug` sin errores. Permanecen avisos de versiones más recientes de dependencias; se mantienen versiones fijas compatibles entre sí.

## Prueba de extremo a extremo

Fecha: 6 de octubre de 2026. Emulador Wear OS 5.1 conectado al servidor Express real por `10.0.2.2:3000`.

Primera sesión comprobada: `5382fe9d-8878-4e67-b87f-7a03c21ae045`. Terminó en `FINISHED`, con seis ubicaciones y tres archivos WAV demo. Se verificó que no quedaran servicio activo ni sesión pendiente después de cerrar. Las capturas y el registro de la prueba se guardan localmente en `artifacts/`.

Se comprobó además la cancelación del diálogo de micrófono sin caída de la app, la prevención de activación por toque corto y el inicio del servicio con notificaciones denegadas. No se ha validado la captura acústica real de un reloj físico.

Segunda sesión: `098f1560-d656-4f8d-8d7b-de81640ea649`, creada con GPS apagado y ubicación demo explícita. Se desconectó el backend y después se reinició el emulador. La app recuperó 133 ubicaciones y 45 segmentos pendientes; un intento sin backend mostró `ENVÍO PENDIENTE`. Al restaurar el servidor y reintentar, terminó en `FINISHED` con **139 ubicaciones y 46 archivos de audio en total**, incluyendo los recibidos antes de la interrupción. Se conservó el mismo ID de emergencia.

Evidencias locales: `artifacts/emergency-online.json`, `artifacts/emergency-recovered.json` y capturas en `artifacts/screens/`. Los archivos de audio asociados están en `backend/uploads/{emergencyId}/`.

## Revalidación solicitada: 6 de octubre de 2026 (Bogotá)

Se instaló el APK demo actual y se repitió el flujo en el emulador Wear OS 5.1, API 35, conectado al backend Express local. No se modificó el código de producción durante esta prueba.

- **18 pruebas automatizadas aprobadas:** 10 JUnit Android ejecutadas nuevamente tras limpiar sus resultados y 8 pruebas HTTP del backend.
- **Lint:** 0 errores y 15 advertencias sobre versiones de dependencias.
- Un toque corto y una pulsación de un segundo no crearon ninguna emergencia. Una pulsación de 2,5 segundos inició el SOS.
- El servicio continuó en segundo plano, con permisos de micrófono y ubicación concedidos y notificaciones denegadas.
- Al desactivar GPS, la demo continuó con ubicación simulada identificada en pantalla.
- Al detener el backend y finalizar desde la app, la captura y el servicio se detuvieron. Persistieron dos ubicaciones y dos audios pendientes, junto con la fecha de cierre.
- Tras forzar el cierre y reabrir la app, se recuperó la sesión pendiente. Al restaurar el backend y reintentar, la misma emergencia terminó en `FINISHED` con **7 ubicaciones y 3 archivos WAV**, sin IDs de muestras ni de subidas duplicados.
- Se verificaron longitud, cabecera RIFF y SHA-256 de los tres audios contra los metadatos del servidor. No quedó sesión pendiente ni servicio activo. El buffer de crashes no contenía cierres inesperados de Watch SOS.

Emergencia: `25d72a57-054f-4304-8053-e9ec2affe7ec`. Evidencias locales: `artifacts/retest/emergency.json`, `artifacts/retest/active.png`, `artifacts/retest/offline.png`, `artifacts/retest/finished.png` y `artifacts/retest/crash-buffer.txt`.

Alcance: audio sintético de la variante demo; la grabación acústica de la variante live y el comportamiento en un reloj físico siguen sin validarse. La matriz manual anterior incluye casos todavía pendientes y no constituye una lista de pruebas aprobadas en esta ejecución.
