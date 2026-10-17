package com.indrive.auto

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun int(k: String, d: Int): Int = p.getInt(k, d)
    fun bool(k: String, d: Boolean): Boolean = p.getBoolean(k, d)
    fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    fun config() = Config(
        precioKm = int("precioKm", 750),
        redondeo = int("redondeo", 50),
        minimo = int("minimo", 2700),
        tolerancia = int("tolerancia", 500),
        toleranciaBoton = int("tolBoton", 800),
        maxKmA10 = int("maxKmA10", 25)
    )
}
