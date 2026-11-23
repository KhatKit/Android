package heizige.kk.khatkit.app.core.ui.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class EditStateTest {
    @Test fun confirmUsesTheLastKeystrokeOnlyOnceEvenWithoutARecomposition() {
        val saved = mutableListOf<String>()
        val state = EditState<String> { saved += it }
        state.open("")
        val displayed = state.draft!!
        state.update(displayed, "ab")
        state.update(displayed, "abc")
        state.confirm()
        state.confirm()
        assertEquals(listOf("abc"), saved)
        assertEquals("abc", displayed.value)
        assertFalse(state.isEditing)
        assertNull(state.currentState)
    }

    @Test fun dismissAndReopenDoesNotLeakLateEditsEvenForTheSameInitialValue() {
        val saved = mutableListOf<String>()
        val state = EditState<String> { saved += it }
        state.open("A")
        val leaving = state.draft!!
        state.currentState = "cancelled"
        state.dismiss()
        state.update(leaving, "late")
        state.open("A")
        assertNotSame(leaving, state.draft)
        assertEquals("A", state.currentState)
        state.open("B")
        state.update(leaving, "another late edit")
        assertEquals("cancelled", leaving.value)
        state.confirm()
        assertEquals(listOf("B"), saved)
    }

    @Test fun onSaveMayOpenTheNextEditor() {
        lateinit var state: EditState<String>
        state = EditState { state.open("next") }
        state.open("first")
        state.confirm()
        assertEquals("next", state.currentState)
    }
}
