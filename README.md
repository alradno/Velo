# Velo

Aplicación Android personal para filtrar llamadas mediante reglas locales. Paquete: `com.alberto.velo`. Interfaz nativa Jetpack Compose; diseñada y observada en un OPPO con Android 17.

**Versión actual: 0.4.2 (código 7).** Bloqueo por reglas, listas de permitir/bloquear, horarios, perfiles, historial sin caducidad opcional, estadísticas y diagnóstico de permisos al abrir. Los APK y símbolos están en [Releases](https://github.com/alradno/Velo/releases/tag/v0.4.2).

El asistente que pregunta nombre/motivo y la traducción de llamadas están en investigación. Un laboratorio separado ya ha procesado voz francesa sintética, la ha transcrito con el reconocedor local del OPPO y ha traducido el texto al español. **El audio bidireccional real de SIM y la recepción automática todavía no están validados ni integrados en Velo.**

## Contenido del repositorio

- `app/`: aplicación Velo y pruebas JVM/instrumentadas.
- [`docs/`](docs/README.md): evolución, mediciones y alcance de las verificaciones.
- [`research/audio-lab/`](research/audio-lab/README.md): proyecto Android independiente para reconocimiento y traducción locales; incluye variantes de preparación de modelos y ejecución sin permiso de Internet.
- `research/audio-research/`: diagnóstico acotado de audio telefónico para un proceso shell. No forma parte del APK de Velo.

Las evidencias indican la versión sobre la que se midieron. Los archivos de validación conservan los hashes de las entregas locales; no deben interpretarse como hashes de un binario recompilado o de los ZIP de código que genera GitHub. No se incluyen claves de firma, configuración del SDK local, cachés de compilación, bases de datos de llamadas ni capturas del dispositivo.

## Compilar

Requiere JDK 17, Android SDK 36 y acceso a Google Maven/Maven Central para resolver dependencias. Crear `local.properties` con `sdk.dir` apuntando al SDK de Android instalado.

En Windows:

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease
.\gradlew.bat :app:assembleReleaseAndroidTest
```

El APK está en `app/build/outputs/apk/release/app-release.apk`.

La entrega personal usa la clave de desarrollo local de este ordenador para permitir actualizar la primera instalación sin borrar sus datos. No se incluye ninguna clave privada en el proyecto. Si se compila en otra máquina con otra clave, Android no permitirá actualizar la instalación existente: conservar y restaurar los datos mediante la exportación JSON antes de una reinstalación. Para publicar o distribuir a otras personas debe configurarse una clave de firma dedicada.

## Código

- `RuleEngine.kt`: modelos, prioridad de reglas y horarios; sin dependencias Android.
- `Store.kt`: JSON, CSV, ajustes, PIN, migración de preferencias y copias con WorkManager.
- `HistoryDatabase.kt`: historial SQLite indexado, migración transaccional, paginación, agregaciones y exportación CSV en flujo.
- `HistoryModels.kt`: periodos de calendario local, motivos y agrupación de gráficos.
- `HistoryScreen.kt`: historial con filtros y estadísticas interactivas.
- `HistorySummaries.kt`: resúmenes incrementales, contadores y catálogo de búsquedas mantenidos en la misma transacción que las llamadas.
- `HistoryPaging.kt`: paginación bidireccional con Paging 3 y cancelación nativa de consultas; dos lectores como máximo.
- `RecentAttempts.kt`: caché acotada para respetar la excepción por repetición mientras termina el guardado asíncrono.
- `Telephony.kt`: contactos, clasificación de identidad, respuestas al sistema y notificaciones.
- `MainActivity.kt`: inicio, listas, reglas, carpetas, horarios, registro y simulador.
- `SettingsScreen.kt`: perfiles, preferencias, copias y privacidad.
- `Theme.kt`: colores, tipografía y barras del sistema.
- `src/test`: 83 pruebas de lógica, formatos, cachés y diagnóstico de permisos.
- `src/androidTest`: 29 pruebas funcionales y escenarios medidos de 100.000/1.000.000 de registros y 5.000 reglas, sin llamadas y con almacenamiento aislado.

No incluye ConnectionService de pruebas, generador de llamadas, marcador, permisos para realizar llamadas, servicio InCallService ni permiso de Internet.

## Activación y límites

La app se inicia desactivada. Para filtrar, seleccionarla como filtro de llamadas de Android y activar la protección. Conceder contactos para distinguir números desconocidos. Puede existir un único filtro seleccionado; se conserva la app de teléfono habitual.

El funcionamiento con identidades privadas depende de las llamadas que Android entregue a `CallScreeningService`. En la versión 0.1.1 se confirmaron rechazos reales por número exacto en ambas SIM y llamadas privadas en SIM2. La 0.2.0 conserva ese motor de reglas y añade almacenamiento y estadísticas; su nueva migración y sus consultas se comprobaron sin generar llamadas. No efectuar pruebas que hagan sonar el dispositivo sin una petición del usuario.

Los filtros trabajan sin red. No se envían contactos a un servidor. Las copias JSON contienen listas, carpetas, perfiles y preferencias; excluyen PIN e historial. Las copias internas desaparecen al desinstalar. Exportar para conservarlas.

## Historial y estadísticas (0.2.0)

La retención `0` significa **Sin límite**, sin caducidad ni tope de entradas. La primera actualización desde 0.1.x activa este modo y conserva los demás ajustes. Las entradas anteriores se migran desde SharedPreferences a `files/velo-history.db` en una transacción idempotente; el origen se retira solo después de confirmar el destino. No se pueden recuperar registros que la versión antigua ya hubiera eliminado.

El historial se consulta por páginas de 100 entradas con cursor de fecha e identificador; todas las páginas siguen accesibles. Desde 0.3.0, Paging 3 carga y descarta páginas según el desplazamiento con una ventana configurada de 300 entradas y precarga de 20. Los cursores usan comparación compuesta indexada, incluso al volver a páginas descartadas. Las búsquedas tienen espera de 250 ms y cancelan el trabajo nativo anterior. El inicio consulta contadores incrementales.

Las estadísticas usan resúmenes diarios y horarios, y un ranking de números, actualizados mediante triggers en la misma transacción que los registros originales. Se conservan todas las llamadas y todos sus campos. Las consultas de periodos con límites parciales completan los extremos desde el historial original. Los resúmenes se reconstruyen si cambia la zona horaria. Una caché de seis resultados se invalida por revisión de la base de datos y zona horaria, incluidas inserciones, borrados y caducidad. En Android 15+ las lecturas consistentes no reservan la conexión de escritura.

La decisión de llamada no consulta el historial si la excepción por repetición está desactivada. Solo consulta contactos cuando pueden cambiar el resultado. Las reglas exactas usan índices en memoria, los patrones se preparan una vez por configuración y la configuración JSON validada se reutiliza hasta que cambia. El registro y las notificaciones se ejecutan en una cola independiente después de responder a Telecom; una caché de hasta 256 números recientes cubre el intervalo previo a la escritura sin mezclar privados. No se mantiene un servicio permanente ni una tarea de sondeo.

Los índices y resúmenes añaden espacio en disco a cambio de reducir lecturas y CPU. El historial permanente sigue creciendo en disco; la memoria del listado y las cachés están acotadas. Las mediciones documentadas son consultas sobre datos aislados y no una garantía de latencia para todas las distribuciones, teléfonos o estados térmicos.

La release se reduce con R8 y recursos sin usar. Las pruebas instrumentadas se compilan con `testBuildType = "release"`, usando su mapping; las API de la app, Kotlin y tracing necesarias para la instrumentación se mantienen estables en `proguard-rules.pro`. Para repetir las pruebas, instalar `app-release.apk` y `app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` con la misma firma.

En **Actividad → Estadísticas**: periodos Hoy/7/30/90/365 días/Todo; proporción filtrada sobre registros guardados; privadas, motivos, métodos de filtrado, evolución, horas y días de la semana, y cinco números visibles más insistentes. Pulsar un número abre su historial; tocar una barra muestra sus fechas y cantidades. Los privados no se agrupan como si fueran una persona identificable. Las cifras excluyen simulaciones y solo representan registros conservados de llamadas entregadas a Velo.

**Exportar historial · CSV** escribe todos los registros por flujo, independientemente de los filtros visibles. Es una exportación de consulta del historial, separada del CSV de reglas y de las copias JSON de configuración. No existe una importación de ese CSV de historial en esta versión. El almacenamiento sin límite permanece en este dispositivo: borrar datos, desinstalar o vaciar el historial elimina esas entradas y sus estadísticas.

Límites: 5.000 reglas, 50 perfiles, 200 carpetas, 100 periodos por perfil y 2 MB por importación. La copia diaria se ejecuta cuando Android permita la tarea periódica, no a una hora exacta. VoIP se infiere del modo de audio; no intercepta directamente llamadas de otras apps.



## Interfaz propia (0.4.0)

Reglas separa Bloquear y Permitir con contadores, búsqueda, carpetas y acceso a contactos. El editor ocupa la pantalla, agrupa número/coincidencia, organización y alcance, muestra una vista previa y mantiene el guardado visible con el teclado. Las opciones del perfil se despliegan bajo la lista. Inicio conserva su composición.

VeloControls.kt implementa botones, chips, campos BasicTextField, interruptores Canvas, deslizador, diálogos y progreso. VeloIcons.kt contiene la familia vectorial original. VeloDateTime.kt implementa el calendario y selector de hora. Se utilizan en reglas, horarios, historial, estadísticas, ajustes, PIN, perfiles, carpetas, copias y simulador. Los selectores de contactos, documentos y permisos siguen siendo pantallas del sistema. Los controles exponen estados semánticos, etiquetas de entrada y foco; las animaciones respetan el ajuste de Velo y la escala global del sistema. El editor se integra en la ventana principal para respetar teclado e insets del OPPO.

La revisión física 0.4.0 comprobó navegación, editor con teclado, tipos de coincidencia, perfiles, cancelación de borradores, diálogos de horario/hora y calendario, preferencias e historial/estadísticas. No se generaron llamadas. Pasaron 71 pruebas JVM y 29 instrumentadas; lint no tiene errores. El motor y los archivos de almacenamiento/paginación son idénticos a 0.3.0; los benchmarks de un millón de registros pertenecen a esa versión, no se repitieron para la actualización visual.


## Permisos y diagnóstico de apertura (0.4.1)

El icono de Reglas contiene tres deslizadores completos. El primer arranque muestra un asistente: Configurar solicita en secuencia los permisos y ajustes pendientes, omite los ya concedidos y evita volver a solicitar el mismo ajuste en esa pasada si se deniega. Puede reintentarse explícitamente. Si el permiso ya no admite diálogo, abre la información de la app.

SetupController.kt comprueba el rol CALL_SCREENING, READ_CONTACTS, POST_NOTIFICATIONS/areNotificationsEnabled y el canal de llamadas, isIgnoringBatteryOptimizations, isBackgroundRestricted y getUnusedAppRestrictionsStatus. Se ejecuta al reanudar la actividad y volver de los ajustes; al entrar de nuevo en primer plano muestra un aviso si falta algo y mantiene una franja visible tras posponerlo. No usa servicios ni sondeo. Readiness.kt separa el estado comprobado de la política y la planificación de solicitudes; SetupScreen.kt presenta el asistente y el aviso con los controles propios.

Las preferencias de introducción y revisión manual de OPPO están en velo-setup, separadas de las reglas y las copias JSON. Los permisos nunca se dan por concedidos por un resultado de actividad: se vuelve a consultar Android. La configuración propia de ColorOS que carece de API pública lleva una indicación de revisión manual, incluso después de registrarla; no se afirma que Velo pueda detectar automáticamente cambios posteriores en Inicio automático.

La excepción de batería se solicita mediante REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, con alternativa al listado de optimización si no existe el diálogo directo. Esta es una entrega personal; lint advierte de la política de Google Play aplicable a esta solicitud directa. Una eventual publicación exigiría revisar el caso de uso y las condiciones de la tienda.

Validación 0.4.1: 83 pruebas JVM (12 nuevas sobre diagnóstico y solicitudes) y 29 instrumentadas correctas, lint con 0 errores/31 avisos. En OPPO Android 17: introducción, posponer, aviso persistente, aviso al reabrir, corrección de permisos por inactividad desde el asistente, relectura al volver y reapertura sin avisos tras configurar. Batería ya excluida y actividad de fondo permitida, ambas observadas. Revisión manual de ColorOS registrada. Historial y configuración de filtrado conservados por hashes antes/después. No se generaron llamadas. Como comprobación adicional, instalación limpia en emulador API 34: rol solicitado, contacto denegado sin bucle, continuación a batería/inactividad, reintento de contactos y concesión; las notificaciones ya las concedió Android por el rol. No sustituye la evidencia física del OPPO ni valida llamadas por operador.

## Ajuste de texto (0.4.2)

Se retiró el aviso informativo «Velo funciona como filtro…» de Ajustes. Se conserva el diagnóstico de permisos, las advertencias de configuración pendiente y el comportamiento del motor. La entrega 0.4.2 se compiló y comprobó visualmente en el OPPO; las verificaciones físicas e instrumentadas anteriores mantienen el alcance documentado de su versión.
