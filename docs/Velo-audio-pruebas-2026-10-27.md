# Velo: pruebas de voz y traducción en el OPPO

27 de octubre de 2026. OPPO CPH2913, Android 17. Continuación de la investigación del día 26.

**La cadena local de voz francesa → texto francés → traducción al español ya ha funcionado en el teléfono con audio sintético. La extracción e inserción de audio de una llamada por la SIM siguen pendientes.** No hay todavía asistente integrado en Velo ni recepción automática validada.

## Resultados comprobados

| Etapa | Resultado en el OPPO | Alcance |
|---|---|---|
| Reconocedor expresamente local desde una app normal | Disponible | Proveedor del sistema, sin reconocimiento remoto de reserva |
| Soporte de francés y español | Declarado por el proveedor | No demuestra precisión con todos los acentos |
| Modelo francés de reconocimiento | Descargado por Wi-Fi e instalado | Verificado después mediante consulta de soporte |
| Voz francesa local | Seis voces instaladas candidatas; síntesis correcta | Voz usada: `fr-FR-language`, declara no requerir red |
| Archivo francés → texto francés | Correcto en una frase sintética | Entrada por pipe PCM mono 16 kHz, sin micrófono |
| Texto reconocido → español | Completado | Redacción mejorable en «hombre de entrega» |
| Traducción de tres textos fijos | Completada sin permiso de Internet en el laboratorio | Dos traducciones adecuadas; una ambigüedad produjo un error de sentido |
| Canales telefónicos en reposo | Rechazan abrirse con MODE_NORMAL | No equivale a funcionamiento durante una llamada |
| Espera de prueba sin llamada | Sale por tiempo límite, sin abrir audio | Prueba en seco de dos segundos |
| Audio real de SIM en ambos sentidos | Sin comprobar | Falta otra línea/persona disponible para llamar |
| Contestar antes de avisar y entregar después al usuario | Sin comprobar | Restricción del marcador del sistema todavía por resolver |

## Prueba completa con audio sintético

Se instaló un laboratorio independiente, `com.alberto.velo.audiolab`, que no sustituye a Velo y no tiene interfaz de inicio ni filtro de llamadas. Su versión final **carece de permisos INTERNET y RECORD_AUDIO**, comprobado durante la propia ejecución. Los modelos se prepararon antes por Wi-Fi con otra variante del mismo laboratorio.

La frase de prueba fue:

> Bonjour. Je m'appelle Marie. Je suis le livreur. Je suis devant votre porte.

El reconocedor local devolvió:

> bonjour Je m'appelle Marie je suis le livreur je suis devant votre porte

La traducción del texto realmente reconocido fue:

> Hola, mi nombre es Marie, soy el hombre de entrega, estoy frente a tu puerta.

La síntesis produjo audio a 24 kHz, convertido para el ensayo a PCM mono de 16 kHz. Se enviaron 179.668 bytes: aproximadamente **5,61 segundos de voz**. El envío y el reconocimiento completo terminaron en **5,85 segundos**; la traducción posterior tardó **181 ms**. Son medidas de una prueba sintética, no una garantía de latencia durante llamadas.

No se reprodujo esa voz por el altavoz, no se abrió el micrófono, no se hicieron ni contestaron llamadas y se eliminó el archivo temporal. El reconocedor recibió un descriptor PCM explícito mediante la API [EXTRA_AUDIO_SOURCE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE). La creación del reconocedor fue expresamente local, no un reconocedor general con una mera preferencia por trabajar sin red.

