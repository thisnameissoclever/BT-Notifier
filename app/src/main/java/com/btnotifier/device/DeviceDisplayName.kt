package com.btnotifier.device

object DeviceDisplayName {

    fun normalizeAlias(alias: String?): String? {
        return alias?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun resolve(
        alias: String?,
        primaryName: String?,
        secondaryName: String?,
        fallback: String
    ): String {
        return normalizeAlias(alias)
            ?: primaryName?.trim()?.takeIf { it.isNotEmpty() }
            ?: secondaryName?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback
    }

    fun safeActualNameForAliasEdit(
        alias: String?,
        lastSeenName: String?,
        storedWatchedName: String?
    ): String? {
        normalizeActualName(lastSeenName)?.let { return it }

        val normalizedAlias = normalizeAlias(alias)
        val normalizedStoredName = normalizeActualName(storedWatchedName)
        return normalizedStoredName?.takeUnless { storedName ->
            normalizedAlias != null && storedName == normalizedAlias
        }
    }

    fun retainedLastSeenName(
        existingAlias: String?,
        existingLastSeenName: String?,
        actualName: String?
    ): String? {
        normalizeActualName(actualName)?.let { return it }

        val normalizedAlias = normalizeAlias(existingAlias)
        val normalizedLastSeenName = normalizeActualName(existingLastSeenName)
        return normalizedLastSeenName?.takeUnless { lastSeenName ->
            normalizedAlias != null && lastSeenName == normalizedAlias
        }
    }

    fun storedNameLooksLikeAlias(alias: String?, storedWatchedName: String?): Boolean {
        val normalizedAlias = normalizeAlias(alias) ?: return false
        return normalizeActualName(storedWatchedName) == normalizedAlias
    }

    private fun normalizeActualName(name: String?): String? {
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }
}
