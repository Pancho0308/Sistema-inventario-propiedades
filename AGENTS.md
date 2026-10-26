# Contexto del proyecto

Leer este archivo antes de planificar o modificar el proyecto.

## Proyecto

App Android de inventario de departamentos para un corredor de propiedades.
MVP 100% local, sin nube ni cuentas.
Objetivo: registrar y revisar artículos con el mínimo de toques.

## Stack (no cambiar)

Kotlin, Jetpack Compose, Material 3, Room, MVVM, Navigation Compose y CameraX.

## Modelo

Propiedad -> Habitación -> Artículo.
Estados del artículo: NUEVO, BUENO, DESGASTADO, DAÑADO, FALTANTE, o sin revisar.

## Fases

1. Base y Room: hecha.
2. Propiedades y navegación: hecha.
3. Habitaciones y checklist: hecha.
3b. Checklist compacto con fila de chips y "Marcar todo como Bueno": pendiente.
4. Fotos con CameraX: pendiente.
5. Plantillas y clonar: pendiente.
6. PDF con fotos y firma: pendiente.
7. Comparación entrada vs. salida: pendiente.

### Punto de partida verificado

- Último commit al crear este archivo: `e460eb8` — `feat: agregar habitaciones y checklist (fase 3)`.
- El árbol de trabajo estaba limpio antes de crear este archivo.
- La Fase 3b no está implementada: el checklist usa tarjetas con botones en dos columnas y no tiene la acción "Marcar todo como Bueno".
- Código principal: `app/src/main/java/com/inventario/propiedades/`, con `data/local`, repositorios en `data` y pantallas/ViewModels en `ui/properties`, `ui/rooms`, `ui/checklist` y `ui/navigation`.
- Pruebas instrumentadas: `app/src/androidTest/`. Esquemas Room: `app/schemas/`.
- Este punto de partida es histórico: comprobar `git status`, el historial y el código antes de asumir el estado en una sesión futura.

## Reglas de trabajo

- Trabajar solo en la fase indicada por el usuario; no avanzar a otra por iniciativa propia.
- Mostrar el plan y esperar confirmación antes de escribir código.
- No borrar archivos sin avisar.
- No agregar dependencias fuera del stack.
- Al terminar una implementación: compilar con `./gradlew assembleDebug`, correr tests, resumir los resultados y hacer commit.
- Para tests locales usar `./gradlew testDebugUnitTest`; para los tests instrumentados existentes, `./gradlew connectedDebugAndroidTest` con dispositivo o emulador disponible. Informar cualquier verificación que no se pueda ejecutar.
- Comentarios del código en español; nombres de identificadores en inglés.
- En esta sesión solo se autorizó reconocer el estado y crear este archivo: esperar instrucciones antes de implementar la Fase 3b.
