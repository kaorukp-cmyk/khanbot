package com.indrive.auto

import kotlin.math.abs

enum class Action { ERROR_DIR, OMITIR, ACEPTAR, CONTRAOFERTA, SIN_ACCION_PERDIDA, MANUAL, SIN_LECTURA }

data class Config(
    val precioKm: Int = 750,
    val redondeo: Int = 50,
    val minimo: Int = 2700,
    val tolerancia: Int = 500,
    val toleranciaBoton: Int = 800,
    val maxKmA10: Int = 25 // decimas de km: 25 = 2.5 km
)

data class Analysis(
    val action: Action,
    val kmA10: Int = -1,
    val kmB10: Int = -1,
    val precioOf: Int = 0,
    val sugerido: Int = 0,
    val dif: Int = 0,
    val chips: List<Int> = emptyList(),
    val clickPrice: Int? = null,
    val detail: String = ""
)

fun km10(v: Int): String = "${v / 10}.${v % 10}"

object Parser {
    private val A_RE = Regex("""[~≈]\s*(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
    private val KM_RE = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    val ACCEPT_RE = Regex("""(?:Aceptar por|Aceitar por|Accept for)\s*[^\d]{0,5}(\d[\d.,]*)""", RegexOption.IGNORE_CASE)
    val CHIP_RE = Regex("""^(?:[A-Z]{2,4}|\$)\s?(\d[\d.,]*)$""")
    private val IGNORAR = listOf("solicitud de viaje", "ofrece tu tarifa", "aceptar", "omitir")

    /** "1,05" -> 10 (decimas, trunca). */
    fun tenths(raw: String): Int {
        val parts = raw.replace(',', '.').split('.')
        val whole = parts[0].filter { it.isDigit() }.toIntOrNull() ?: 0
        val dec = parts.getOrNull(1)?.firstOrNull()?.takeIf { it.isDigit() }?.digitToInt() ?: 0
        return whole * 10 + dec
    }

    fun digits(s: String): Int? = s.filter { it.isDigit() }.toIntOrNull()

    fun chipPrice(line: String): Int? =
        CHIP_RE.find(line.trim())?.groupValues?.get(1)?.let { digits(it) }

    /** Misma direccion en A y B: dos lineas consecutivas iguales (>= 8 chars). */
    fun hasDuplicateAddress(lines: List<String>): Boolean {
        val c = lines.map { it.trim() }.filter { it.isNotEmpty() && it != "A" && it != "B" }
        for (i in 0 until c.size - 1) {
            val t = c[i]
            if (t.length < 8 || !t.equals(c[i + 1], ignoreCase = true)) continue
            val low = t.lowercase()
            if (IGNORAR.any { low.contains(it) }) continue
            if (CHIP_RE.matches(t) || KM_RE.containsMatchIn(t)) continue
            return true
        }
        return false
    }

    fun analyze(lines: List<String>, cfg: Config): Analysis {
        val joined = lines.joinToString("\n")

        // A = km de recogida (el que lleva ~)
        val aRaw = A_RE.find(joined)?.groupValues?.get(1)
        val kmA10 = aRaw?.let { tenths(it) } ?: -1

        // B = ultimo "X km" tras quitar los de A
        var kmB10 = -1
        if (aRaw != null) {
            val quitarA = Regex("[~≈]?\\s*(?<![\\d.,])(?=\\d)" + Regex.escape(aRaw) + "\\s*km\\b", RegexOption.IGNORE_CASE)
            val rest = joined.replace(quitarA, "")
            val bRaw = KM_RE.findAll(rest).lastOrNull()?.groupValues?.get(1) ?: aRaw
            kmB10 = tenths(bRaw)
        }

        // Precio ofrecido = "Aceptar por CLP2,500"
        val precio = ACCEPT_RE.find(joined)?.groupValues?.get(1)?.let { digits(it) } ?: 0

        if (hasDuplicateAddress(lines)) {
            return Analysis(Action.ERROR_DIR, kmA10, kmB10, precio, detail = "A y B tienen la misma direccion")
        }
        if (aRaw == null) {
            return Analysis(Action.SIN_LECTURA, kmA10, kmB10, precio, detail = "No encontre los km de recogida (~X km)")
        }
        if (kmA10 > cfg.maxKmA10) {
            return Analysis(Action.OMITIR, kmA10, kmB10, precio,
                detail = "Recogida ${km10(kmA10)} km > ${km10(cfg.maxKmA10)} km")
        }
        if (precio <= 0) {
            return Analysis(Action.SIN_LECTURA, kmA10, kmB10, precio, detail = "No encontre el precio (Aceptar por ...)")
        }

        // Precio sugerido
        val red = if (cfg.redondeo < 1) 1 else cfg.redondeo
        val total = kmB10.toLong() * cfg.precioKm / 10
        var sug = (((total + red / 2) / red) * red).toInt()
        if (sug < cfg.minimo) sug = cfg.minimo

        val dif = precio - sug
        val difT = if (precio < cfg.minimo) -1_000_000 else dif + cfg.tolerancia
        if (difT >= 0) {
            return Analysis(Action.ACEPTAR, kmA10, kmB10, precio, sug, dif,
                detail = "Ofrecen $precio, sugerido $sug, tolerancia ${cfg.tolerancia}")
        }

        // Contraoferta: botones de precio
        val chips = lines.mapNotNull { chipPrice(it) }.distinct()
        val validos = chips.filter { it > precio && it >= cfg.minimo }
        if (validos.isEmpty()) {
            return Analysis(Action.MANUAL, kmA10, kmB10, precio, sug, dif, chips,
                detail = "Sin botones de precio validos")
        }
        val best = validos.minWith(compareBy<Int>({ abs(it - sug) }, { -it })) // empate -> el mas caro
        val loss = sug - best
        return if (loss - cfg.toleranciaBoton > 0) {
            Analysis(Action.SIN_ACCION_PERDIDA, kmA10, kmB10, precio, sug, dif, chips, best,
                "Perdida $loss > maximo ${cfg.toleranciaBoton}")
        } else {
            Analysis(Action.CONTRAOFERTA, kmA10, kmB10, precio, sug, dif, chips, best,
                "Mejor boton $best, perdida $loss")
        }
    }
}
