package heizige.kk.khatkit.app.feature.automation

import heizige.kk.khatkit.app.feature.automation.helper.VirtualDisplayProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayProtocolTest {
    @Test
    fun parsesCreatedDisplayIdAndRejectsShellFailures() {
        assertEquals(12, VirtualDisplayProtocol.parseCreatedDisplayId(success("Created virtual display with id 12")))
        assertNull(VirtualDisplayProtocol.parseCreatedDisplayId("[exit 1]\ncmd: Can't find service: display"))
        assertNull(VirtualDisplayProtocol.parseCreatedDisplayId(success("Created virtual display with id 0")))
    }

    @Test
    fun createsAnOwnedDisplayAndValidatesDestroyIds() {
        val command = VirtualDisplayProtocol.createCommand("KhatKit_a1b2c3d4")
        assertTrue(command.contains("create-virtual-display"))
        assertTrue(command.contains("--own-content-only"))
        assertTrue(command.contains("720x1280"))
        assertEquals("cmd display destroy-virtual-display 4", VirtualDisplayProtocol.destroyCommand(4))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 2] permission denied"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeDisplayNames() {
        VirtualDisplayProtocol.createCommand("display; rm -rf /")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDefaultDisplayDestroy() {
        VirtualDisplayProtocol.destroyCommand(0)
    }

    @Test
    fun requiresAnExplicitSuccessfulExitAndRejectsTransportFailures() {
        assertFalse(VirtualDisplayProtocol.isSuccessful(""))
        assertFalse(VirtualDisplayProtocol.isSuccessful("Created virtual display with id 12"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[timeout] command timed out"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit -1]\nCreated virtual display with id 12"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\n[exit 1]"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\nCreated virtual display with id 12"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\nUnknown command: destroy-virtual-display"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\nError: permission denied"))
        assertTrue(VirtualDisplayProtocol.isSuccessful("\n[khatkit-display-exit 0]\n"))
        assertTrue(VirtualDisplayProtocol.isSuccessful("[exit 0]\n\n[khatkit-display-exit 0]\n"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\n[exit 0]\n[khatkit-display-exit 0]\n"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[exit 0]\n\n[khatkit-display-exit 1]\n"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[khatkit-display-exit 0]\n[error] failed"))
        assertFalse(VirtualDisplayProtocol.isSuccessful("[khatkit-display-exit 0]\n[khatkit-display-exit 0]\n"))
    }

    @Test
    fun refusesAmbiguousOrOverflowingDisplayIds() {
        assertNull(VirtualDisplayProtocol.parseCreatedDisplayId(success("Created virtual display with id 2\nDisplay id: 3")))
        assertNull(VirtualDisplayProtocol.parseCreatedDisplayId(success("Created virtual display with id 9999999999999")))
        assertNull(VirtualDisplayProtocol.parseCreatedDisplayId(success("Created virtual display with id -2")))
        assertEquals(7, VirtualDisplayProtocol.parseCreatedDisplayId("Created virtual display with id 7\n[khatkit-display-exit 0]\n"))
    }

    @Test
    fun requiresBothCommandsInSuccessfulHelpOutputBeforeCreatingADisplay() {
        val help = "Display manager commands:\n  create-virtual-display [options] NAME\n  destroy-virtual-display ID\n"
        assertTrue(VirtualDisplayProtocol.supportsVirtualDisplayCommands("$help[khatkit-display-exit 0]\n"))
        assertFalse(VirtualDisplayProtocol.supportsVirtualDisplayCommands(help))
        assertFalse(VirtualDisplayProtocol.supportsVirtualDisplayCommands("[exit 1]\n$help"))
        assertFalse(VirtualDisplayProtocol.supportsVirtualDisplayCommands(success("  create-virtual-display [options] NAME")))
        assertFalse(VirtualDisplayProtocol.supportsVirtualDisplayCommands(success("Unknown command: create-virtual-display")))
        assertFalse(VirtualDisplayProtocol.supportsVirtualDisplayCommands(success("Do not run create-virtual-display or destroy-virtual-display.")))
    }

    @Test
    fun capturesCommandExitBeforeTheSuccessMarkerIsPrinted() {
        val checked = VirtualDisplayProtocol.checkedCommand("cmd display help")
        assertTrue(checked.startsWith("cmd display help; _khatkit_display_exit=\$?;"))
        assertTrue(checked.endsWith("\"\$_khatkit_display_exit\""))
    }

    private fun success(output: String): String = "$output\n[khatkit-display-exit 0]\n"
}
