package com.inventario.propiedades

import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.data.local.TemplateEntity
import com.inventario.propiedades.ui.templates.TemplatesViewModel
import kotlinx.coroutines.runBlocking

class TemplatesViewModelTest : DatabaseTestCase() {
    fun testOnlyCustomTemplatesCanBeConfirmedForDeletion() = runBlocking {
        val dao = database.templateDao()
        val baseId = dao.insertTemplate(TemplateEntity(name = "Estudio", isBuiltIn = true))
        val ownId = dao.insertTemplate(TemplateEntity(name = "Propia"))
        val model = createViewModel { TemplatesViewModel(TemplateRepository(database)) }
        model.uiState.await { !it.isLoading && it.templates.size == 2 }
        onMain {
            model.requestDelete(TemplateEntity(baseId, "Estudio", true))
            assertNull(model.pendingDelete)
            model.requestDelete(TemplateEntity(ownId, "Propia"))
            model.dismissDelete()
            assertNull(model.pendingDelete)
        }
        assertNotNull(dao.getById(ownId))
        onMain {
            model.requestDelete(TemplateEntity(ownId, "Propia"))
            model.deleteConfirmed()
            model.deleteConfirmed()
            model.dismissDelete()
            assertTrue(model.isDeleting)
        }
        awaitMain { !model.isDeleting && model.pendingDelete == null }
        val state = model.uiState.await { it.templates.size == 1 }
        assertEquals(baseId, state.templates.single().id)
        assertNull(dao.getById(ownId))
        assertNotNull(dao.getById(baseId))
    }

    fun testFailedDeletionKeepsConfirmationAndAllowsRetry() = runBlocking {
        val dao = database.templateDao()
        val id = dao.insertTemplate(TemplateEntity(name = "Propia"))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_template_delete BEFORE DELETE ON templates
            BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
        val model = createViewModel { TemplatesViewModel(TemplateRepository(database)) }
        onMain {
            model.requestDelete(TemplateEntity(id, "Propia"))
            model.deleteConfirmed()
        }
        awaitMain { !model.isDeleting && model.deleteError != null }
        assertNotNull(dao.getById(id))
        onMain { assertEquals(id, model.pendingDelete!!.id) }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_template_delete")
        onMain { model.deleteConfirmed() }
        awaitMain { !model.isDeleting && model.pendingDelete == null }
        assertNull(dao.getById(id))
        onMain { assertNull(model.deleteError) }
    }

    fun testDeleteWithoutConfirmationDoesNothing() = runBlocking {
        val id = database.templateDao().insertTemplate(TemplateEntity(name = "Propia"))
        val model = createViewModel { TemplatesViewModel(TemplateRepository(database)) }
        onMain {
            model.deleteConfirmed()
            assertFalse(model.isDeleting)
        }
        assertNotNull(database.templateDao().getById(id))
    }

}
