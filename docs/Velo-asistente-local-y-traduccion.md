# Velo: asistente de llamadas local y traducción

Investigación del 26 de octubre de 2026. Dispositivo observado: OPPO CPH2913, Android 17/API 37, firmware CPH2913_17.0.0.106(EX01). Velo permanece en la versión 0.4.2. Este informe distingue observaciones del teléfono, documentación y propuestas todavía sin validar.

**Actualización del 27 de octubre:** la cadena local de voz sintética francesa → reconocimiento → traducción al español ya se ha probado en el OPPO. El audio de SIM continúa pendiente. Véase `Velo-audio-pruebas-2026-10-27.md`; el resto de este documento conserva el estado de la investigación del día 26.

## Resultado

**Hay una vía experimental razonable para un asistente completamente local, mediante un ayudante con privilegios de ADB, posiblemente gestionado con Shizuku. Todavía no está demostrado que pueda reproducir toda la experiencia de Apple en este OPPO.** La prueba pendiente es el acceso bidireccional real al audio y el control de la llamada antes de presentarla al usuario.

Con los permisos normales de Velo no basta. El filtro actual decide permitir, silenciar o bloquear; no recibe el audio del llamante. La API ordinaria exige responder en cinco segundos. El mecanismo interno para continuar mediante procesamiento de audio está reservado al UID del marcador del sistema. Elegir Velo como filtro, o convertirlo en marcador predeterminado instalado por el usuario, no equivale a obtener ese UID. Fuentes: [API pública de filtrado](https://developer.android.com/reference/android/telecom/CallScreeningService) y [restricción explícita en AOSP, método setShouldScreenCallViaAudioProcessing](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telecomm/java/android/telecom/CallScreeningService.java).

**Los Subtítulos automáticos abiertos en el OPPO no ofrecen actualmente traducción de francés a español.** La pantalla permite elegir idiomas de reconocimiento, no un idioma de salida para traducir.

## Lo observado en los subtítulos del OPPO

La pantalla pertenece a Google Android System Intelligence (`com.google.android.as`), versión `C.5.playstore.oemfull.948771517`.

- Subtítulos automáticos activados.
- Idiomas instalados: español de España, beta, e inglés de Estados Unidos.
- Francés de Francia, beta, disponible en «Añadir un idioma», todavía sin descargar.
- La explicación de idiomas dice que el teléfono muestra subtítulos cuando se hablan los idiomas enumerados. Dos idiomas instalados no significan traducción entre ellos.
- Aparece «Subtítulos automáticos en llamadas» y está activada la opción «Escribir respuestas durante llamadas». El sistema indica que lee esas respuestas a la otra persona mediante Servicios de voz de Google.
- La pantalla declara que audio, subtítulos y respuestas escritas se procesan en el dispositivo.
- No aparece un selector de traducción ni una opción de salida en español.

No se descargaron idiomas ni se cambiaron interruptores. Se restauró la pantalla original de ajustes antes de terminar el acceso al teléfono. No se hizo una llamada en francés para comprobar la transcripción.

Google documenta la transcripción de llamadas y distingue la traducción de subtítulos mediante Live Translate en determinados Pixel. Por tanto, la presencia de Subtítulos automáticos en un OPPO no demuestra que incluya esa traducción. Añadir francés permitiría intentar obtener texto francés; no habilita por sí solo francés → español. [Ayuda oficial de Subtítulos automáticos](https://support.google.com/accessibility/android/answer/9350862?hl=es).

Las respuestas escritas podrían servir para pedir manualmente nombre y motivo después de contestar, si funcionan en la llamada concreta. Eso tampoco demuestra recepción automática antes de avisarte.

## Diagnóstico local del audio: resultado prometedor, alcance limitado

Se ejecutó un programa temporal bajo el usuario `shell` de ADB, UID 2000. El programa solo comprobó permisos, métodos y capacidades declaradas. **No creó grabadores ni reproductores, no capturó audio, no hizo ni contestó llamadas.** El archivo temporal del teléfono fue eliminado al terminar.

| Comprobación | Resultado observado |
|---|---|
| `CAPTURE_AUDIO_OUTPUT` para shell | Concedido |
| `CALL_AUDIO_INTERCEPTION` para shell | Concedido |
| Método `isPstnCallAudioInterceptable()` | Existe y devuelve `true` |
| Método de extracción de audio recibido | Existe |
| Método de inserción de audio enviado | Existe |
| Puertos de telefonía de entrada y salida | Ambos declarados |
| Audio de una llamada real | Sin probar |
| Arranque mediante Shizuku y estabilidad | Sin probar |

La salida íntegra está en `Velo-audio-oppo-diagnostico.txt`; su interpretación está en `Velo-audio-oppo-evidencia.json`.

AOSP contiene métodos internos para obtener un `AudioRecord` del audio recibido y un `AudioTrack` que inserte audio hacia el llamante. Requieren `CALL_AUDIO_INTERCEPTION` y un modo de audio de llamada compatible. La consulta positiva comprueba la capacidad declarada; no demuestra que el fabricante permita crear y utilizar ambos canales simultáneamente durante una llamada. [Implementación de AudioManager en AOSP](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioManager.java).

La fuente AOSP citada es la rama `main`, no el código exacto del firmware OPPO. La existencia de los métodos, la respuesta positiva y los permisos indicados sí se comprobaron en este teléfono.

## Qué podría hacer un ayudante local

Un proceso iniciado con ADB podría encargarse del audio protegido y comunicarse con Velo dentro del teléfono. Shizuku es una posible forma de gestionar ese proceso; no es un servidor externo. Su funcionamiento concreto en este firmware todavía debe probarse. Sin root, su arranque debe repetirse después de reiniciar el móvil. [Funcionamiento de Shizuku](https://shizuku.rikka.app/introduction/) y [guía de arranque](https://shizuku.rikka.app/guide/setup/).

Es necesario resolver dos problemas independientes:

1. **Audio:** recibir la voz del llamante e introducir una pregunta sintetizada que la otra persona escuche con claridad.
2. **Control:** aceptar la comunicación para que el asistente pueda hablar, mantener el micrófono del usuario aislado y ofrecer después aceptar o rechazar. El camino reservado al marcador del sistema impide asumir que este flujo funcionará solo por disponer de audio.

Una llamada ya contestada por el asistente puede contar como contestada para el operador y el historial, aunque el usuario todavía no haya decidido hablar. Su presentación, sonido, transferencia y cierre deben validarse como parte de la experiencia. No se promete una copia exacta de Apple.

## Voz y traducción sin enviar la llamada a servidores

Una vez conseguido el audio, la cadena propuesta es:

`audio recibido → reconocimiento local de voz → texto original → traducción local al español → pantalla de Velo`

Para preguntar nombre y motivo:

`preguntas prefijadas → voz sintetizada local → audio enviado al llamante`

Hay motores para estas etapas:

- **Reconocimiento:** Android ofrece un reconocedor expresamente local, cuya disponibilidad debe consultarse. Otra opción es ML Kit Speech Recognition en modo Basic, que admite audio PCM mediante descriptor y contempla francés y español. Esta última API está en alfa: habría que comprobar soporte y modelos en el OPPO antes de elegirla. No se debe recurrir silenciosamente a reconocimiento remoto. [SpeechRecognizer de Android](https://developer.android.com/reference/android/speech/SpeechRecognizer) y [ML Kit Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android).
- **Traducción:** ML Kit dispone de traducción de texto en el dispositivo y admite francés y español. Entre idiomas distintos del inglés utiliza este como paso intermedio, por lo que habría que valorar errores y latencia con frases reales. Los modelos requieren descarga inicial; el procesamiento posterior puede ser local. [Traducción local](https://developers.google.com/ml-kit/language/translation) e [idiomas admitidos](https://developers.google.com/ml-kit/language/translation/translation-language-support).
- **Voz:** se debe seleccionar una voz de síntesis que no necesite conexión. Que un motor esté instalado no garantiza que todas sus voces sean locales. [Propiedad isNetworkConnectionRequired](https://developer.android.com/reference/android/speech/tts/Voice).

Pedir nombre y motivo no exige un modelo conversacional grande. Dos preguntas separadas, transcripción literal y botones para aceptar o rechazar pueden dar una experiencia útil con menor consumo. El nombre es lo declarado por la persona; no constituye una identidad verificada.

## Asistente nativo de OPPO

Existe el paquete privilegiado `com.oplus.aicall`, versión 17.7.12, con componentes relacionados con asistencia y traducción. Sin embargo, su pantalla interna de ajustes rechazó incluso el acceso de ADB porque exige `com.oplus.permission.safe.PHONE`. Los ajustes visibles de Teléfono no mostraban un asistente equivalente ni traducción de llamadas.

La presencia del paquete no confirma que estas funciones estén habilitadas para este modelo y región, que funcionen en español y francés, que sean locales o que Velo pueda invocarlas. No es una dependencia viable demostrada.

## Próxima comprobación cuando vuelva el OPPO

Primero conviene un prototipo de diagnóstico separado, sin modificar el filtrado estable de Velo:

1. En una única llamada acordada, comprobar extracción del audio recibido e inserción de una frase breve. Limitar duración y liberar recursos al colgar o ante cualquier error.
2. Comprobar aislamiento del micrófono del usuario, salida por auricular, altavoz y Bluetooth, y recuperación del audio normal al tomar la llamada.
3. Validar el control de la llamada y determinar si es posible una recepción automática sin aviso previo, o si únicamente puede ofrecerse una asistencia tras contestar.
4. Solo si lo anterior funciona, probar reconocimiento, voz y traducción con modelos descargados y datos/Wi-Fi desconectados, manteniendo disponible la línea telefónica.
5. Después medir latencia, batería y estabilidad con pantalla bloqueada, ambas SIM y pérdida/reinicio del ayudante.

Para conservar el rendimiento de Velo, el diseño debe activar los modelos solo durante las llamadas que lo necesiten, usar buffers de audio acotados en memoria, limitar la cola de traducción y liberar sesiones al terminar. No debe guardar audio de forma permanente por defecto. Si el ayudante no está disponible, las reglas normales de bloqueo deben seguir funcionando y el estado del asistente debe verse claramente.

**Estado al cerrar la investigación:** diagnóstico de capacidades completado; audio y recepción reales pendientes; sin cambios de código, APK o configuración de Velo para esta función. Desde que el usuario se llevó el OPPO no se ha vuelto a acceder al dispositivo.
