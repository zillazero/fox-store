package com.looker.droidify.model

import java.net.URL

data class Repository(
    var id: Long,
    val address: String,
    val mirrors: List<String>,
    val name: String,
    val description: String,
    val version: Int,
    val enabled: Boolean,
    val fingerprint: String,
    val lastModified: String,
    val entityTag: String,
    val updated: Long,
    val timestamp: Long,
    val authentication: String,
) {

    fun edit(address: String, fingerprint: String, authentication: String): Repository {
        val isAddressChanged = this.address != address
        val isFingerprintChanged = this.fingerprint != fingerprint
        val shouldForceUpdate = isAddressChanged || isFingerprintChanged
        return copy(
            address = address,
            fingerprint = fingerprint,
            lastModified = if (shouldForceUpdate) "" else lastModified,
            entityTag = if (shouldForceUpdate) "" else entityTag,
            authentication = authentication,
        )
    }

    fun update(
        mirrors: List<String>,
        name: String,
        description: String,
        version: Int,
        lastModified: String,
        entityTag: String,
        timestamp: Long,
    ): Repository {
        return copy(
            mirrors = mirrors,
            name = name,
            description = description,
            version = if (version >= 0) version else this.version,
            lastModified = lastModified,
            entityTag = entityTag,
            updated = System.currentTimeMillis(),
            timestamp = timestamp,
        )
    }

    fun enable(enabled: Boolean): Repository {
        return copy(enabled = enabled, lastModified = "", entityTag = "")
    }

    @Suppress("SpellCheckingInspection")
    companion object {

        fun newRepository(
            address: String,
            fingerprint: String,
            authentication: String,
        ): Repository {
            val name = try {
                URL(address).let { "${it.host}${it.path}" }
            } catch (e: Exception) {
                address
            }
            return defaultRepository(address, name, "", 0, true, fingerprint, authentication)
        }

        fun defaultRepository(
            address: String,
            name: String,
            description: String,
            version: Int = 21,
            enabled: Boolean = false,
            fingerprint: String,
            authentication: String = "",
        ): Repository {
            return Repository(
                -1, address, emptyList(), name, description, version, enabled,
                fingerprint, "", "", 0L, 0L, authentication,
            )
        }

        /** FOX Store: feste Hauptquelle (nicht löschbar, nicht abschaltbar). */
        const val FOX_REPO_ADDRESS = "https://apps.pk187.site/fdroid/repo"
        const val FOX_REPO_FINGERPRINT = "7E9BF6C4A35D14608F5387B406E967909451131B2F804D4978DBC960A4666033"
        /** Präfix der untergeordneten Rubrik für freie Fremdquellen. */
        const val FREE_GROUP_PREFIX = "Weitere freie Apps · "

        fun isFoxRepo(address: String): Boolean = address.trimEnd('/') == FOX_REPO_ADDRESS

        val defaultRepositories = listOf(
            defaultRepository(
                address = FOX_REPO_ADDRESS,
                name = "FOX Store",
                description = "Hauptquelle des FOX Store (pk187).",
                enabled = true,
                fingerprint = FOX_REPO_FINGERPRINT,
            ),
            defaultRepository(
                address = "https://f-droid.org/repo",
                name = FREE_GROUP_PREFIX + "F-Droid",
                description = "Freie Software, aus dem Quellcode gebaut und geprüft.",
                enabled = true,
                fingerprint = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB",
            ),
            defaultRepository(
                address = "https://apt.izzysoft.de/fdroid/repo",
                name = FREE_GROUP_PREFIX + "IzzyOnDroid",
                description = "Freie Apps direkt von den Entwicklern, schnelle Updates, Sicherheits-Scans.",
                enabled = true,
                fingerprint = "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A",
            ),
            defaultRepository(
                address = "https://guardianproject.info/fdroid/repo",
                name = FREE_GROUP_PREFIX + "Guardian Project",
                description = "Datenschutz- und Sicherheits-Apps (z. B. Tor) vom Guardian Project.",
                fingerprint = "B7C2EEFD8DAC7806AF67DFCD92EB18126BC08312A7F2D6F3862E46013C7A6135",
            ),
            defaultRepository(
                address = "https://mobileapp.bitwarden.com/fdroid/repo",
                name = FREE_GROUP_PREFIX + "Bitwarden",
                description = "Offizielle Quelle des Passwort-Managers Bitwarden.",
                fingerprint = "BC54EA6FD1CD5175BCCCC47C561C5726E1C3ED7E686B6DB4B18BAC843A3EFE6C",
            ),
        )

        fun newRepos(): List<Repository> = listOf()

        fun addressesToRemove(): List<String> = listOf(
            // Add repository addresses that should be removed during database upgrades and remove them from the lists above
            // Example: "https://example.com/fdroid/repo"
        )
    }
}
