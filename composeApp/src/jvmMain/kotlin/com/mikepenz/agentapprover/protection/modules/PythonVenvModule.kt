package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object PythonVenvModule : ProtectionModule {
    override val id = "python_venv"
    override val name = "Python Virtual Environment"
    override val description = "Enforces use of virtual environments and uv for Python commands."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        BarePython,
        BarePip,
        PythonMVenv,
    )

    private val venvActivatePattern =
        Regex("""(?:^|\s|;|&&|\|\|)(?:source|\.)\s+\S*(?:\.venv|venv|env)/bin/activate\b""")

    /** True if a `source .venv/bin/activate` occurs strictly before `targetStart` in the command. */
    private fun activatedBefore(cmd: String, targetStart: Int): Boolean =
        venvActivatePattern.findAll(cmd).any { it.range.last < targetStart }

    /** True if the start of [matchRange] falls inside any range produced by [allowPatterns]. */
    private fun isAllowedByPattern(
        cmd: String,
        matchRange: IntRange,
        allowPatterns: List<Regex>,
    ): Boolean = allowPatterns.any { allow ->
        allow.findAll(cmd).any { it.range.first <= matchRange.first && it.range.last >= matchRange.first }
    }

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object BarePython : ProtectionRule {
        override val id = "bare_python"
        override val name = "Bare python command"
        override val description = "Detects bare python command without virtual environment."
        override val correctiveHint = "Use .venv/bin/python or uv run python instead. Create a venv with: uv venv"
        private val pattern = Regex("""\bpython[23]?\s""")
        private val allowPatterns = listOf(
            Regex("""\bpython[23]?\s+--version\b"""),
            Regex("""\.venv/bin/python"""),
            Regex("""\buv\s+run\s+python"""),
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val offending = pattern.findAll(cmd).firstOrNull { match ->
                !isAllowedByPattern(cmd, match.range, allowPatterns) &&
                    !activatedBefore(cmd, match.range.first)
            } ?: return null
            return hit(
                id,
                "Use .venv/bin/python or uv run python instead. Create a venv with: uv venv (at: ${offending.value.trim()})",
            )
        }
    }

    private object BarePip : ProtectionRule {
        override val id = "bare_pip"
        override val name = "Bare pip install"
        override val description = "Detects pip install without virtual environment."
        override val correctiveHint = "Use uv pip install or activate a virtual environment first. Create one with: uv venv && source .venv/bin/activate"
        private val pipPattern = Regex("""\b(pip|pip3)\s+install\b""")
        private val pythonMPipPattern = Regex("""\bpython[23]?\s+-m\s+pip\s+install\b""")
        private val allowPatterns = listOf(Regex("""\buv\s+pip\s+install\b"""))

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val allMatches = pipPattern.findAll(cmd) + pythonMPipPattern.findAll(cmd)
            val offending = allMatches.firstOrNull { match ->
                !isAllowedByPattern(cmd, match.range, allowPatterns) &&
                    !activatedBefore(cmd, match.range.first)
            } ?: return null
            return hit(id, "Use uv pip install or activate a venv first (at: ${offending.value.trim()})")
        }
    }

    private object PythonMVenv : ProtectionRule {
        override val id = "python_m_venv"
        override val name = "Python -m venv"
        override val description = "Detects python -m venv and suggests uv venv instead."
        override val correctiveHint = "Use uv venv instead of python -m venv. uv is faster and more reliable."
        private val pattern = Regex("""\bpython[23]?\s+-m\s+venv\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Use uv venv instead. uv is faster and more reliable.")
        }
    }
}
