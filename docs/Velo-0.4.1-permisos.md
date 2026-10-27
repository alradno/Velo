# Velo 0.4.1 · Permisos y comprobación de apertura

Instalada en el OPPO. Corregido el icono de Reglas con tres deslizadores completos. Asistente en el primer arranque, solicitudes secuenciales de ajustes pendientes y comprobación al entrar en primer plano y regresar de Ajustes. Si falta algo, aparece un aviso y una franja que permanece tras posponerlo. Existe acceso permanente desde Ajustes → Revisar permisos y protección.

Comprueba filtro de llamadas, contactos, avisos y canal de llamadas, excepción de optimización de batería, restricción estándar de segundo plano y retirada de permisos/hibernación por inactividad. El inicio automático y otros controles propios de ColorOS se identifican como revisión manual, sin fingir una verificación automática. Una negativa no bloquea la app ni crea un bucle de solicitudes.

En el OPPO se detectó Gestionar aplicaciones no usadas activado. Se desactivó mediante el asistente y se confirmó su nuevo estado al volver. La excepción de batería y la actividad en segundo plano ya estaban permitidas. Se registró la revisión manual de los controles de ColorOS visibles. Los permisos comprobables pasan y ya no aparece aviso al reabrir. Protección activa; mismos 8 registros y mismos hashes de configuración e historial. No se generaron llamadas.

Validación: 83 pruebas JVM y 29 instrumentadas correctas; lint 0 errores y 31 avisos. Instalación limpia adicional en emulador API 34: solicitud de rol, negativa de contactos, continuación sin bucle, autorización de batería, ajustes de inactividad y reintento exitoso de contactos. Android concedió notificaciones por el rol. Se distingue esta prueba de la evidencia física Android 17 y de las pruebas previas por operador.

La API de Android no cubre todos los controles privados de cada fabricante; la revisión manual de OPPO se conserva como tal en la interfaz. El asistente no solicita permiso de Internet, accesibilidad, historial del sistema, superposición o realización de llamadas. Las preferencias del asistente son locales y no alteran el formato de reglas/copias.

Referencias técnicas oficiales consultadas:

- [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService)
- [Optimización de batería y excepción](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Restricciones de segundo plano](https://developer.android.com/topic/performance/background-optimization)
- [Estado de restricciones por inactividad](https://developer.android.com/reference/androidx/core/content/PackageManagerCompat)
- [Acceso a ajustes por inactividad](https://developer.android.com/reference/androidx/core/content/IntentCompat)

La solicitud directa de excepción de batería genera un aviso de lint sobre política de Google Play. Esta distribución es personal; una publicación futura requeriría revisar las condiciones de la tienda para ese caso de uso.

APK SHA-256: `c414af9345089b2693a6ae2935a0ac517cb2920acd5b876ba0dcc1399c3ae330`
