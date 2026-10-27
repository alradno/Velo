package com.alberto.velo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Velo's own 24-unit, rounded monoline icon family. Vectors are built once. */
object VeloIcons {
    private fun glyph(name: String, path: String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(addPathNodes(path), stroke=SolidColor(Color.Black), strokeLineWidth=1.7f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    val Shield = glyph("Calma","M12 2.5 L20 6 V12 C20 17 16 20 12 22 C8 20 4 17 4 12 V6 Z M8.5 12 L11 14.5 L16 9")
    val VerifiedUser = Shield
    val Tune = glyph("Reglas","M4 6 H7 M11 6 H20 M4 12 H13 M17 12 H20 M4 18 H7 M11 18 H20 M11 6 A2 2 0 1 1 7 6 A2 2 0 1 1 11 6 M17 12 A2 2 0 1 1 13 12 A2 2 0 1 1 17 12 M11 18 A2 2 0 1 1 7 18 A2 2 0 1 1 11 18")
    val Schedule = glyph("Ritmo","M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M12 6 V12 L16 14")
    val History = glyph("Actividad","M4 5 V10 H9 M4 10 C5 3 14 1 19 6 C24 12 19 21 12 21 C8 21 5 19 4 17 M12 7 V12 L15 14")
    val Settings = glyph("Preferencias","M5 5 H19 V19 H5 Z M8 9 H16 M8 15 H16 M10 7 V11 M14 13 V17")
    val Lock = glyph("Privacidad","M7 10 V7 A5 5 0 0 1 17 7 V10 M5 10 H19 V21 H5 Z M12 14 V17")
    val Block = glyph("Bloquear","M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M6 6 L18 18")
    val FavoriteBorder = glyph("Permitir","M12 21 L3.5 12 C-1 5 7 1 12 7 C17 1 25 5 20.5 12 Z")
    val FolderOpen = glyph("Carpeta","M3 8 V4 H10 L13 7 H21 V20 H3 Z M3 11 H21")
    val Search = glyph("Buscar","M17 10 A7 7 0 1 1 3 10 A7 7 0 1 1 17 10 M15 15 L21 21")
    val Add = glyph("Crear","M12 4 V20 M4 12 H20")
    val Contacts = glyph("Contactos","M5 3 H20 V21 H5 Z M2 7 H6 M2 12 H6 M2 17 H6 M15 8 A2 2 0 1 1 11 8 A2 2 0 1 1 15 8 M9 17 C9 12 17 12 17 17")
    val Edit = glyph("Editar","M4 16 L16 4 L20 8 L8 20 L3 21 Z M13 7 L17 11")
    val Check = glyph("Hecho","M5 12 L10 17 L20 6")
    val Info = glyph("Nota","M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M12 11 V17 M12 7 V7.1")
    val BarChart = glyph("Estadisticas","M4 20 V13 H7 V20 M11 20 V5 H14 V20 M18 20 V9 H21 V20")
    val PersonOff = glyph("Desconocido","M14 6 A3 3 0 1 1 8 6 A3 3 0 1 1 14 6 M3 21 C3 12 15 11 18 17 M17 4 L22 9 M22 4 L17 9")
    val VisibilityOff = glyph("Privado","M3 3 L21 21 M8 6 C14 3 19 8 22 12 L19 16 M16 18 C10 23 4 17 2 12 L5 8 M10 10 L14 14")
    val NightsStay = glyph("Descanso","M18 3 C8 8 16 18 22 14 C19 24 4 23 2 13 C0 5 10 0 18 3 Z")
    val Science = glyph("Simulador","M8 3 H16 M10 3 V10 L4 20 H20 L14 10 V3 M7 15 H17")
    val ArrowForward = glyph("Continuar","M3 12 H21 M15 6 L21 12 L15 18")
    val ChevronRight = glyph("Abrir","M9 5 L16 12 L9 19")
    val Close = glyph("Cerrar","M5 5 L19 19 M19 5 L5 19")
    val Refresh = glyph("Actualizar","M20 4 V10 H14 M20 10 C19 3 8 1 4 8 C0 15 6 23 14 20 M4 20 V14 H10")
    val FileDownload = glyph("Exportar","M12 3 V15 M7 10 L12 15 L17 10 M4 16 V21 H20 V16")
    val DeleteOutline = glyph("Eliminar","M3 6 H21 M9 6 V3 H15 V6 M6 6 L7 21 H17 L18 6 M10 10 V17 M14 10 V17")
    val Inbox = glyph("Vacio","M6 3 H18 L22 14 V21 H2 V14 Z M2 14 H8 L10 17 H14 L16 14 H22")
    val Call = glyph("Llamada","M4 3 H8 L10 8 L7 11 C8 14 10 16 13 17 L16 14 L21 16 V20 C9 26 -2 10 4 3 Z")
    val VolumeOff = glyph("Silencio","M3 9 H7 L12 4 V20 L7 15 H3 Z M16 9 L22 15 M22 9 L16 15")
}
