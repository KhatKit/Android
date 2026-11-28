package heizige.kk.khatkit.app.core.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** JVM guards for the shared sheet's layout contract; visual and gesture checks still need a device. */
class PrimaryBottomSheetLayoutSourceGuardTest {
    private val source = File(
        "src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt",
    ).readText().replace(Regex("""(?s)/\*.*?\*/|//[^\n]*"""), "")
    private val shell = source.substringAfter("private fun PrimarySheetShell(")
    private val libraryPatch = File("../.github/scripts/patch-mochi.py").readText()

    @Test
    fun managedBodyUsesLibrarySpacingAndScrollWithRemainingHeight() {
        val managedBody = shell.substringAfter("if (scrollable) {").substringBefore("} else if (floatingHeader) {")
        assertTrue("Use the library's concentric content and nested-scroll integration", managedBody.contains("MochiSheetContent("))
        assertTrue("The body must fit below the header", managedBody.contains("Modifier.weight(1f, fill = false)"))
        assertTrue("Keep the caller's dismiss callback", managedBody.contains("content(onDismiss)"))
        assertFalse("Library content owns its own spacing", managedBody.contains(".padding("))
    }

    @Test
    fun selfScrolledBodyIsBoundedWithoutAnOuterScrollContainer() {
        val selfScrolledBody = shell.substringAfter("} else if (floatingHeader) {").substringAfter("} else {")
        assertTrue("Use the same library geometry as managed content", selfScrolledBody.contains("MochiSheetContent("))
        assertTrue(selfScrolledBody.contains("scrollable = false"))
        assertTrue("Keep a finite remaining viewport for lazy lists", selfScrolledBody.contains("Modifier.weight(1f, fill = false)"))
        assertTrue(selfScrolledBody.contains("content(onDismiss)"))
        assertFalse("Do not add fixed spacing outside the library", selfScrolledBody.contains(".padding("))
        assertFalse("Do not measure an inner lazy list with infinite height", shell.contains(".verticalScroll("))
    }

    @Test
    fun edgeToEdgeFormsKeepTheirOwnSpacingAndBoundedScrollViewport() {
        val form = shell.substringAfter("} else if (floatingHeader) {").substringBefore("} else {")
        assertTrue(form.contains("Column(Modifier.fillMaxWidth().weight(1f))"))
        assertTrue(form.contains("content(onDismiss)"))
        assertFalse(form.contains("MochiSheetContent("))
        assertFalse(form.contains(".padding("))
        assertFalse(form.contains(".verticalScroll("))
    }

    @Test
    fun libraryNonScrollingContentSubtractsInsetsAndKeepsOriginalScrollingOverload() {
        assertTrue(libraryPatch.contains("source.count(old) != 1"))
        assertTrue(libraryPatch.contains("scrollable = true,"))
        assertTrue(libraryPatch.contains(".then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)"))
        assertTrue(libraryPatch.contains("val horizontal = sheetStableHorizontalInset(metrics, corner)"))
        assertTrue(libraryPatch.contains("val vertical = sheetStableTopInset(metrics, corner)"))
        assertTrue(libraryPatch.contains("check(c.hasBoundedHeight)"))
        assertTrue(libraryPatch.contains("minHeight = (c.minHeight - vertical * 2).coerceAtLeast(0)"))
        assertTrue(libraryPatch.contains("maxHeight = (c.maxHeight - vertical * 2).coerceAtLeast(0)"))
        assertTrue(libraryPatch.contains("(p.height + vertical * 2).coerceIn(c.minHeight, c.maxHeight)"))
    }

    @Test
    fun keyboardAndNavigationSafeAreaHaveOneOwner() {
        listOf("imePadding(", "navigationBarsPadding(", "windowInsetsPadding(", "safeDrawingPadding(").forEach {
            assertFalse("MochiSheet already owns the IME and system insets: $it", shell.contains(it))
        }
        assertFalse("The shell must use the full library viewport", shell.contains("fillMaxHeight("))
        assertFalse(shell.contains("heightIn("))
        assertTrue("The library must consume its handled bottom IME without adding padding",
            libraryPatch.contains(".consumeWindowInsets(WindowInsets.ime.only(WindowInsetsSides.Bottom))"))
    }

    @Test
    fun headerIsMeasuredBeforeEitherBodyWithoutFrameDelayedSizeFeedback() {
        val header = shell.indexOf("MochiSheetHeader(")
        val body = shell.indexOf("if (scrollable) {")
        assertTrue(header >= 0 && header < body)
        assertFalse("Avoid first-frame overlap and layout feedback", shell.contains("onSizeChanged"))
        assertFalse(shell.contains("headerHeight"))
        assertFalse("Floating forms must honor the same scrollable flag", shell.contains("return@MochiSheet"))
    }

    @Test
    fun bothEntryPointsKeepStateNavigationAndDismissContracts() {
        assertEquals(2, Regex("""fun PrimaryBottomSheet\s*\(""").findAll(source).count())
        assertEquals(2, Regex("""scrollable: Boolean = true""").findAll(source).count())
        assertEquals(2, Regex("""floatingHeader: Boolean = false""").findAll(source).count())
        assertTrue(source.contains("if (visible) sheetState.show() else sheetState.hide()"))
        assertTrue(source.contains("visible = null"))
        assertTrue(shell.contains("navigator = navigator"))
        assertTrue(shell.contains("if (dismissible && currentVisible != false) currentOnDismiss()"))
    }
}
