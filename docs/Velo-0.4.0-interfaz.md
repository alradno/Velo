# Velo 0.4.0 · Interfaz personalizada

Instalada en el OPPO. Inicio mantiene su composición. Reglas añade dos áreas con contadores, búsqueda, carpetas, acceso a contactos y tarjetas con estado. El editor ahora es una pantalla amplia con tres grupos, cuatro tipos de coincidencia con ejemplos, vista previa y guardado fijo sobre el teclado.

Botones, campos de texto, interruptores, chips, deslizadores, diálogos, calendario, hora, indicadores e iconos usan componentes propios de Velo en toda la app. Los permisos, contactos y archivos se abren con los selectores de Android. Se conserva la paleta y los modos oscuro, claro y sistema. Las animaciones de controles respetan las preferencias de Velo y del sistema.

Verificación: 71 pruebas JVM y 29 instrumentadas correctas; lint con 0 errores y 26 avisos. Revisión física de reglas, editor y teclado, validación de perfiles, cancelación, horarios/hora/calendario, ajustes, historial y estadísticas. El historial conserva sus 8 registros y los hashes de configuración y eventos coinciden antes y después. Protección activa y rol de filtro confirmado. No se generaron llamadas ni se guardaron reglas de prueba.

El motor, SQLite y paginación no cambian respecto a 0.3.0. Los benchmarks anteriores siguen documentados por versión; esta entrega no repite la prueba de un millón de registros. La inspección visual se hizo en el tema oscuro y tamaño de texto actual, sin afirmar una auditoría exhaustiva de accesibilidad o de otros dispositivos.

APK SHA-256: `ca997529aa2c9e757446096e6fc745ff967137d4e30b1998823a1e690ee4ffac`