La ausencia de INTERNET se comprobó para el proceso del laboratorio. No se desconectó globalmente la conectividad del OPPO ni se auditó el tráfico de todas las apps del sistema. El proveedor de voz se seleccionó mediante la API local y la traducción usa el motor local documentado por [ML Kit](https://developers.google.com/ml-kit/language/translation).

## Calidad de traducción y tiempos

En una ejecución independiente, ya con la variante sin permiso de Internet:

| Francés | Resultado literal en español | Tiempo |
|---|---|---:|
| Bonjour, je m'appelle Marie. Je vous appelle pour confirmer notre rendez-vous demain à dix heures. | Hola, mi nombre es Marie. Te llamo para confirmar nuestra cita mañana a las diez en punto. | 201 ms |
| Je suis le livreur. Je suis devant votre porte. | Soy el repartidor. Estoy frente a tu puerta. | 26 ms |
| Pouvez-vous me rappeler cet après-midi ? | ¿Puedes recordar esta tarde? | 30 ms |

La tercera frase debería transmitir la petición de devolver una llamada; el resultado pierde ese sentido. Conviene mostrar siempre el texto original junto a la traducción. No usaría una traducción automática como única base para rechazar llamadas. Tres ejemplos no permiten estimar una tasa de acierto general.

## Corrección de un resultado preliminar

Una primera consulta desde el programa shell de ADB devolvió que el reconocedor local no estaba disponible. Otra consulta de soporte chocó con la identidad de paquete de ese contexto de diagnóstico. **Ese resultado no describía la disponibilidad para una app normal.** Por eso se construyó y probó el laboratorio con su propio paquete y UID. Desde ese contexto, la API local está disponible y el modelo francés funciona con entrada PCM.

Los Subtítulos automáticos de Google y la API de reconocimiento para apps no deben tratarse como la misma configuración. Descargar este modelo francés no demuestra que el menú de subtítulos ahora traduzca llamadas al español; esa función sigue sin observarse en el menú examinado el día 26.

## Programa preparado para la siguiente llamada

El diagnóstico telefónico está compilado y sus comprobaciones en reposo pasan. Su modo de llamada, todavía sin ejecutar durante una llamada real:

1. Espera como máximo 120 segundos a una llamada telefónica ya contestada.
2. Intenta abrir extracción del audio recibido e inserción del audio enviado.
3. Durante un máximo de 16 segundos mide muestras recibidas, sin guardar la conversación, e intenta enviar la frase fija «Hola. Esta es una prueba de audio de Velo».
4. Libera los recursos. No llama, contesta ni cuelga; tampoco cambia modo de audio, volumen o micrófono. Tiene un watchdog que limita la duración total del proceso.

La otra persona deberá confirmar que oye la frase. El audio recibido deberá contener su voz y posteriormente se comprobará su transcripción. Un contador de muestras distinto de cero no prueba por sí solo que recibamos voz inteligible del interlocutor.

Si ambos canales funcionan, podremos conectar al audio real la cadena local ya probada. Después faltará validar recepción automática, aislamiento del micrófono del usuario, aceptación de la conversación, llamadas con pantalla bloqueada, Bluetooth y recuperación ante errores. Android mantiene una [restricción de UID del marcador del sistema para su mecanismo interno de filtrado mediante audio](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telecomm/java/android/telecom/CallScreeningService.java); el éxito del procesamiento local no elimina esa restricción.

## Estado dejado en el teléfono

- Velo conserva versión **0.4.2, código 7**, y sigue siendo el filtro de llamadas del sistema.
- No se modificaron el código instalado, las reglas o los ajustes de Velo para estas pruebas.
- El laboratorio sin permiso de Internet queda instalado para continuar. No arranca automáticamente ni mantiene un servicio permanente.
- Los modelos preparados quedan disponibles. No se cambiaron interruptores de subtítulos, Wi-Fi, datos, micrófono o volumen del teléfono.
- Los archivos JAR/PCM temporales del diagnóstico telefónico se retiraron del teléfono al finalizar los ensayos.
- No había otra persona/línea disponible, por lo que no se realizaron llamadas de prueba.

Las evidencias estructuradas, hashes y salidas están en `Velo-audio-resultados-2026-10-27.json`. El APK del laboratorio y sus fuentes se entregan por separado; no son una actualización de Velo.
