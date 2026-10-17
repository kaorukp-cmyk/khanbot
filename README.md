# InDrive Auto

Servicio de accesibilidad que replica el macro "inDrive 776 Filtro A<=2.5km + B + Precio v11.6":
lee la solicitud, calcula el precio sugerido (B km x 750, redondeo 50, minimo 2700) y omite,
acepta o contraoferta segun las reglas. Registra cada solicitud en un CSV.

## Obtener el APK (sin Android Studio)
1. Crea un repositorio en GitHub y sube TODO el contenido de esta carpeta.
2. Pestana Actions > "Build APK" (corre solo al subir). Al terminar, descarga el artefacto `InDriveAuto-apk`.
3. Descomprime e instala `app-debug.apk`.

(Con Android Studio: abrir la carpeta y Build > Build APK.)

## Primer uso
1. Abre la app > "Abrir ajustes de accesibilidad" > InDrive Auto > activar.
   - Android 13+: si sale "ajustes restringidos", ve a Ajustes > Apps > InDrive Auto > menu (...) > "Permitir ajustes restringidos" y repite.
   - Xiaomi/HyperOS: Ajustes > Apps > InDrive Auto > Inicio automatico ON y Ahorro de bateria = Sin restricciones.
2. Primera prueba: apaga "Acciones automaticas". Solo avisara y registrara; revisa que A, B y precio se lean bien.
3. Si algo se lee mal: "Compartir ultimo problema de lectura" / "ultimo volcado" y enviamelo.
4. Cuando todo lea bien, enciende "Acciones automaticas".

## Reglas (editables en la app)
Precio/km 750 | redondeo 50 | minimo 2700 | tolerancia aceptar 500 | perdida max contraoferta 800 | recogida max 2.5 km
