package heizige.kk.khatkit.plugins.ssh

import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.ssh.api.SshRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshPluginTest {
    @Test
    fun descriptorIsAnOptInDependencyPluginAndNotACard() {
        assertEquals("remote-ssh", SshPluginInfo.ID)
        assertEquals("ssh", SshPluginInfo.PACKAGE_ID)
        assertTrue(SshPluginInfo.VERSION.isNotBlank())
        assertTrue(SshPluginInfo.DESCRIPTION.contains("Linux"))
    }

    /** 打包脚本里的 entry_class 必须和实现类对得上，否则安装后 DexClassLoader 反射落空。 */
    @Test
    fun entryClassMatchesTheImplementation() {
        assertEquals(SshPluginInfo.ENTRY_CLASS, SshEngineImpl::class.java.name)
    }

    @Test
    fun requestDefaultsToTheStandardSshPort() {
        assertEquals(
            22,
            SshRequest(host = "host", username = "user", password = "secret", command = "uname").port,
        )
    }
}
