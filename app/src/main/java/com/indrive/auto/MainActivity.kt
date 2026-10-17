package com.indrive.auto

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var logger: Logger
    private val edits = LinkedHashMap<String, EditText>()
    private lateinit var maxKm: EditText
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        logger = Logger(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "InDrive Auto"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)
        root.addView(btn("Abrir ajustes de accesibilidad") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(section("Reglas"))
        addField(root, "precioKm", "Precio por km (CLP)", 750)
        addField(root, "redondeo", "Redondeo del sugerido (CLP)", 50)
        addField(root, "minimo", "Precio minimo (CLP)", 2700)
        addField(root, "tolerancia", "Tolerancia para aceptar (CLP)", 500)
        addField(root, "tolBoton", "Perdida maxima en contraoferta (CLP)", 800)
        maxKm = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(km10(prefs.int("maxKmA10", 25)))
        }
        root.addView(lbl("Recogida maxima A (km)"))
        root.addView(maxKm)

        root.addView(section("Acciones"))
        addSwitch(root, "acciones", "Acciones automaticas (apagado = solo avisar y registrar)")
        addSwitch(root, "autoOmitir", "Omitir si la recogida supera el maximo")
        addSwitch(root, "autoAceptar", "Aceptar si el precio conviene")
        addSwitch(root, "autoContra", "Enviar contraoferta")
        root.addView(btn("Guardar reglas") { guardar() })

        root.addView(section("Registro (ultimas 15)"))
        logView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        root.addView(logView)
        root.addView(btn("Actualizar") { refresh() })
        root.addView(btn("Compartir CSV") { share(logger.csv, "text/csv") })
        root.addView(btn("Compartir ultimo volcado de pantalla") { share(logger.dump, "text/plain") })
        root.addView(btn("Compartir ultimo problema de lectura") { share(logger.problema, "text/plain") })
        root.addView(btn("Borrar registro") {
            logger.csv.delete()
            refresh()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { f() }
    }

    private fun lbl(t: String) = TextView(this).apply {
        text = t
        setPadding(0, dp(10), 0, 0)
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun addField(root: LinearLayout, key: String, label: String, def: Int) {
        root.addView(lbl(label))
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.int(key, def).toString())
        }
        edits[key] = e
        root.addView(e)
    }

    private fun addSwitch(root: LinearLayout, key: String, label: String) {
        root.addView(Switch(this).apply {
            text = label
            isChecked = prefs.bool(key, true)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, v -> prefs.putBool(key, v) }
        })
    }

    private fun guardar() {
        for ((k, e) in edits) e.text.toString().toIntOrNull()?.let { prefs.putInt(k, it) }
        prefs.putInt("maxKmA10", Parser.tenths(maxKm.text.toString()))
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName, ignoreCase = true) == true
        status.text = if (enabled) "✅ Servicio activo" else
            "⚠️ Servicio desactivado: actívalo en Accesibilidad > InDrive Auto"
        logView.text = if (logger.csv.exists()) {
            logger.csv.readLines().takeLast(15).joinToString("\n")
        } else "Sin registros todavia"
    }

    private fun share(f: File, mime: String) {
        if (!f.exists()) {
            Toast.makeText(this, "Aun no hay datos", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Compartir"))
    }
}
