package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.SecretEntry
import io.github.sanitised.st.api.SecretProviderState
import java.util.Locale

internal data class SecretProviderOption(
    val key: String,
    val label: String
)

internal data class SecretDisplayRow(
    val providerKey: String,
    val providerLabel: String,
    val entry: SecretEntry
) {
    val displayLabel: String = entry.label.ifBlank { "默认密钥" }
    val displayValue: String = entry.value.takeIf { it.isConfiguredSecretValue() } ?: "已保存，后端未返回可显示值"
    val statusLabel: String = if (entry.active) "当前启用" else "已配置"
}

internal fun configuredSecretRows(secrets: List<SecretProviderState>): List<SecretDisplayRow> {
    return secrets.flatMap { provider ->
        provider.entries
            .filter { it.isConfiguredSecretEntry() }
            .map { entry ->
                SecretDisplayRow(
                    providerKey = provider.key,
                    providerLabel = provider.label,
                    entry = entry
                )
            }
    }
}

internal fun secretProviderOptions(secrets: List<SecretProviderState>): List<SecretProviderOption> {
    val backendOptions = secrets.map { provider ->
        SecretProviderOption(
            key = provider.key,
            label = provider.label
        )
    }
    val knownOptions = apiConnectionSecretProviderDefinitions().flatMap { provider ->
        provider.secretKeys.map { key ->
            SecretProviderOption(
                key = key,
                label = provider.label
            )
        }
    }

    return (backendOptions + knownOptions)
        .filter { it.key.isNotBlank() }
        .distinctBy { it.key }
        .sortedBy { it.label.lowercase(Locale.US) }
}

private fun SecretEntry.isConfiguredSecretEntry(): Boolean {
    return id.isNotBlank() || value.isConfiguredSecretValue() || label.isNotBlank()
}

private fun String.isConfiguredSecretValue(): Boolean {
    return isNotBlank() && !equals("null", ignoreCase = true)
}
