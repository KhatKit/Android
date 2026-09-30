package heizige.kk.khatkit.dependencies.musicKey

/** 独立音乐解密依赖入口，可由 Lua 通过 musicKey.call("decrypt", ...) 调用。 */
class MusicKeyDependency {
    fun decrypt(path: String, output: String = ""): String = MusicKeyDecoder.decrypt(path, output)
}
