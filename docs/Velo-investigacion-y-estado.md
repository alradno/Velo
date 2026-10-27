# Velo: app personal de bloqueo de llamadas

26 de octubre de 2026. OPPO CPH2913, Android 17 / API 37.

## Actualización instalada 0.3.0: rendimiento con historiales grandes

Se optimizaron historial, búsquedas, estadísticas y ruta de decisión de llamadas. La memoria del listado se limita mediante Paging 3, los resúmenes y contadores se mantienen incrementalmente, se cancelan consultas obsoletas y se usan cachés acotadas e índices para las reglas y los datos. El guardado se separa de la respuesta a Telecom. Se redujo el APK a 4,41 MB frente a 40,09 MB de la 0.2.0.

En la release final instalada pasaron 71 pruebas unitarias, 29 pruebas funcionales en el OPPO y cargas aisladas de 100.000/1.000.000 de registros y 5.000 reglas. Con un millón: estadísticas completas 15,94 ms, primera página 2,38 ms y búsqueda de un número 12,27 ms. Son mediciones puntuales de consultas, no latencias completas de la red o de pantalla. El historial real conserva sus ocho registros y ajustes sin alteraciones y la protección sigue activada. Detalles y límites: `Velo-0.3.0-optimizacion.md`; evidencia: `Velo-0.3.0-rendimiento.json`.

## Actualización instalada 0.2.0: historial permanente y estadísticas

Se activó «Sin límite» y se eliminó tanto el límite temporal como el recorte oculto a 5.000 entradas. El historial se migró de SharedPreferences a SQLite con transacción y marcador idempotente. La auditoría de los ocho registros reales confirmó que todos sus campos e identificadores coinciden antes y después, y que los demás ajustes se conservan. La protección sigue activada. Se añadieron paginación, búsqueda indexada y exportación de todo el historial a CSV.

Actividad ofrece Historial y Estadísticas: periodos de calendario local, proporción filtrada sobre registros conservados, motivos y métodos, evolución interactiva, horas, días y números visibles más insistentes. No se consideran los privados una persona identificable ni se estima tiempo ahorrado. La pantalla se comprobó con los datos reales: 6 filtradas de 8 registros, 75 %, incluidas 2 privadas filtradas.

Pasaron 63 pruebas unitarias y 11 pruebas de historial aisladas en el OPPO físico, incluida la conservación y paginación de más de 5.000 entradas de hace más de dos años. Release y Lint no presentan errores. Los resultados están en `Velo-0.2.0-validacion.json` y las instrucciones en `Velo-0.2.0-novedades.md`. Los ensayos del operador descritos más abajo corresponden a 0.1.1; esta actualización se validó sin generar nuevas llamadas. Se retiraron las herramientas temporales de prueba.

## Prueba real de número privado confirmada

Se comprobaron tres llamadas privadas recibidas por la SIM2 el 26 de octubre, hora de Madrid. Android identifica las tres con número nulo y presentación `Restricted`: TC@53 a las 21:10:20 fue rechazada por Velo; TC@54 a las 21:10:57 fue permitida y el historial de Velo indica «Protección desactivada»; TC@55 a las 21:11:23 fue rechazada por Velo. Las dos rechazadas terminaron con `REJECTED / INCOMING_REJECTED`. La última aparece en Velo como «Privado / oculto», «Rechazar · Número privado» y «Filtrado · 5 ms de evaluación». El filtrado completo de Telecom tardó 26 ms en ese último caso y 50 ms en el primero.

En la comprobación posterior la protección está activada. La prueba confirma que este OPPO entrega a Velo llamadas privadas reales y ejecuta el rechazo por su filtro, conservando Google Teléfono como marcador. Esta observación específica del dispositivo y sus operadores es más precisa para esta configuración que la limitación general descrita en la documentación pública de Android. Queda por comprobar mediante una llamada real el filtro de número visible fuera de contactos; no se requiere identificar el número oculto para bloquearlo.

## Prueba real por operador confirmada

El usuario añadió una regla de número exacto para un contacto y realizó llamadas desde otra línea. Antes de la prueba se comprobó que la regla estaba habilitada, compartida por todos los perfiles y configurada para heredar «Rechazar». El simulador, respetando la activación y pausa reales y marcando el origen como contacto, devolvió «Rechazar · Regla».

Telecom confirmó cuatro recepciones reales mediante `com.android.services.telephony.TelephonyConnectionService`: TC@48 y TC@49 en SIM2, TC@50 y TC@51 en SIM1, entre las 21:02:21 y las 21:03:01, hora de Madrid. Las cuatro registran `SCREENING_COMPLETED: Reject`, identifican a Velo como responsable y terminan con `REJECTED / INCOMING_REJECTED`. El filtrado completo del sistema tardó entre 57 y 162 ms; no es el tiempo aislado del motor de reglas. El usuario confirmó que no sonó el OPPO, apareció una notificación y la persona que llamó recibió una indicación de no disponible. Esa experiencia corresponde a estas llamadas y sus operadores, sin garantizar el mismo anuncio o buzón en todas las redes.

