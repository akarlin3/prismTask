package com.averycorp.prismtask.domain.usecase

/**
 * Expands user-defined text shortcuts in an input string. Runs BEFORE the
 * NaturalLanguageParser pipeline, so expanded text carries through the normal
 * #tag @project !priority parsing.
 *
 * Matching rules:
 * - Whole-word only (word boundaries on both sides). Typing "show" must NOT
 *   trigger a "hw" shortcut.
 * - Case-sensitive (matches the stored trigger exactly).
 * - Longest-match-first when multiple triggers could apply in the same
 *   position (to avoid ambiguity between, e.g., "hw" and "hwk").
 */
object NlpShortcutExpander {
    data class Shortcut(
        val trigger: String,
        val expansion: String
    )

    /**
     * Expands any shortcut trigger found in [input]. Returns the expanded
     * string. If no shortcuts match, returns [input] unchanged.
     */
    fun expand(input: String, shortcuts: List<Shortcut>): String {
        if (input.isBlank() || shortcuts.isEmpty()) return input

        // Sort triggers by length descending so longer triggers match first.
        val sorted = shortcuts.sortedByDescending { it.trigger.length }

        val result = StringBuilder()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            val atWordStart = i == 0 || !input[i - 1].isLetterOrDigit()

            if (atWordStart) {
                var matched: Shortcut? = null
                for (sc in sorted) {
                    val end = i + sc.trigger.length
                    if (end <= input.length && input.substring(i, end) == sc.trigger) {
                        // Ensure word end (next char is non-alnum or end of string)
                        val atWordEnd = end == input.length || !input[end].isLetterOrDigit()
                        if (atWordEnd) {
                            matched = sc
                            break
                        }
                    }
                }
                if (matched != null) {
                    result.append(matched.expansion)
                    i += matched.trigger.length
                    continue
                }
            }
            result.append(ch)
            i++
        }
        return result.toString()
    }
}
