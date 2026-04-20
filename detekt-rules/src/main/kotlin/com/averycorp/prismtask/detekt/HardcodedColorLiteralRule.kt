package com.averycorp.prismtask.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags hardcoded `Color(0xAARRGGBB)` literals in Compose files.
 * Theme-owned files and user-facing color pickers are excluded via the
 * `excludes` patterns in `detekt.yml`.
 *
 * Severity is WARNING — this rule never fails CI builds (warningsAsErrors: false).
 */
class HardcodedColorLiteralRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Warning,
        description = "Use LocalPrismColors theme tokens instead of hardcoded Color(0x…) literals.",
        debt = Debt.FIVE_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != "Color") return
        val args = expression.valueArguments
        if (args.size != 1) return

        val argText = args[0].getArgumentExpression()?.text ?: return
        if (!argText.startsWith("0x") && !argText.startsWith("0X")) return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Hardcoded Color($argText) found. Use LocalPrismColors.current.<token> instead."
            )
        )
    }
}
