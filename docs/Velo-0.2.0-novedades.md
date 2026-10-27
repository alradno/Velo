# Velo 0.2.0

## Historial sin límite

- Activado en tu OPPO: sin caducidad por días ni recorte a 5.000 entradas.
- Conserva los ocho registros anteriores. La auditoría comparó sus identificadores y contenidos completos antes y después de la migración; coinciden. También coinciden todos los ajustes salvo la retención, que pasa de 90 días a Sin límite.
- Base de datos local con búsquedas y carga progresiva, para consultar historiales largos sin cargar todas las llamadas en memoria.
- Ajustes → Registro y avisos permite elegir Sin límite o un plazo de 7, 30, 90 o 365 días.
- Exportación de todo el historial en CSV, independiente de las listas de números y de las copias JSON de configuración.

Sin límite significa sin borrado automático por antigüedad o cantidad. Los datos permanecen en este móvil; se eliminan al vaciar el historial, borrar los datos de la app o desinstalarla. La actualización conserva lo que aún existe, pero no puede recuperar registros eliminados por versiones anteriores.

## Estadísticas

En **Actividad → Estadísticas**, o desde el acceso de Inicio:

- Periodos Hoy, 7 días, 30 días, 90 días, 1 año y Todo.
- Cantidad y porcentaje de llamadas filtradas sobre los registros guardados.
- Privadas filtradas, filtros por fuera de contactos, números visibles filtrados y llamadas permitidas.
- Motivos de filtrado y métodos: rechazar, ignorar o silenciar.
- Evolución con barras que se pueden tocar para ver fechas y cantidades.
- Horas y días de la semana con más actividad filtrada.
- Cinco números visibles más insistentes; tocar uno abre sus llamadas en el historial.

Los privados cuentan como llamadas, no como personas o números distintos identificables. Si no se guardan llamadas permitidas, los porcentajes describen solo el historial conservado. No se inventan cifras de tiempo ahorrado ni se importa el registro de otras apps. Todo se calcula en el dispositivo, sin permiso de Internet.

Con los registros existentes al actualizar: **8 registradas, 6 filtradas (75 %), 2 privadas filtradas**. Son las pruebas reales anteriores; no se generaron nuevas llamadas para esta actualización.

## Verificación

- 63 pruebas unitarias correctas.
- 11 pruebas de historial correctas en el OPPO físico, con datos aislados: migración idempotente, conservación de más de 5.000 entradas de hace más de dos años, paginación sin pérdidas, búsqueda, periodos, estadísticas, exportación y borrado explícito.
- Configuración e historial reales conservados mediante comparación SHA-256 de sus contenidos antes y después.
- Compilación release y Android Lint sin errores.
- Interfaz revisada en el OPPO con Android 17; protección activada y app de teléfono conservada.

La implementación usa las API locales de [SQLite de Android](https://developer.android.com/training/data-storage/sqlite).
