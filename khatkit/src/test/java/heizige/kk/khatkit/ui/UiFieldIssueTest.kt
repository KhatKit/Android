package heizige.kk.khatkit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFieldIssueTest {
    @Test fun requiredFieldHasAStableCodeAndLegacyDiagnostics() {
        val root = UiNode("TextField", mapOf("id" to "name", "required" to true), emptyList())
        assertEquals(UiFieldIssue.Code.REQUIRED, root.validationIssues(emptyMap())["name"]?.code)
        assertEquals("请填写此项", root.errors(emptyMap())["name"])
    }

    @Test fun stepIssueCarriesItsNumericArgumentWithoutAndroidDependencies() {
        val root = UiNode("NumberField", mapOf("id" to "n", "min" to 0, "step" to 2), emptyList())
        val issue = root.validationIssues(mapOf("n" to 3)).getValue("n")
        assertEquals(UiFieldIssue.Code.STEP_MISMATCH, issue.code)
        assertEquals(2.0, issue.step)
        assertEquals("数值不符合步长 2.0", issue.diagnosticMessage())
    }
}
