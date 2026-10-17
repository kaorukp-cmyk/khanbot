package com.indrive.auto

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger(ctx: Context) {
    private val dir: File = ctx.filesDir
    val csv = File(dir, "registro_indrive.csv")
    val dump = File(dir, "ultimo_volcado.txt")
    val problema = File(dir, "ultimo_problema.txt")
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    @Synchronized
    fun add(an: Analysis, ejecutado: Boolean, lines: List<String>) {
        try {
            if (!csv.exists()) {
                csv.writeText("fecha_hora,km_recogida,km_viaje,precio_ofrecido,sugerido,diferencia,decision,ejecutado,boton,detalle\n")
            }
            val row = listOf(
                df.format(Date()),
                if (an.kmA10 >= 0) km10(an.kmA10) else "",
                if (an.kmB10 >= 0) km10(an.kmB10) else "",
                an.precioOf.toString(),
                an.sugerido.toString(),
                an.dif.toString(),
                an.action.name,
                if (ejecutado) "si" else "no",
                an.clickPrice?.toString() ?: "",
                q(an.detail)
            ).joinToString(",")
            csv.appendText(row + "\n")

            val txt = lines.mapIndexed { i, s -> "$i: ${s.replace("\n", "\\n")}" }.joinToString("\n")
            dump.writeText(txt)
            if (an.action == Action.SIN_LECTURA || an.action == Action.MANUAL || an.action == Action.ERROR_DIR) {
                problema.writeText(txt)
            }
        } catch (_: Exception) {
        }
    }
}
