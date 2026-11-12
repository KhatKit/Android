package heizige.kk.khatkit.app.core.data.ai.tools

import android.content.Context
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader
import heizige.kk.khatkit.app.core.data.ssh.SshPluginStore
import heizige.kk.khatkit.ssh.api.SshEngineProvider
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.ssh.api.SshRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Tools contributed by installed opt-in plugins.
 *
 * The catalogue tool is always available so the model can discover which plugins are installed.
 * A plugin's operational tools are added only when its engine is loaded successfully.
 */
/**
 * Builds the tools exposed by installed plugins for one generation.
 *
 * Loading the persisted plugin record here matters because chat can be opened before the
 * Extensions/Explore screens have had a chance to warm the dynamic loader.
 */
suspend fun createPluginTools(context: Context): List<Tool> {
    val appContext = context.applicationContext
    SshPluginLoader.prepare(appContext)
    return buildList {
        add(
            Tool(
                name = "plugin_list",
                description = "列出当前已安装且可用的 KhatKit 插件及其能力。需要插件能力前先调用此工具。",
                parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
                execute = {
                    val ssh = SshPluginStore(appContext).read()
                    val payload = buildJsonObject {
                        put("plugins", buildJsonObject {
                            if (ssh != null) {
                                val available = SshEngineProvider.engine() != null
                                put(SshPluginInfo.ID, buildJsonObject {
                                    put("name", SshPluginInfo.DISPLAY_NAME)
                                    put("version", ssh.version)
                                    put("capability", "ssh_execute")
                                    put("description", SshPluginInfo.DESCRIPTION)
                                    put("installed", true)
                                    put("available", available)
                                })
                            }
                        })
                    }
                    listOf(UIMessagePart.Text(payload.toString()))
                },
            ),
        )

        if (SshEngineProvider.engine() != null) {
            add(
                Tool(
                    name = "ssh_execute",
                    description = """
                        通过已安装的远程 SSH 插件在用户指定的主机上执行一条命令。
                        这是外部副作用操作，每次调用都需要用户确认。不要猜测主机、用户名或密码；
                        如果用户没有提供这些参数，先向用户询问。密码只在本次调用内存中使用，不会保存。
                    """.trimIndent().replace("\n", " "),
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("host", buildJsonObject {
                                    put("type", "string")
                                    put("description", "SSH 主机名或 IP 地址")
                                })
                                put("port", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "SSH 端口，默认 22")
                                })
                                put("username", buildJsonObject {
                                    put("type", "string")
                                    put("description", "SSH 用户名")
                                })
                                put("password", buildJsonObject {
                                    put("type", "string")
                                    put("description", "SSH 密码；仅本次调用使用")
                                })
                                put("command", buildJsonObject {
                                    put("type", "string")
                                    put("description", "要在远程主机执行的 shell 命令")
                                })
                                put("timeout_ms", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "超时时间，范围 1000 到 600000 毫秒，默认 30000")
                                })
                            },
                            required = listOf("host", "username", "password", "command"),
                        )
                    },
                    needsApproval = { true },
                    execute = { input ->
                        val params = input.jsonObject
                        val engine = SshEngineProvider.engine()
                            ?: error("远程 SSH 插件未安装或加载失败")
                        val request = SshRequest(
                            host = params["host"]?.jsonPrimitive?.contentOrNull
                                ?: error("host is required"),
                            port = (params["port"]?.jsonPrimitive?.intOrNull ?: 22)
                                .coerceIn(1, 65535),
                            username = params["username"]?.jsonPrimitive?.contentOrNull
                                ?: error("username is required"),
                            password = params["password"]?.jsonPrimitive?.contentOrNull
                                ?: error("password is required"),
                            command = params["command"]?.jsonPrimitive?.contentOrNull
                                ?: error("command is required"),
                        )
                        val timeout = (params["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 30_000L)
                            .coerceIn(1_000L, 600_000L)
                        val result = engine.execute(appContext, request, timeout)
                        listOf(
                            UIMessagePart.Text(
                                buildJsonObject {
                                    put("success", result.exitCode == 0)
                                    put("exit_code", result.exitCode)
                                    put("output", result.output)
                                }.toString(),
                            ),
                        )
                    },
                ),
            )
        }
    }
}