La prueba de número exacto está confirmada en ambas SIM. En la revisión inmediatamente posterior el interruptor general aparecía apagado; no se modificó. El usuario lo reactivó después, como se verificó al comprobar las llamadas privadas. Los apartados siguientes conservan el historial de las verificaciones anteriores.

## Actualización 0.1.1

Se corrigieron tres defectos encontrados en una revisión posterior sin llamadas:

- Editar o activar una regla mantiene su posición y su prioridad frente a otras reglas coincidentes.
- Añadir desde la lista de permitidos abre una regla permitida y conserva la carpeta seleccionada. Se comprobó el interruptor activado en el formulario del OPPO, sin guardar una entrada de prueba.
- Los patrones «Contiene» y «Termina en» conservan los ceros iniciales de su contenido, por ejemplo `00` o `0012`. Los prefijos internacionales siguen aceptando `0034` y `+34`.

Resultado: 50 pruebas unitarias correctas, compilación release correcta y Android Lint sin errores. La 0.1.1 está instalada. Tras la nueva autorización expresa del usuario para hacer llamadas y comprobaciones, se seleccionó Velo como filtro y se activó su protección. Google Teléfono sigue siendo la app de llamadas y se conservó el modo vibración.

## Validación local autorizada de la 0.1.1

El usuario autorizó reanudar las llamadas de prueba con el teléfono en vibración. Se usó una herramienta temporal separada del APK de Velo, con cierre de cada llamada antes del siguiente escenario y cierre de respaldo a los seis segundos. Se comprobó que al terminar no quedaban llamadas de ensayo activas.

Pasaron 14 escenarios a través de Telecom en el OPPO: desconocido visible, privado, identidad no disponible, bloqueo total, número exacto, prefijo, patrón con ceros, permitidos sobre bloqueo total, silenciar sin colgar, ignorar, fuera de horario, carpeta pausada y los dos intentos de la excepción por repetición. Las evaluaciones de Velo tardaron entre 2 y 18 ms. Los casos de rechazo terminaron la conexión de prueba. Los casos permitidos y silenciados conservaron la conexión hasta su cierre controlado. La herramienta y sus entradas de registro se retiraron al terminar.

Los resultados detallados están en `Velo-validacion-local.json`. Son llamadas sintéticas locales en el teléfono físico; no se han presentado como llamadas recibidas por el operador.

El Samsung conectado no tiene SIM y el usuario prefiere no trasladar las del OPPO. El OPPO reporta `DSDS`; sus registros IMS también anuncian capacidad de llamadas simultáneas, lo que no demuestra que esté disponible con ambas líneas y operadores actuales. Se realizó un único intento real desde la SIM española hacia la francesa, ambas en el OPPO. Telecom registró la salida por SIM1 a las 20:51:48 y su cierre local a las 20:51:57, sin registrar una nueva llamada entrante. El ensayo es inconcluyente para el bloqueo: no hubo recepción que Velo pudiera evaluar. No se atribuye a Velo un fallo ni un éxito del operador. Una llamada desde otra línea activa sigue siendo la comprobación pendiente.

Al terminar se verificaron: ninguna llamada activa, solo el paquete de producción de Velo instalado, contactos y notificaciones concedidos, Velo como filtro, Google Teléfono como marcador y modo vibración conservado. No se movieron ni se reconfiguraron las SIM.

APK actual: `Velo-0.1.1.apk`. SHA-256: `5045721468b6e211f7912d76354e0747d43334081bea1d0c0b9a08dbc6b7ffbb`.

## Resultado y estado

Velo es una aplicación nativa Android con Jetpack Compose. El APK final funciona como filtro de llamadas mediante `CallScreeningService`, igual que la versión instalada de Calls Blacklist. Conserva Google Teléfono. No incluye un marcador de teléfono ni un generador de llamadas de prueba.

**Estado actual:** número exacto de un contacto rechazado por Velo en llamadas reales sobre ambas SIM; llamadas privadas reales rechazadas en SIM2. Queda verificar por operador el filtro de desconocidos fuera de contactos. Velo sigue seleccionada como filtro, Google Teléfono como app de llamadas y la protección está activada.

## Referencia comprobada

Se ha inspeccionado Calls Blacklist 4.16.1, código 697, paquete `com.vladlee.easyblacklist`, instalada en el OPPO. Su APK declara un servicio `CallFilteringService`, permisos de contactos y notificaciones, y no declara recepción de SMS ni un `InCallService`. La referencia funcional de esta investigación es esa versión de llamadas; no se han atribuido al producto actual prestaciones SMS de versiones antiguas.

