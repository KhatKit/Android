package heizige.kk.khatkit.card

/**
 * 极简 SemVer：支持 `^` `~` 精确、`>` `>=` `<` `<=`、`*`。
 * 空格/逗号 = AND，`||` = OR。够卡片依赖声明用即可，不追求完整规范。
 */
object Semver {

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

        override fun toString(): String = "$major.$minor.$patch"
    }

    fun parse(text: String): Version? {
        val cleaned = text.trim().trimStart('v', '^', '~', '=', '>', '<', ' ')
        val match = Regex("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(cleaned) ?: return null
        return Version(
            major = match.groupValues[1].toIntOrNull() ?: return null,
            minor = match.groupValues[2].toIntOrNull() ?: 0,
            patch = match.groupValues[3].toIntOrNull() ?: 0,
        )
    }

    fun satisfies(versionText: String, rangeText: String): Boolean {
        val version = parse(versionText) ?: return false
        val range = rangeText.trim()
        if (range.isEmpty() || range == "*" || range.equals("latest", ignoreCase = true)) return true
        return range.split("||").any { orPart ->
            orPart.split(Regex("[\\s,]+"))
                .filter(String::isNotBlank)
                .all { constraint -> matchConstraint(version, constraint) }
        }
    }

    private fun matchConstraint(version: Version, constraint: String): Boolean = when {
        constraint.startsWith("^") -> {
            val base = parse(constraint)
            base != null && version >= base && version.major == base.major
        }

        constraint.startsWith("~") -> {
            val base = parse(constraint)
            base != null && version >= base && version.major == base.major && version.minor == base.minor
        }

        constraint.startsWith(">=") -> parse(constraint)?.let { version >= it } == true
        constraint.startsWith("<=") -> parse(constraint)?.let { version <= it } == true
        constraint.startsWith(">") -> parse(constraint)?.let { version > it } == true
        constraint.startsWith("<") -> parse(constraint)?.let { version < it } == true
        constraint.startsWith("=") -> parse(constraint)?.let { version == it } == true
        else -> parse(constraint)?.let { version == it } == true
    }
}
