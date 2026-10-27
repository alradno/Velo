# Velo Audio Lab

Diagnóstico personal separado de Velo. Paquete `com.alberto.velo.audiolab`, sin Activity ni icono de inicio, sin filtro de llamadas y sin permiso de micrófono. Solo se ejecuta mediante instrumentación ADB. No es una versión nueva de Velo ni implementa todavía el asistente de llamadas.

## Compilar

JDK 17, Android SDK 36. La descarga de dependencias usa el PC.

```powershell
.\gradlew.bat :app:assemblePrepareDebug :app:assembleOfflineDebug --console=plain
```

`prepare` permite descargar modelos por Wi-Fi; `offline` elimina expresamente INTERNET y ACCESS_NETWORK_STATE del manifiesto combinado. Ambas variantes tienen el mismo paquete y firma de depuración. Instalar primero prepare (versionCode 1), preparar modelos y actualizar con offline (versionCode 2), conservando los modelos de traducción. El modelo de voz francés se prepara a través del proveedor local del sistema.

Modos de instrumentación, mediante `adb -s <serial> shell am instrument -w -e mode <modo> com.alberto.velo.audiolab/.OfflineProbeInstrumentation`:

- `support`: consulta proveedor e idiomas, sin iniciar reconocimiento.
- `prepare-speech`: descarga francés mediante el reconocedor local, solo en prepare y con Wi-Fi.
- `translation`: en prepare descarga modelos FR/ES si hace falta; en offline traduce tres textos fijos sin intentar descargarlos.
- `speech-file`: elige una voz francesa instalada que no requiera red, sintetiza un archivo temporal, lo convierte a PCM mono de 16 kHz, lo envía por un pipe al reconocedor local y traduce la transcripción a español. No reproduce audio ni utiliza el micrófono. Si el reconocedor intentara recurrir al micrófono, este paquete carece del permiso necesario. El archivo temporal se elimina al terminar.

No hay llamadas automáticas, arranque al reiniciar ni servicio permanente de este laboratorio. Los modelos y el paquete de pruebas se conservan para continuar las comprobaciones. Las medidas con voz sintética no validan audio de SIM, calidad en entornos ruidosos, batería o conversación continua.

## Prueba telefónica pendiente

En la carpeta hermana `audio-research`, `run-call-probe.ps1 -Serial <serial> -Mode idle` comprueba los rechazos en reposo. `-Mode dry-wait -WaitSeconds 2` prueba una espera sin abrir audio, incluso si aparece una llamada.

Solo con una llamada de prueba acordada, ejecutar `-Mode wait -WaitSeconds 120`. Este modo espera una llamada telefónica ya contestada. No llama, contesta, silencia ni cuelga. Durante un máximo de 16 segundos intenta medir el canal recibido y enviar la frase fija de 3,71 segundos «Hola. Esta es una prueba de audio de Velo». El otro extremo debe confirmar lo que oye. La medida de muestras no sustituye esa confirmación.

El programa no guarda el audio recibido. Mantiene buffers pequeños, consulta el estado de la llamada a intervalos de 250 ms durante el ensayo y libera los objetos al terminar. Un watchdog limita la duración total del proceso. El script retira el JAR y la frase temporal del teléfono; la pérdida de conexión impide garantizar esa retirada inmediata, pero el watchdog permanece en el teléfono. El audio telefónico real y este watchdog durante una llamada siguen sin validar.

El programa se restringe al modelo CPH2913 comprobado y requiere un proceso shell, UID 2000. Usa APIs internas: una actualización del sistema puede cambiar su funcionamiento. La técnica de preparar el contexto del proceso shell se contrastó con el código público de [scrcpy](https://github.com/Genymobile/scrcpy/tree/master/server/src/main/java/com/genymobile/scrcpy).

SDK de traducción: [ML Kit](https://developers.google.com/ml-kit/language/translation/android). Entrada PCM al reconocedor: [RecognizerIntent.EXTRA_AUDIO_SOURCE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE). Audio telefónico protegido: [AOSP AudioManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioManager.java).