Se observaron en pantalla los filtros de privados, desconocidos, todas las llamadas, llamadas durante VoIP y el interruptor de lista blanca. También se comprobaron los menús de perfiles, horarios, historial, contraseña, tamaño de fuente, notificaciones, copias, importación y exportación.

La inspección estática confirmó tres respuestas de Android: rechazar (`disallow=true`, `reject=true`), ignorar (`disallow=true`, `reject=false`) y silenciar (`silence=true`). Esto describe implementación; no demuestra por sí solo el resultado con un operador.

Evidencia REA del APK: `ev_20e0406da64222913e2404f0addad3e000d20a20f58b8d2a8b87a61f900e4113`. Método de filtrado: `ev_da0cd54f619c7874b43de4b4b099b58ec8995872e45506d3c4174a98154b44da`. SHA-256 del APK inspeccionado: `1d2ee214da52369fad22260ca251610ed68fad54a5e92fa061f1cc0fb929ac52`.

## Funciones creadas

| Función de la referencia | Implementación en Velo |
|---|---|
| Privados y desconocidos | Filtros independientes para identidad oculta, identidad no disponible y número visible fuera de contactos |
| Bloqueo total y durante VoIP | Opciones por perfil; VoIP se infiere del modo de audio del dispositivo |
| Lista negra y blanca | Números exactos, prefijos, contenido y sufijos; prioridad de permitidos configurable |
| Contactos y entrada manual | Selector de contactos y edición manual; acceso al historial local de Velo |
| Carpetas | Crear, renombrar, activar, pausar y quitar carpetas conservando sus reglas |
| Búsqueda y notas | Buscar número o nota; editar, activar y eliminar reglas |
| Métodos de bloqueo | Rechazar, ignorar o silenciar; método del perfil o específico por regla |
| Horarios | Varios periodos por día, días independientes, cruce de medianoche y día completo |
| Perfiles | Duplicar, renombrar, elegir y eliminar; reglas compartidas o limitadas a perfiles concretos |
| Registro | Decisión, motivo, fecha y tiempo de evaluación; añadir números a ambas listas |
| CSV y texto | Importar números por línea o CSV de Velo; exportar listas en CSV |
| Copia y restauración | JSON de listas, carpetas, perfiles y ajustes; historial local; tarea diaria aproximada |
| Contraseña | PIN de 6 a 12 dígitos, hash PBKDF2 con sal y bloqueo de capturas mientras está activo |
| Notificaciones | Avisos de Velo y preferencia para ocultar avisos de bloqueo del sistema |
| Aspecto | Tema oscuro, claro o del sistema; escala de texto y animaciones configurables |
| Añadidos | Simulador sin llamadas, pausa temporal y excepción para llamadas visibles repetidas |

El historial muestra las llamadas que Android entrega a Velo. No es una importación del registro de Call Blacklist ni una copia de todo el registro del sistema. El CSV exporta números y sus atributos básicos; la copia JSON conserva la configuración completa y el ámbito de perfiles.

## Orden de las reglas

1. Permitir emergencias identificadas por Android.
2. Respetar interruptor general, pausa temporal y horarios.
3. Aplicar la lista de permitidos si está activada en el perfil.
4. Aplicar la excepción opcional por repetición de un número visible.
5. Buscar reglas de bloqueo habilitadas, con carpeta activa y perfil aplicable. Si coinciden varias, se usa la primera guardada.
6. Evaluar bloqueo total, privado, no disponible, VoIP y desconocido.
7. Permitir si no hay coincidencia. Si no se pueden consultar contactos, no tratar automáticamente el número como desconocido.

Los periodos nocturnos pertenecen al día de inicio. Por ejemplo, lunes 22:00–08:00 termina el martes a las 08:00. Inicio y fin iguales significan todo el día seleccionado. Se usa la zona horaria del teléfono.

## Android, privados y operador

La documentación pública de Android indica que `CallScreeningService` no recibe algunas presentaciones restringidas o desconocidas. En este OPPO se verificó primero la entrega de llamadas sintéticas privadas y no disponibles. Después se confirmó también la recepción y rechazo de llamadas privadas reales por SIM2, con presentación `Restricted` y Velo como filtro. Google Teléfono permanece como app de llamadas. No se generaliza este comportamiento a otros modelos o versiones del sistema.

La app responde al sistema antes de cinco segundos. Si una consulta tarda demasiado, permite la llamada a los 3,5 segundos. Las consultas son locales; no hay permiso de Internet ni servicios de publicidad.

