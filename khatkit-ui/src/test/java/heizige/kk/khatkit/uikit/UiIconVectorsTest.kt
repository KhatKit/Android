package heizige.kk.khatkit.uikit

import heizige.kk.khatkit.ui.UiIcons
import org.junit.Assert.assertEquals
import org.junit.Test

class UiIconVectorsTest {
    @Test fun everyValidatedIconNameHasAVector() {
        assertEquals(UiIcons.names, UiIconVectors.names)
    }
}
