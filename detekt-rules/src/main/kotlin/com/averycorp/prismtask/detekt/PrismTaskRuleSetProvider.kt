package com.averycorp.prismtask.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class PrismTaskRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "PrismTaskCustom"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            HardcodedColorLiteralRule(config.subConfig("HardcodedColorLiteral"))
        )
    )
}
