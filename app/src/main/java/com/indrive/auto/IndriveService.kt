package com.indrive.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class IndriveService : AccessibilityService() {

    companion object {
        const val PKG = "sinet.startup.inDriver"
        private val C_RED = Color.parseColor("#B3261E")
        private val C_GREEN = Color.parseColor("#1B8E3E")
        private val C_ORANGE = Color.parseColor("#E65100")
        private val C_GRAY = Color.parseColor("#616161")
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var logger: Logger
    private var pending = false
    private var lastSig: String? = null
    private var retrySig: String? = null
    private var retry = 0
    private var overlay: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        logger = Logger(this)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != PKG) return
        schedule()
    }

    private fun schedule() {
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            try {
                procesar()
            } catch (_: Exception) {
            }
        }, 500)
    }

    private fun procesar() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != PKG) return
        val lines = ArrayList<String>()
        collect(root, lines)

        if (lines.none { it.contains("Solicitud de viaje", ignoreCase = true) }) {
            lastSig = null
            retrySig = null
            retry = 0
            return
        }

        val an = Parser.analyze(lines, prefs.config())
        val sig = "${an.kmA10}|${an.kmB10}|${an.precioOf}|${an.action == Action.ERROR_DIR}"
        if (sig == lastSig) return

        // Lecturas dudosas: reintenta unas veces por si la pantalla aun carga
        if (an.action == Action.SIN_LECTURA || an.action == Action.MANUAL) {
            if (sig != retrySig) {
                retrySig = sig
                retry = 0
            }
            retry++
            if (retry < 3) {
                schedule()
                return
            }
        }
        lastSig = sig
        retrySig = null
        retry = 0
        ejecutar(an, lines)
    }

    private fun ejecutar(an: Analysis, lines: List<String>) {
        val auto = prefs.bool("acciones", true)
        var hecho = false
        var msg = ""
        var color = C_GRAY
        val viaje = if (an.kmB10 >= 0) km10(an.kmB10) else "?"
        val recogida = if (an.kmA10 >= 0) km10(an.kmA10) else "?"
        val cfg = prefs.config()

        when (an.action) {
            Action.ERROR_DIR -> {
                color = C_RED
                msg = "🚫 ERROR DE DIRECCION\nA y B tienen la misma direccion.\nSin accion automatica: decide tu."
            }
            Action.OMITIR -> {
                if (auto && prefs.bool("autoOmitir", true)) {
                    hecho = clickText { it.equals("Omitir", true) } || clickText { it.equals("Cerrar", true) }
                }
                color = C_GRAY
                msg = "⏭️ ${if (hecho) "OMITIDO" else "Conviene omitir"}: recogida $recogida km (max ${km10(cfg.maxKmA10)})"
            }
            Action.ACEPTAR -> {
                if (auto && prefs.bool("autoAceptar", true)) {
                    hecho = clickText { Parser.ACCEPT_RE.containsMatchIn(it) }
                }
                color = C_GREEN
                msg = "✅ ${if (hecho) "ACEPTADO AUTOMATICO" else "Conviene aceptar"}\n" +
                    "Viaje $viaje km | Recogida $recogida km\n" +
                    "Sugerido: ${an.sugerido} | Ofrecen: ${an.precioOf} (${an.dif}) | Tolerancia: ${cfg.tolerancia}"
            }
            Action.CONTRAOFERTA -> {
                val precio = an.clickPrice
                if (auto && prefs.bool("autoContra", true) && precio != null) {
                    hecho = clickText { Parser.chipPrice(it) == precio }
                }
                color = C_ORANGE
                msg = "⚠️ ${if (hecho) "CONTRAOFERTA enviada" else "Conviene contraofertar"}: ${an.clickPrice}\n" +
                    "Sugerido: ${an.sugerido} | Ofrecian: ${an.precioOf} (${an.dif})\n" +
                    "Viaje $viaje km | Recogida $recogida km"
            }
            Action.SIN_ACCION_PERDIDA -> {
                color = C_GRAY
                msg = "⏭️ SIN ACCION: la perdida seria muy grande\n" +
                    "Mejor boton: ${an.clickPrice} | Sugerido: ${an.sugerido} | Ofrecian: ${an.precioOf}\n" +
                    "${an.detail}"
            }
            Action.MANUAL -> {
                color = C_ORANGE
                msg = "⚠️ Sin botones de precio validos\n" +
                    "Contraoferta manual: ${an.sugerido} | Ofrecian: ${an.precioOf} (${an.dif})\n" +
                    "Lei: ${an.chips.joinToString(" ")}"
            }
            Action.SIN_LECTURA -> {
                color = C_RED
                msg = "❓ No pude leer la solicitud\n${an.detail}"
            }
        }
        logger.add(an, hecho, lines)
        showOverlay(msg, color)
    }

    // ---------- lectura de nodos ----------

    private fun nodeText(n: AccessibilityNodeInfo): String? {
        val t = n.text?.toString()?.trim()
        if (!t.isNullOrEmpty()) return t
        if (n.childCount == 0) {
            val d = n.contentDescription?.toString()?.trim()
            if (!d.isNullOrEmpty()) return d
        }
        return null
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int = 0) {
        if (n == null || depth > 40) return
        nodeText(n)?.let { out.add(it) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun findNode(n: AccessibilityNodeInfo?, pred: (String) -> Boolean, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        val t = nodeText(n)
        if (t != null && pred(t)) return n
        for (i in 0 until n.childCount) {
            val r = findNode(n.getChild(i), pred, depth + 1)
            if (r != null) return r
        }
        return null
    }

    // ---------- clics ----------

    private fun clickText(pred: (String) -> Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = findNode(root, pred) ?: return false
        return clickNode(n)
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            depth++
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) return tap(r.centerX().toFloat(), r.centerY().toFloat())
        return false
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ---------- aviso en pantalla ----------

    private fun showOverlay(text: String, color: Int) {
        handler.post {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                overlay?.let { runCatching { wm.removeView(it) } }
                val tv = TextView(this).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    setBackgroundColor(color)
                    setPadding(32, 24, 32, 24)
                    textSize = 14f
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                    y = 220
                }
                wm.addView(tv, lp)
                overlay = tv
                handler.postDelayed({
                    runCatching { wm.removeView(tv) }
                    if (overlay === tv) overlay = null
                }, 4000)
            } catch (_: Exception) {
            }
        }
    }
}
