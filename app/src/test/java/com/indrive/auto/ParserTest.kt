package com.indrive.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class ParserTest {
    // Pantalla real tomada del volcado del macro (CLP2,500 ofrecido, A=1.0, B=3.7)
    private val sample = listOf(
        "22:03 .", "J-630)", "Google", "Carolina\nY5.0", "(178)", "Justo ahora", "3 min", "1,0 km",
        "Solicitud de viaje", "~1,0 km", "CLP2,500", "Pje. Tres 430", "l )9l 100", "Libre", "1J-5141",
        "CLP2,750", "Nápoles 2756 (Curicó)", "EBoldgB", "Estadio La Granja", "Omitir",
        "Aceptar por CLP2,500", "Ofrece tu tarifa", "10 min", "CLP3,000", "5G", "3,7 km", "Aguas, Negr"
    )

    @Test fun contraoferta() {
        val a = Parser.analyze(sample, Config())
        assertEquals(Action.CONTRAOFERTA, a.action)
        assertEquals(2800, a.sugerido)
        assertEquals(2750, a.clickPrice)
    }

    @Test fun aceptar() {
        val l = sample.map { if (it == "Aceptar por CLP2,500") "Aceptar por CLP3,000" else it }
        assertEquals(Action.ACEPTAR, Parser.analyze(l, Config()).action)
    }

    @Test fun omitirPorRecogida() {
        val l = sample.map { if (it == "~1,0 km") "~3,0 km" else if (it == "1,0 km") "3,0 km" else it }
        assertEquals(Action.OMITIR, Parser.analyze(l, Config()).action)
    }

    @Test fun errorDireccion() {
        val l = sample.map { if (it == "Pje. Tres 430") "Nápoles 2756 (Curicó)" else it }
        // direcciones iguales pero no consecutivas en este fixture -> se inserta adyacente
        val m = l.toMutableList(); m.add(l.indexOf("Nápoles 2756 (Curicó)"), "Nápoles 2756 (Curicó)")
        assertEquals(Action.ERROR_DIR, Parser.analyze(m, Config()).action)
    }
}
