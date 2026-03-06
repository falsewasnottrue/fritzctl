package fritzctl

import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.terminal.Terminal

class Output(val format: OutputFormat?, private val terminal: Terminal) {

    /**
     * Gibt eine Erfolgsmeldung aus.
     * - Bei machine-readable Formaten: strukturierte Ausgabe mit status=ok und den übergebenen Feldern.
     * - Bei menschenlesbarer Ausgabe: der [humanBlock] wird ausgeführt.
     */
    fun success(vararg fields: Pair<String, String>, humanBlock: () -> Unit) {
        when (format) {
            OutputFormat.YAML -> println(buildYaml("ok", fields.toList()))
            OutputFormat.JSON -> println(buildJson("ok", fields.toList()))
            null -> humanBlock()
        }
    }

    /**
     * Gibt eine Warnmeldung aus (kein Fehlercode).
     * - Bei machine-readable Formaten: strukturierte Ausgabe mit status=warning.
     * - Bei menschenlesbarer Ausgabe: gelbe Konsolenausgabe.
     */
    fun warning(message: String) {
        when (format) {
            OutputFormat.YAML -> println(buildYaml("warning", listOf("message" to message)))
            OutputFormat.JSON -> println(buildJson("warning", listOf("message" to message)))
            null -> terminal.println(yellow(message))
        }
    }

    /**
     * Gibt einen Fehler aus.
     * - Bei machine-readable Formaten: strukturierte Ausgabe mit status=error.
     * - Bei menschenlesbarer Ausgabe: rote Konsolenausgabe.
     */
    fun error(message: String) {
        when (format) {
            OutputFormat.YAML -> println(buildYaml("error", listOf("message" to message)))
            OutputFormat.JSON -> println(buildJson("error", listOf("message" to message)))
            null -> terminal.println(red("Fehler: $message"))
        }
    }

    val isMachineReadable: Boolean get() = format != null

    // -------------------------------------------------------------------------

    private fun buildYaml(status: String, fields: List<Pair<String, String>>): String =
        buildString {
            appendLine("status: $status")
            fields.forEach { (k, v) -> appendLine("$k: ${yamlString(v)}") }
        }.trimEnd()

    private fun buildJson(status: String, fields: List<Pair<String, String>>): String =
        buildString {
            appendLine("{")
            append("  \"status\": ${jsonString(status)}")
            fields.forEach { (k, v) -> append(",\n  ${jsonString(k)}: ${jsonString(v)}") }
            append("\n}")
        }

    private fun yamlString(value: String): String {
        val needsQuoting = value.any { it in ":#{},[]|>&*!'\"%@`\n\r\t" } || value.isBlank()
        return if (needsQuoting) "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else value
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
