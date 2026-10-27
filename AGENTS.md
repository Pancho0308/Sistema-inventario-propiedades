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
5. Plantillas y clonar: hecha.
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
- Desde la Fase 5, los archivos huérfanos de `filesDir/photos/` se limpian automáticamente al iniciar un nuevo proceso, después de abrir/migrar Room y antes de habilitar la navegación.

## Plantillas, clonar y limpieza inicial (Fase 5)

- Room v3 añade `templates`, `template_rooms` y `template_items`, con IDs autogenerados, claves foráneas, índices y cascadas dentro de cada plantilla. Esquema exportado en `app/schemas/`.
- `MIGRATION_2_3` solo añade esas tablas y precarga las tres bases. Se conserva `MIGRATION_1_2` para actualizar 1 → 2 → 3 sin migración destructiva ni pérdida de datos.
- Bases: Estudio, 1D1B y 2D1B, con habitaciones y artículos típicos en español. Se insertan exactamente una vez en instalación nueva o migración 2 → 3; nunca al reabrir la base.
- `PropertyRepository.clone` copia solo nombres y estructura. `create` permite elegir una plantilla o empezar en blanco. Las propiedades nuevas quedan pendientes y los artículos con `status = NULL`, `note = ""` y `photoPath = NULL`.
- `TemplateRepository.saveFromProperty` guarda una instantánea independiente con nombre propio, sin estados, notas ni fotos. Cada copia completa, incluida la lectura del origen, se ejecuta en una sola transacción.
- Habitaciones y artículos se leen ordenados por `id` y se insertan en secuencia para conservar el orden original. Borrar una plantilla no afecta a las propiedades creadas desde ella.
- El DAO impide borrar plantillas base (`isBuiltIn`); la interfaz solo permite eliminar las propias, con confirmación.
- `InventoryApplication` ejecuta la limpieza de fotos una vez por proceso en `Dispatchers.IO`. La navegación espera al resultado para no competir con capturas. Si falla la consulta a Room, no se borra ningún archivo; ante un error de arranque se ofrece Reintentar.
- `PhotoStorage.cleanupUnreferenced` conserva las rutas referenciadas y elimina archivos propios huérfanos y temporales; nunca borra directorios ni archivos fuera de `filesDir/photos/`.
- Ubicación de controles: Propiedades → ⋮ a la derecha de una tarjeta → Clonar / Guardar como plantilla; + abajo a la derecha → Estructura → elegir plantilla o Empezar en blanco; Plantillas arriba a la derecha → Eliminar junto a una plantilla propia.
- Verificación al cerrar: `assembleDebug` correcto, 81 tests instrumentados en el celular y tests locales `NO-SOURCE`. Incluye tests separados de bases únicas en instalación nueva/migración 2 → 3, preservación de estados/notas/rutas y archivo de foto en 1 → 2 → 3, orden por id, rollback completo, limpieza segura y ViewModels. Usuario confirmó los flujos manuales. APK actualizado con `adb install -r`, sin borrar datos.
- Sin dependencias nuevas. No se añadieron edición de plantillas ni funciones de la Fase 6.

## Reglas de trabajo

- Trabajar solo en la fase indicada por el usuario; no avanzar a otra por iniciativa propia.
- Mostrar el plan y esperar confirmación antes de escribir código.
- Al pedir pruebas manuales, indicar siempre la pantalla, la ubicación de cada botón y el recorrido exacto para encontrarlo.
- No borrar archivos sin avisar.
- No agregar dependencias fuera del stack.
- Al terminar una implementación: compilar con `./gradlew assembleDebug`, correr tests, resumir los resultados y hacer commit.
- Para tests locales usar `./gradlew testDebugUnitTest`; para los tests instrumentados existentes, `./gradlew connectedDebugAndroidTest` con dispositivo o emulador disponible. Informar cualquier verificación que no se pueda ejecutar.
- Comentarios del código en español; nombres de identificadores en inglés.
- La Fase 5 y la limpieza inicial de fotos fueron autorizadas. No avanzar a la Fase 6 sin indicación del usuario.
