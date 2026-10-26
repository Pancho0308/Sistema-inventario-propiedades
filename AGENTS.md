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
3b. Checklist compacto con fila de chips y "Marcar todo como Bueno": hecha; el marcado masivo es atómico y solo afecta artículos sin revisar de la habitación.
4. Fotos con CameraX: hecha.
5. Plantillas y clonar: pendiente.
6. PDF con fotos y firma: pendiente.
7. Comparación entrada vs. salida: pendiente.

### Punto de partida verificado

- Último commit al crear este archivo: `e460eb8` — `feat: agregar habitaciones y checklist (fase 3)`.
- El árbol de trabajo estaba limpio antes de crear este archivo.
- En ese punto de partida, la Fase 3b no estaba implementada: el checklist usaba tarjetas con botones en dos columnas y no tenía la acción "Marcar todo como Bueno".
- Código principal: `app/src/main/java/com/inventario/propiedades/`, con `data/local`, repositorios en `data` y pantallas/ViewModels en `ui/properties`, `ui/rooms`, `ui/checklist` y `ui/navigation`.
- Pruebas instrumentadas: `app/src/androidTest/`. Esquemas Room: `app/schemas/`.
- Este punto de partida es histórico: comprobar `git status`, el historial y el código antes de asumir el estado en una sesión futura.

## Fotos (Fase 4)

- CameraX `1.4.2`: solo `camera-core`, `camera-camera2`, `camera-lifecycle` y `camera-view`. Sin librería de imágenes adicional.
- Una foto por artículo, guardada en `filesDir/photos/`; el campo real de Room es `photoPath` (no `rutaFoto`). Sin migración ni cambio de esquema.
- JPEG con lado mayor máximo de 1600 px, sin ampliar imágenes pequeñas, calidad 80 y orientación EXIF aplicada. Miniaturas decodificadas a tamaño reducido con APIs Android/Compose.
- Permiso de cámara al primer uso; tras rechazo, explicación y acceso a Ajustes. Sin permiso de almacenamiento.
- El reemplazo escribe la nueva foto y confirma su ruta en Room antes de eliminar la anterior. Si falla Room, conserva la anterior y elimina el archivo nuevo.
- Para borrar entidades usar `ItemRepository.delete(roomId, itemId)`, `RoomRepository.delete(roomId)` y `PropertyRepository.delete(propertyId)`. Recogen las rutas dentro de la transacción antes de la cascada y eliminan archivos tras confirmar Room. No usar borrados DAO directamente desde la app: omiten la limpieza de archivos.
- `PhotoStorage` solo permite cargar/eliminar archivos dentro de su carpeta propia y rechaza rutas externas, incluidos enlaces simbólicos hacia fuera.
- El visor ocupa la pantalla del checklist, conserva su desplazamiento y tiene acciones fijas de reemplazar/eliminar; no usar un diálogo de altura libre que pueda ocultarlas bajo las barras del sistema.
- Verificación al cerrar la fase: `assembleDebug` correcto, 54 tests instrumentados aprobados en el celular; tests locales `NO-SOURCE`. Usuario confirmó permiso, captura, miniatura y reemplazar/eliminar. APK actualizado con `adb install -r`, sin borrar datos.
- Límite: un cierre forzado entre escritura de archivos y commit de Room puede dejar archivos huérfanos; no hay reconciliación automática tras muerte del proceso.

## Reglas de trabajo

- Trabajar solo en la fase indicada por el usuario; no avanzar a otra por iniciativa propia.
- Mostrar el plan y esperar confirmación antes de escribir código.
- No borrar archivos sin avisar.
- No agregar dependencias fuera del stack.
- Al terminar una implementación: compilar con `./gradlew assembleDebug`, correr tests, resumir los resultados y hacer commit.
- Para tests locales usar `./gradlew testDebugUnitTest`; para los tests instrumentados existentes, `./gradlew connectedDebugAndroidTest` con dispositivo o emulador disponible. Informar cualquier verificación que no se pueda ejecutar.
- Comentarios del código en español; nombres de identificadores en inglés.
- La Fase 4 y sus cambios mínimos en DAO/repositorios fueron autorizados. No avanzar a la Fase 5 sin indicación del usuario.
