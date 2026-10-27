package com.inventario.propiedades

import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.TemplateEntity
import com.inventario.propiedades.ui.properties.PropertyViewModel
import kotlinx.coroutines.runBlocking

class PropertyTemplatesViewModelTest : DatabaseTestCase() {
    private fun newModel() = createViewModel {
        PropertyViewModel(PropertyRepository(database, photoStorage), TemplateRepository(database))
    }

    private suspend fun source(): Long {
        val id = createProperty()
        val room = createRoom(id)
        database.itemDao().insert(ItemEntity(roomId = room, name = "Mesa", status = ItemStatus.DAÑADO,
            note = "Nota original", photoPath = "/files/mesa.jpg"))
        return id
    }

    fun testCloneCreatesOneFreshPropertyAndIgnoresTemplateSelection() = runBlocking {
        val source = source()
        val unrelated = database.templateDao().insertTemplate(TemplateEntity(name = "Otra estructura"))
        val model = newModel()
        onMain {
            model.openCloneDialog(source)
            model.selectTemplate(unrelated)
            assertNull(model.form.templateId)
            model.updateName("  Copia  ")
            model.updateAddress("  Calle 2  ")
            model.createProperty()
            model.createProperty()
            assertTrue(model.form.isSaving)
            model.dismissCreateDialog()
            model.updateName("No reemplazar")
            assertEquals("  Copia  ", model.form.name)
        }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        val copy = database.propertyDao().getAll().single { it.id != source }
        assertEquals("Copia", copy.name)
        assertEquals("Calle 2", copy.address)
        val room = database.roomDao().getByProperty(copy.id).single()
        val item = database.itemDao().getByRoom(room.id).single()
        assertEquals(ItemEntity(id = item.id, roomId = room.id, name = "Mesa"), item)
    }

    fun testCreateFromTemplateThenBlankResetsTheSelection() = runBlocking {
        val source = source()
        val template = TemplateRepository(database).saveFromProperty(source, "Propia")
        val model = newModel()
        model.uiState.await { !it.isLoading && it.templates.size == 1 }
        onMain {
            model.openCreateDialog()
            model.selectTemplate(template)
            model.updateName("Desde plantilla")
            model.updateAddress("Calle 2")
            model.createProperty()
        }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        val copy = database.propertyDao().getAll().single { it.name == "Desde plantilla" }
        assertEquals(1, database.roomDao().getByProperty(copy.id).size)
        onMain {
            model.openCreateDialog()
            assertNull(model.form.templateId)
            model.updateName("Blanca")
            model.updateAddress("Calle 3")
            model.createProperty()
        }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        val blank = database.propertyDao().getAll().single { it.name == "Blanca" }
        assertTrue(database.roomDao().getByProperty(blank.id).isEmpty())
    }

    fun testSaveTemplateValidatesNameAndPreventsDuplicateSubmissions() = runBlocking {
        val source = source()
        val model = newModel()
        onMain {
            model.openTemplateDialog(source)
            model.updateTemplateName(" ")
            model.saveTemplate()
            assertNotNull(model.templateForm.error)
            model.updateTemplateName("  Propia  ")
            model.saveTemplate()
            model.saveTemplate()
            model.dismissTemplateDialog()
            model.openCreateDialog()
            model.updateTemplateName("No reemplazar")
            assertEquals("  Propia  ", model.templateForm.name)
            assertTrue(model.templateForm.isSaving)
        }
        awaitMain { !model.templateForm.isSaving && model.templateForm.propertyId == null }
        assertEquals("Propia", database.templateDao().getAll().single().name)
        assertEquals(1, database.propertyDao().getAll().size)
    }

    fun testCloneFailurePreservesFieldsAndAllowsRetry() = runBlocking {
        val source = source()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_clone BEFORE INSERT ON items
            BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
        val model = newModel()
        onMain {
            model.openCloneDialog(source)
            model.updateName("Copia")
            model.updateAddress("Calle 2")
            model.createProperty()
        }
        awaitMain { model.form.error != null && !model.form.isSaving }
        onMain {
            assertEquals(source, model.form.sourceId)
            assertEquals("Copia", model.form.name)
            assertEquals("Calle 2", model.form.address)
        }
        assertEquals(1, database.propertyDao().getAll().size)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clone")
        onMain { model.createProperty() }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        assertEquals(2, database.propertyDao().getAll().size)
    }

    fun testSaveTemplateFailurePreservesNameAndAllowsRetry() = runBlocking {
        val source = source()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_template BEFORE INSERT ON template_items
            BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
        val model = newModel()
        onMain {
            model.openTemplateDialog(source)
            model.updateTemplateName("Propia")
            model.saveTemplate()
        }
        awaitMain { model.templateForm.error != null && !model.templateForm.isSaving }
        onMain { assertEquals("Propia", model.templateForm.name) }
        assertTrue(database.templateDao().getAll().isEmpty())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_template")
        onMain { model.saveTemplate() }
        awaitMain { !model.templateForm.isSaving && model.templateForm.propertyId == null }
        assertEquals(1, database.templateDao().getAll().size)
    }

    fun testDeletedSelectedTemplateDoesNotCreateAnEmptyProperty() = runBlocking {
        val source = source()
        val templates = TemplateRepository(database)
        val template = templates.saveFromProperty(source, "Propia")
        val model = newModel()
        onMain {
            model.openCreateDialog()
            model.selectTemplate(template)
            model.updateName("Nueva")
            model.updateAddress("Calle 2")
        }
        templates.delete(template)
        onMain { model.createProperty() }
        awaitMain { model.form.error != null && !model.form.isSaving }
        assertEquals(1, database.propertyDao().getAll().size)
        onMain {
            assertEquals("Nueva", model.form.name)
            model.selectTemplate(null)
            model.createProperty()
        }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        val created = database.propertyDao().getAll().single { it.name == "Nueva" }
        assertTrue(database.roomDao().getByProperty(created.id).isEmpty())
    }
}
