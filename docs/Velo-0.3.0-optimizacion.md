# Velo 0.3.0 · optimización verificada

Instalada en el OPPO CPH2913 con Android 17. El historial sigue en Sin límite y la protección activada. Los ocho registros reales y todos los ajustes conservan exactamente los mismos contenidos antes y después de la actualización, comprobados mediante SHA-256. No se generaron llamadas telefónicas para estas pruebas.

## Resultados medidos en el OPPO

| Operación con 100.000 registros | 0.2.0 anterior | 0.3.0 final |
|---|---:|---:|
| Primer cálculo de estadísticas completas | 418 ms | 19 ms |
| Repetir estadísticas sin cambios | 407 ms | 0,05 ms |
| Buscar un número concreto | 72 ms | 1,15 ms |
| Búsqueda sin coincidencias | 70 ms | 0,18 ms |
| Página situada lejos del inicio | 3,74 ms | 0,35 ms |

| Operación con 1.000.000 de registros, release final | Tiempo |
|---|---:|
| Primer cálculo de estadísticas completas | 15,94 ms |
| Repetir estadísticas sin cambios | 0,05 ms |
| Primera página de historial | 2,38 ms |
| Página situada lejos del inicio | 1,38 ms |
| Buscar un número concreto | 12,27 ms |
| Búsqueda sin coincidencias | 0,65 ms |

Con **5.000 reglas**, el primer cálculo del motor tardó **14,70 ms**, incluida la preparación de índices. Los siguientes **1.000 cálculos**, con lectura de configuración incluida, tardaron **42,02 ms en total**. Son decisiones del motor local; no incluyen consultas a contactos, Telecom ni la red del operador.

El APK pasa de **40,09 MB a 4,41 MB**, aproximadamente un **89 % menos**. La release final conserva las API necesarias para probarla mediante instrumentación y elimina código y recursos de dependencias que no se utilizan. Arranque en frío observado del APK final: 247 ms; no se usa ese dato aislado para afirmar una mejora de arranque respecto a versiones anteriores.

Los tiempos son muestras puntuales de operaciones, no latencias de pantalla completas ni un percentil estadístico. Los datos de carga cubren 730 días y números generados a partir de 1.000 valores posibles, con un 20 % de llamadas privadas, un 20 % permitidas y un 80 % filtradas. Cada escenario comprueba sus cantidades y resultados; no basta con medir que termine rápido. La carga y eliminación de los datos de ensayo quedan fuera de los tiempos de consulta. Las variaciones de CPU, caché y temperatura explican que el cálculo de un millón no resulte necesariamente mayor que el de cien mil en estas muestras.

Los resultados exactos están en `Velo-0.3.0-rendimiento.json`.

## Cambios para sostener el rendimiento

- **Memoria del historial acotada:** páginas de 100 llamadas, ventana configurada de 300 entradas y precarga de 20 mediante Paging 3. Se descartan y recargan páginas al desplazarse; no se acumula todo lo visitado. Todos los registros siguen accesibles.
- **Páginas profundas:** comparación compuesta de fecha e identificador apoyada en el índice; no se recorre desde la llamada más reciente para llegar a cada página.
- **Búsquedas:** catálogo de valores distintos, índices de números y motivos, espera de 250 ms entre cambios y cancelación de la consulta anterior. Se mantiene la búsqueda literal de fragmentos y no se convierte el texto introducido en SQL.
- **Estadísticas incrementales:** los resúmenes se actualizan en la misma transacción que los registros, incluida su eliminación o caducidad. Los límites parciales de un periodo se completan desde los eventos originales y los resúmenes se reconstruyen al cambiar la zona horaria.
- **Caché limitada:** hasta seis estadísticas, invalidadas por cambios de datos y zona horaria. Los contadores de inicio no necesitan contar toda la tabla.
- **Lecturas sin reservar el escritor en Android 17:** consultar estadísticas no bloquea el guardado de una llamada por mantener una transacción de escritura abierta.
- **Prioridad para la llamada:** sin lectura de historial cuando no se usa repetición; contactos solo cuando pueden cambiar la decisión; reglas exactas indexadas en memoria y patrones preparados por configuración. La escritura y notificación van después de responder a Telecom, en una cola independiente.
- **Repetición consistente:** una caché temporal de hasta 256 números visibles cubre las llamadas pendientes de escritura, sin agrupar los privados.
- **Menos trabajo repetido:** reutilización de configuración JSON validada y formateadores, listas de reglas memorizadas, preferencias visuales guardadas al terminar el gesto y programación de copias solo cuando cambia su activación.
- **Release reducida:** R8 y eliminación de recursos sin uso; símbolos guardados en `Velo-0.3.0-simbolos.zip` para diagnosticar futuras incidencias.

Los índices y resúmenes ocupan disco adicional a cambio de menos lecturas y CPU. El historial permanente crece en disco; la memoria de páginas y cachés está limitada. Los bytes de los directorios de ensayo incluidos en el JSON también incluyen los archivos temporales WAL de la carga masiva, y no representan solo el tamaño final de datos tras un checkpoint.

## Validación del APK final

- **71 pruebas unitarias correctas**, incluidas prioridades, equivalencia del índice de reglas, horarios, formatos y caché de intentos recientes.
- **29 pruebas funcionales correctas en el OPPO**, ejecutadas contra la release optimizada: migraciones, conservación, exportación, periodos exactos, caducidad, caché, paginación en ambos sentidos, cancelación, concurrencia y contratos de respuesta Android.
- **Tres escenarios de rendimiento correctos:** 100.000 registros, 1.000.000 de registros y 5.000 reglas, en almacenamiento aislado que se retira al terminar.
- Interfaz de historial, búsqueda sin coincidencias y estadísticas revisada en el móvil. El panel real continúa mostrando 6 llamadas filtradas de 8 registros, 75 %, incluidas 2 privadas.
- Filtro Velo y Google Teléfono conservados; paquetes temporales de comprobación retirados.
- Compilación release y Android Lint sin errores.

La prueba de carga comprueba el almacenamiento y los cálculos con grandes cantidades; no certifica todos los modelos Android, distribuciones de llamadas o condiciones de batería. Las pruebas reales por operador anteriores siguen documentadas como pruebas de la 0.1.1; no se han atribuido nuevas llamadas reales a esta actualización.

## Referencias técnicas

- [Paging de Android: caché, paginación y gestión de carga](https://developer.android.com/topic/libraries/architecture/paging/v3-overview).
- [Transacciones SQLite de Android](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase).
- [Optimización de release con R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
