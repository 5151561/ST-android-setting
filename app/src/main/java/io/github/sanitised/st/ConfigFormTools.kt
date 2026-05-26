package io.github.sanitised.st

import java.util.LinkedHashMap
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

enum class ConfigFormRowKind {
    SECTION,
    BOOLEAN,
    NUMBER,
    TEXT,
    MULTILINE_TEXT,
    SCALAR_LIST,
    YAML,
    NULL
}

data class ConfigFormRow(
    val path: List<String>,
    val pathKey: String,
    val label: String,
    val depth: Int,
    val kind: ConfigFormRowKind,
    val initialText: String
)

data class ConfigFormDocument(
    val root: Map<String, Any?>,
    val rows: List<ConfigFormRow>,
    val initialValues: Map<String, String>
)

object ConfigFormTools {
    private val yaml = Yaml()
    private val yamlWriter = Yaml(
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            width = 120
            splitLines = false
        }
    )

    fun parse(configText: String): ConfigFormDocument {
        val rawRoot = yaml.load<Any?>(configText)
        val root = normalizeMap(rawRoot as? Map<*, *> ?: error("config.yaml must contain a YAML object"))
        val rows = buildList {
            root.forEach { (key, value) ->
                addRows(path = listOf(key), value = value, depth = 0)
            }
        }
        return ConfigFormDocument(
            root = root,
            rows = rows,
            initialValues = rows
                .filter { it.kind != ConfigFormRowKind.SECTION }
                .associate { it.pathKey to it.initialText }
        )
    }

    fun pathKey(vararg segments: String): String = pathKey(segments.toList())

    fun pathKey(path: List<String>): String {
        return path.joinToString(prefix = "/", separator = "/") { segment ->
            segment.replace("~", "~0").replace("/", "~1")
        }
    }

    fun writeYaml(document: ConfigFormDocument, values: Map<String, String>): String {
        val rowsByKey = document.rows.associateBy { it.pathKey }
        val updated = applyEdits(
            value = document.root,
            path = emptyList(),
            rowsByKey = rowsByKey,
            values = values
        )
        return yamlWriter.dump(updated)
    }

    fun readStartupPort(configText: String): Int? {
        return runCatching {
            val rawRoot = yaml.load<Any?>(configText)
            val rawPort = (rawRoot as? Map<*, *>)?.get("port")
            when (rawPort) {
                is Number -> rawPort.toInt()
                is String -> rawPort.trim().toIntOrNull()
                else -> null
            }?.takeIf(::isValidPort)
        }.getOrNull()
    }

    private fun MutableList<ConfigFormRow>.addRows(
        path: List<String>,
        value: Any?,
        depth: Int
    ) {
        if (value is Map<*, *>) {
            add(
                ConfigFormRow(
                    path = path,
                    pathKey = pathKey(path),
                    label = path.last(),
                    depth = depth,
                    kind = ConfigFormRowKind.SECTION,
                    initialText = ""
                )
            )
            normalizeMap(value).forEach { (key, childValue) ->
                addRows(path = path + key, value = childValue, depth = depth + 1)
            }
            return
        }

        val kind = kindFor(value)
        add(
            ConfigFormRow(
                path = path,
                pathKey = pathKey(path),
                label = path.last(),
                depth = depth,
                kind = kind,
                initialText = valueToText(value, kind)
            )
        )
    }

    private fun kindFor(value: Any?): ConfigFormRowKind {
        return when (value) {
            null -> ConfigFormRowKind.NULL
            is Boolean -> ConfigFormRowKind.BOOLEAN
            is Number -> ConfigFormRowKind.NUMBER
            is String -> if (value.contains('\n')) ConfigFormRowKind.MULTILINE_TEXT else ConfigFormRowKind.TEXT
            is List<*> -> if (value.all(::isScalarValue)) ConfigFormRowKind.SCALAR_LIST else ConfigFormRowKind.YAML
            else -> ConfigFormRowKind.YAML
        }
    }

    private fun valueToText(value: Any?, kind: ConfigFormRowKind): String {
        return when (kind) {
            ConfigFormRowKind.BOOLEAN,
            ConfigFormRowKind.NUMBER,
            ConfigFormRowKind.TEXT,
            ConfigFormRowKind.MULTILINE_TEXT -> value?.toString().orEmpty()
            ConfigFormRowKind.NULL -> "null"
            ConfigFormRowKind.SCALAR_LIST -> (value as List<*>).joinToString("\n") { it?.toString().orEmpty() }
            ConfigFormRowKind.YAML -> yamlWriter.dump(value).trimEnd()
            ConfigFormRowKind.SECTION -> ""
        }
    }

    private fun applyEdits(
        value: Any?,
        path: List<String>,
        rowsByKey: Map<String, ConfigFormRow>,
        values: Map<String, String>
    ): Any? {
        if (value is Map<*, *>) {
            val updated = LinkedHashMap<String, Any?>()
            normalizeMap(value).forEach { (key, childValue) ->
                updated[key] = applyEdits(childValue, path + key, rowsByKey, values)
            }
            return updated
        }

        val key = pathKey(path)
        val row = rowsByKey[key] ?: return value
        val text = values[key] ?: row.initialText
        return parseEditedValue(row, value, text)
    }

    private fun parseEditedValue(row: ConfigFormRow, original: Any?, text: String): Any? {
        val trimmed = text.trim()
        return when (row.kind) {
            ConfigFormRowKind.BOOLEAN -> when (trimmed.lowercase()) {
                "true" -> true
                "false" -> false
                else -> error("${row.path.joinToString(".")} must be true or false")
            }
            ConfigFormRowKind.NUMBER -> parseNumber(row, original as Number, trimmed)
            ConfigFormRowKind.TEXT,
            ConfigFormRowKind.MULTILINE_TEXT -> text
            ConfigFormRowKind.NULL -> if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) null else text
            ConfigFormRowKind.SCALAR_LIST -> parseScalarList(row, original as List<*>, text)
            ConfigFormRowKind.YAML -> runCatching { yaml.load<Any?>(text) }
                .getOrElse { error("${row.path.joinToString(".")} is not valid YAML: ${it.message}") }
            ConfigFormRowKind.SECTION -> original
        }
    }

    private fun parseNumber(row: ConfigFormRow, original: Number, text: String): Number {
        if (text.isBlank()) error("${row.path.joinToString(".")} must be a number")
        fun fail(): Nothing = error("${row.path.joinToString(".")} must be a number")
        return when (original) {
            is Byte -> text.toByteOrNull() ?: fail()
            is Short -> text.toShortOrNull() ?: fail()
            is Int -> text.toIntOrNull() ?: fail()
            is Long -> text.toLongOrNull() ?: fail()
            is Float -> text.toFloatOrNull() ?: fail()
            is Double -> text.toDoubleOrNull() ?: fail()
            else -> text.toIntOrNull() ?: text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail()
        }
    }

    private fun parseScalarList(row: ConfigFormRow, original: List<*>, text: String): List<Any?> {
        val lines = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val sample = original.firstOrNull { it != null }
        return lines.map { line ->
            when (sample) {
                is Boolean -> when (line.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> error("${row.path.joinToString(".")} list values must be true or false")
                }
                is Number -> parseNumber(row, sample, line)
                else -> line
            }
        }
    }

    private fun normalizeMap(map: Map<*, *>): LinkedHashMap<String, Any?> {
        val normalized = LinkedHashMap<String, Any?>()
        map.forEach { (key, value) ->
            normalized[key?.toString().orEmpty()] = normalizeValue(value)
        }
        return normalized
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeMap(value)
            is List<*> -> value.map(::normalizeValue)
            else -> value
        }
    }

    private fun isScalarValue(value: Any?): Boolean {
        return value == null || value is String || value is Boolean || value is Number
    }

    private fun isValidPort(port: Int): Boolean = port in 1..65535
}