Rechazar no garantiza evitar el buzón de voz: depende del operador. Silenciar permite que la llamada siga visible. El filtro VoIP se refiere a llamadas telefónicas recibidas durante una conversación de voz; no intercepta directamente llamadas de WhatsApp u otras apps.

## Verificación e incidencia de la 0.1.0 (histórico)

- 47 pruebas unitarias pasaron: prioridades, patrones, contactos, privados, horarios, medianoche, cambio de hora, perfiles, JSON y CSV.
- 10 pruebas Android pasaron en el OPPO: respuestas del sistema, persistencia, copias, PIN y normalización de números.
- 6 escenarios sintéticos pasaron con Velo como filtro y teléfono durante una prueba temporal.
- 3 escenarios sintéticos pasaron con Velo como filtro y **Google Teléfono** como teléfono: desconocido visible, privado y no disponible.
- La ruta alternativa exclusivamente como teléfono no pasó: no produjo los eventos esperados. Esa implementación se retiró del producto final.
- Algunas llamadas sintéticas quedaron sonando y el usuario tuvo que colgarlas. Se detuvieron las pruebas, se retiró la cuenta de telefonía de prueba y se restauraron Call Blacklist y Google Teléfono. No se generarán más llamadas sin petición del usuario.
- Se retiraron las entradas de registro identificadas por el componente de la cuenta de prueba de Velo y su identificador `velo-qa`, conservando las demás llamadas.
- Las capturas de pantalla corresponden a la interfaz en el OPPO. La compilación final y su comprobación estática se registran junto a la entrega.

Estos resultados no equivalen a una certificación de bloqueo de llamadas reales, duración de batería, funcionamiento tras reinicios o todas las particularidades de ColorOS. No se han cambiado los ajustes de ahorro de batería del teléfono.

La compilación final `release` pasó 47 pruebas unitarias y Android Lint sin errores (19 avisos de dependencias y estilo). El APK firmado se instaló correctamente; arranque en frío observado: 263 ms. Se revisaron inicio, reglas, horarios y ajustes en el teléfono. Las pruebas Android de almacenamiento y los ensayos sintéticos se realizaron en iteraciones anteriores; no se repitieron llamadas tras la incidencia.

SHA-256 de `Velo-0.1.0.apk`: `08d75a6bd33aaa1c9b8dd8694246478640615806517b55f79c9a25f8cec3efb5`.

## Uso

En el OPPO conectado la versión 0.1.1 ya tiene Velo seleccionada como filtro y la protección activada. Los pasos siguientes describen también la configuración de una instalación nueva.

1. Abrir Velo y configurar perfiles y reglas. Se puede usar el simulador sin activar el filtro.
2. Para usar el bloqueo, pulsar **Activar Velo como filtro** y seleccionarla en Android. Solo puede haber un filtro seleccionado; esto sustituye a Call Blacklist en ese rol.
3. Conceder contactos para distinguir desconocidos y notificaciones si se desean avisos.
4. Activar el interruptor de protección. Las preferencias iniciales incluyen privados y desconocidos, pero la protección general se entrega apagada.
5. Para volver al filtro anterior, elegir Call Blacklist en las apps predeterminadas de Android.

Las copias locales se pierden al desinstalar: exportar JSON para conservarlas. El PIN y el registro no se incluyen en esas copias. No se ha copiado la configuración privada de Call Blacklist.

## Fuentes

- [Calls Blacklist en Google Play](https://play.google.com/store/apps/details?id=com.vladlee.easyblacklist&hl=es).
- [Android: CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService).
- [Android: respuestas de filtrado](https://developer.android.com/reference/android/telecom/CallScreeningService.CallResponse.Builder).
- APK y pantallas observados en el OPPO conectado, como se identifica arriba.


## Actualización 0.4.0 · Interfaz personalizada

Reglas y editor rediseñados; familia propia de controles e iconos aplicada a horarios, actividad, ajustes y diálogos. Inicio conserva su composición. Instalada y revisada en el OPPO sin generar llamadas; configuración y 8 registros conservados. 71 pruebas JVM y 29 instrumentadas correctas. Véanse Velo-0.4.0-interfaz.md y Velo-0.4.0-validacion.json.


## Actualización 0.4.1 · Icono y permisos al abrir

Icono de Reglas corregido. Asistente inicial, comprobación al abrir/volver de Ajustes y aviso persistente de ajustes pendientes. Revisados filtro, contactos, avisos, batería, segundo plano e inactividad; ColorOS conserva revisión manual. Instalada en OPPO; retirada de permisos por inactividad desactivada y verificada; reglas y 8 registros conservados. 83 pruebas JVM y 29 instrumentadas correctas; primer arranque y denegación/reintento adicional en emulador API 34. Detalles en Velo-0.4.1-permisos.md y Velo-0.4.1-validacion.json.
