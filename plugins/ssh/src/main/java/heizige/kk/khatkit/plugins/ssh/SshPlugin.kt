package heizige.kk.khatkit.plugins.ssh

import android.content.Context
import heizige.kk.khatkit.ssh.api.SshEngine
import heizige.kk.khatkit.ssh.api.SshRequest
import heizige.kk.khatkit.ssh.api.SshResult
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.Closeable
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 可下载的远程 SSH 插件实现（market kind=dependency, plugin_id=ssh）。
 *
 * 宿主只认 [heizige.kk.khatkit.ssh.api.SshEngine]；这个类由 DexClassLoader 按
 * [heizige.kk.khatkit.ssh.api.SshPluginInfo.ENTRY_CLASS] 反射实例化，必须保留无参构造函数。
 */
class SshEngineImpl : SshEngine {
    override fun execute(context: Context, request: SshRequest, timeoutMillis: Long): SshResult =
        SshClient(context.applicationContext).execute(request, timeoutMillis)
}

/**
 * A short-lived SSH client. Credentials are only held by the caller and are never persisted.
 *
 * Host keys use TOFU: the first key seen for host:port is recorded locally, then every later
 * connection must present the same key. A changed key fails closed.
 */
/**
 * Android 专用谈判面：只留 Android 平台 JCE 真支持的算法。
 *
 * 原因：BouncyCastle 的 multi-release jar 里 X25519/EdDSA 的 KeyPairGenerator/KeyFactory
 * 在 META-INF/versions/11 下，D8 打进 dex 时只取基础目录，于是 provider BC 里查不到
 * "X25519"（实测崩在 KeyExchanger："no such algorithm: X25519 for provider BC"）。
 * 这里用 sshj 自带的 AndroidConfig，并把 curve25519 与 ed25519/sk-* 从候选里删掉，
 * 让协商落到 ecdh-sha2-nistp256 + rsa-sha2 / ecdsa 主机密钥（Conscrypt 都支持）。
 */
private fun androidSafeConfig(): AndroidConfig = AndroidConfig().apply {
    keyExchangeFactories = keyExchangeFactories.filterNot { it.name.contains("curve25519", ignoreCase = true) }
    keyAlgorithms = keyAlgorithms.filterNot {
        val name = it.name.lowercase()
        name.contains("ed25519") || name.startsWith("sk-")
    }
}

internal class SshClient(context: Context) : Closeable {
    private val hostKeys = SshHostKeyStore(context)
    private var active: SSHClient? = null

    fun execute(request: SshRequest, timeoutMillis: Long = 30_000L): SshResult {
        require(request.host.isNotBlank()) { "主机地址不能为空" }
        require(request.username.isNotBlank()) { "用户名不能为空" }
        require(request.password.isNotEmpty()) { "密码不能为空" }
        require(request.command.isNotBlank()) { "命令不能为空" }
        require(request.port in 1..65535) { "端口范围必须是 1-65535" }
        require(timeoutMillis > 0) { "超时时间必须大于 0" }

        val timeout = timeoutMillis.toInt()
        val client = SSHClient(androidSafeConfig()).apply {
            connectTimeout = timeout
            setTimeout(timeout)
        }
        active = client
        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean =
                hostKeys.verifyOrRemember(hostname, port, key)

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
                emptyList()
        })
        return try {
            client.connect(request.host, request.port)
            client.authPassword(request.username, request.password)
            client.startSession().use { session ->
                session.exec(request.command).use { commandSession ->
                    val stdout = StringBuilder()
                    val stderr = StringBuilder()
                    val stdoutReader = readAsync(commandSession.inputStream) { stdout.append(it) }
                    val stderrReader = readAsync(commandSession.errorStream) { stderr.append(it) }
                    stdoutReader.join(timeoutMillis)
                    stderrReader.join(timeoutMillis)
                    if (stdoutReader.isAlive || stderrReader.isAlive) {
                        throw SshTimeoutException("命令执行超过 ${timeoutMillis}ms，已断开连接")
                    }
                    commandSession.join(timeoutMillis, TimeUnit.MILLISECONDS)
                    val combined = buildString {
                        append(stdout)
                        if (stderr.isNotBlank()) {
                            if (isNotEmpty() && !endsWith("\n")) append('\n')
                            append(stderr)
                        }
                    }
                    SshResult(
                        exitCode = commandSession.exitStatus ?: -1,
                        output = combined.trimEnd(),
                    )
                }
            }
        } finally {
            close()
        }
    }

    override fun close() {
        active?.let { runCatching { it.disconnect() } }
        active = null
    }

    /** Drains one stream on a daemon thread so a noisy stderr can never block stdout. */
    private fun readAsync(stream: InputStream, sink: (String) -> Unit): Thread =
        Thread {
            runCatching { stream.bufferedReader().use { sink(it.readText()) } }
        }.apply {
            isDaemon = true
            start()
        }
}

class SshTimeoutException(message: String) : Exception(message)

private class SshHostKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("khatkit_ssh_host_keys", Context.MODE_PRIVATE)

    fun verifyOrRemember(host: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = fingerprint(key)
        val name = "$host:$port"
        val known = prefs.getString(name, null)
        if (known == null) {
            prefs.edit().putString(name, fingerprint).apply()
            return true
        }
        check(known == fingerprint) {
            "SSH 主机密钥已变更（$host:$port），为避免中间人攻击已拒绝连接"
        }
        return true
    }

    private fun fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
