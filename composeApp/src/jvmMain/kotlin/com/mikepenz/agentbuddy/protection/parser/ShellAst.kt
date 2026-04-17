package com.mikepenz.agentbuddy.protection.parser

/**
 * A parsed bash command tree. This is deliberately small: just enough structure that protection
 * rules can ask semantic questions ("is there an `rm` command anywhere in this chain?", "does
 * this pipeline contain `curl` followed later by `bash`?", "what file does this `>` write to?")
 * without each rule re-implementing its own regex parser.
 *
 * The parser is intentionally fail-closed: any token whose literal value cannot be recovered
 * (variable expansion, command substitution, arithmetic expansion) yields a [ShellToken.Word]
 * with `literal == null`. Rules that enumerate allow-lists must treat such words as unknown and
 * reject them; rules that look for forbidden literals simply won't find a match on them.
 */

internal enum class ChainOp { NONE, SEMI, AND, OR, AMP, NEWLINE }

internal data class ParsedCommand(val pipelines: List<PipelineEntry>) {
    val isEmpty: Boolean get() = pipelines.isEmpty()
}

internal data class PipelineEntry(val leading: ChainOp, val pipeline: Pipeline)

internal data class Pipeline(val commands: List<SimpleCommand>)

internal data class SimpleCommand(
    val assignments: List<Assignment>,
    val name: ShellToken.Word?,
    val args: List<ShellToken.Word>,
    val redirects: List<Redirect>,
    /** Set if this "command" is really a subshell `( ... )` or brace group `{ ...; }`. */
    val subshell: ParsedCommand? = null,
) {
    /** Literal basename of the command, or null if the name is opaque / missing. */
    val commandName: String?
        get() {
            val lit = name?.literal ?: return null
            // Strip trailing slash / path prefix.
            val trimmed = lit.substringAfterLast('/')
            return trimmed.ifEmpty { null }
        }

    val rawName: String? get() = name?.literal

    /** Literal (non-opaque) argument strings. Opaque args are skipped. */
    fun literalArgs(): List<String> = args.mapNotNull { it.literal }

    /** True if any argument contains an opaque (variable / substitution) token. */
    fun hasOpaqueArg(): Boolean = args.any { it.isOpaque }

    /** True if any redirect has an opaque target. */
    fun hasOpaqueRedirectTarget(): Boolean = redirects.any { it.target.isOpaque }

    /** True if the command name or any arg/redirect is opaque — rules can use this for fail-closed checks. */
    val isFullyLiteral: Boolean
        get() = name?.literal != null && args.all { !it.isOpaque } && redirects.all { !it.target.isOpaque }

    /** True if any literal argument equals [value]. */
    fun hasLiteralArg(value: String): Boolean = args.any { it.literal == value }

    /**
     * True if the command carries the given flag. Supports long (`--global`) and short (`-g`)
     * forms, plus combined short flags (`-rf` contains `r` and `f`).
     */
    fun hasFlag(short: Char? = null, long: String? = null): Boolean {
        for (a in args) {
            val lit = a.literal ?: continue
            if (long != null && lit == long) return true
            if (short != null && lit.length >= 2 && lit[0] == '-' && lit[1] != '-') {
                if (lit.drop(1).contains(short)) return true
            }
        }
        return false
    }

    /** True if the exact literal long-flag appears. */
    fun hasLongFlag(long: String): Boolean = args.any { it.literal == long }

    /** First positional (non-flag) literal argument at or after [fromIndex]. */
    fun positionalAfter(fromIndex: Int = 0): String? {
        for (i in fromIndex until args.size) {
            val lit = args[i].literal ?: continue
            if (!lit.startsWith("-")) return lit
        }
        return null
    }

    /** All redirect targets (opaque-safe: opaque targets are skipped). */
    fun literalRedirectTargets(): List<Pair<OpKind, String>> =
        redirects.mapNotNull { r -> r.target.literal?.let { r.op to it } }
}

internal data class Assignment(val name: String, val value: ShellToken.Word)

internal data class Redirect(
    val op: OpKind,
    val fd: Int?,
    val target: ShellToken.Word,
    val heredocBody: String? = null,
)

internal class ShellParser(private val tokens: List<ShellToken>) {
    private var pos = 0

    fun parse(): ParsedCommand = parseList(stopAt = emptySet())

    private fun parseList(stopAt: Set<OpKind>): ParsedCommand {
        val entries = mutableListOf<PipelineEntry>()
        var leading = ChainOp.NONE
        while (pos < tokens.size) {
            skipSeparatorTokens()
            if (pos >= tokens.size) break
            val head = tokens[pos]
            if (head is ShellToken.Op && head.kind in stopAt) break
            val pipeline = parsePipeline(stopAt) ?: break
            entries.add(PipelineEntry(leading, pipeline))
            leading = consumeChainOp() ?: ChainOp.NONE
            if (leading == ChainOp.NONE) break
        }
        return ParsedCommand(entries)
    }

    private fun skipSeparatorTokens() {
        while (pos < tokens.size) {
            val t = tokens[pos]
            if (t is ShellToken.Op && (t.kind == OpKind.NEWLINE || t.kind == OpKind.SEMI)) pos++ else return
        }
    }

    private fun consumeChainOp(): ChainOp? {
        // Skip leading NEWLINEs — the "real" chain op may follow (e.g. after a heredoc body).
        var sawNewline = false
        while (pos < tokens.size) {
            val t = tokens[pos] as? ShellToken.Op ?: return if (sawNewline) ChainOp.NEWLINE else null
            when (t.kind) {
                OpKind.NEWLINE -> { sawNewline = true; pos++ }
                OpKind.AND -> { pos++; return ChainOp.AND }
                OpKind.OR -> { pos++; return ChainOp.OR }
                OpKind.SEMI -> { pos++; return ChainOp.SEMI }
                OpKind.AMP -> { pos++; return ChainOp.AMP }
                else -> return if (sawNewline) ChainOp.NEWLINE else null
            }
        }
        return if (sawNewline) ChainOp.NEWLINE else null
    }

    private fun parsePipeline(stopAt: Set<OpKind>): Pipeline? {
        val cmds = mutableListOf<SimpleCommand>()
        val first = parseCommand(stopAt) ?: return null
        cmds.add(first)
        while (pos < tokens.size) {
            val t = tokens[pos]
            if (t is ShellToken.Op && (t.kind == OpKind.PIPE || t.kind == OpKind.PIPE_AMP)) {
                pos++
                val next = parseCommand(stopAt) ?: break
                cmds.add(next)
            } else break
        }
        return Pipeline(cmds)
    }

    private fun parseCommand(stopAt: Set<OpKind>): SimpleCommand? {
        if (pos >= tokens.size) return null
        val t = tokens[pos]
        if (t is ShellToken.Op && t.kind == OpKind.LPAREN) {
            pos++
            val body = parseList(stopAt = setOf(OpKind.RPAREN))
            if (pos < tokens.size && (tokens[pos] as? ShellToken.Op)?.kind == OpKind.RPAREN) pos++
            val redirects = parseTrailingRedirects()
            return SimpleCommand(
                assignments = emptyList(),
                name = null,
                args = emptyList(),
                redirects = redirects,
                subshell = body,
            )
        }
        if (t is ShellToken.Op && t.kind == OpKind.LBRACE) {
            pos++
            val body = parseList(stopAt = setOf(OpKind.RBRACE))
            if (pos < tokens.size && (tokens[pos] as? ShellToken.Op)?.kind == OpKind.RBRACE) pos++
            val redirects = parseTrailingRedirects()
            return SimpleCommand(
                assignments = emptyList(),
                name = null,
                args = emptyList(),
                redirects = redirects,
                subshell = body,
            )
        }
        if (t is ShellToken.Op) return null

        // Simple command: collect assignments, name, args, and interleaved redirects.
        val assignments = mutableListOf<Assignment>()
        val args = mutableListOf<ShellToken.Word>()
        val redirects = mutableListOf<Redirect>()
        var name: ShellToken.Word? = null
        var seenName = false

        while (pos < tokens.size) {
            val cur = tokens[pos]
            if (cur is ShellToken.Op) {
                if (cur.kind in stopAt) break
                when (cur.kind) {
                    OpKind.PIPE, OpKind.PIPE_AMP, OpKind.AND, OpKind.OR,
                    OpKind.SEMI, OpKind.AMP, OpKind.NEWLINE, OpKind.RPAREN, OpKind.RBRACE -> break
                    OpKind.REDIR_IN, OpKind.REDIR_OUT, OpKind.REDIR_APPEND, OpKind.REDIR_FORCE_OUT,
                    OpKind.REDIR_DUP_OUT, OpKind.REDIR_HEREDOC, OpKind.REDIR_HERE_STR,
                    OpKind.REDIR_ALL_OUT, OpKind.REDIR_ALL_APPEND -> {
                        val opTok = cur
                        pos++
                        val tgt = nextWordOrNull() ?: break
                        val hereBody = if (opTok.kind == OpKind.REDIR_HEREDOC) consumeHeredocBody() else null
                        redirects.add(Redirect(opTok.kind, opTok.fd, tgt, hereBody))
                        continue
                    }
                    else -> break
                }
            }
            if (cur is ShellToken.Heredoc) { pos++; continue }
            cur as ShellToken.Word
            if (!seenName && looksLikeAssignment(cur)) {
                val eq = cur.raw.indexOf('=')
                val aName = cur.raw.substring(0, eq)
                val valueLit = cur.literal?.substring(eq + 1)
                val valueWord = ShellToken.Word(
                    raw = cur.raw.substring(eq + 1),
                    literal = valueLit,
                    substitutions = cur.substitutions,
                )
                assignments.add(Assignment(aName, valueWord))
                pos++
            } else if (!seenName) {
                name = cur
                seenName = true
                pos++
            } else {
                args.add(cur)
                pos++
            }
        }

        if (!seenName && assignments.isEmpty() && redirects.isEmpty()) return null
        return SimpleCommand(assignments, name, args, redirects)
    }

    private fun nextWordOrNull(): ShellToken.Word? {
        while (pos < tokens.size) {
            val t = tokens[pos]
            if (t is ShellToken.Word) { pos++; return t }
            if (t is ShellToken.Heredoc) { pos++; continue }
            return null
        }
        return null
    }

    private fun consumeHeredocBody(): String? {
        if (pos < tokens.size) {
            val t = tokens[pos]
            if (t is ShellToken.Heredoc) { pos++; return t.body }
        }
        return null
    }

    private fun parseTrailingRedirects(): List<Redirect> {
        val redirects = mutableListOf<Redirect>()
        while (pos < tokens.size) {
            val t = tokens[pos]
            if (t is ShellToken.Op && t.kind in REDIR_OPS) {
                val opTok = t
                pos++
                val tgt = nextWordOrNull() ?: break
                val hereBody = if (opTok.kind == OpKind.REDIR_HEREDOC) consumeHeredocBody() else null
                redirects.add(Redirect(opTok.kind, opTok.fd, tgt, hereBody))
            } else break
        }
        return redirects
    }

    private fun looksLikeAssignment(word: ShellToken.Word): Boolean {
        val raw = word.raw
        val eq = raw.indexOf('=')
        if (eq <= 0) return false
        for (i in 0 until eq) {
            val c = raw[i]
            if (!(c.isLetterOrDigit() || c == '_')) return false
        }
        return raw[0].isLetter() || raw[0] == '_'
    }

    companion object {
        private val REDIR_OPS = setOf(
            OpKind.REDIR_IN, OpKind.REDIR_OUT, OpKind.REDIR_APPEND, OpKind.REDIR_FORCE_OUT,
            OpKind.REDIR_DUP_OUT, OpKind.REDIR_HEREDOC, OpKind.REDIR_HERE_STR,
            OpKind.REDIR_ALL_OUT, OpKind.REDIR_ALL_APPEND,
        )
    }
}

// ---------- Helper queries used by protection rules ----------

/**
 * Yields every [SimpleCommand] reachable from this parsed command, recursively descending into:
 *  - subshells `( ... )` / brace groups `{ ...; }`
 *  - command substitutions `$(...)` / backticks inside word tokens
 *  - process substitutions `<(...)` / `>(...)` (captured as opaque word substitutions)
 *  - literal script bodies passed to shell interpreters via `-c` (bash, sh, zsh, dash, eval)
 */
internal fun ParsedCommand.allSimpleCommands(): Sequence<SimpleCommand> = sequence {
    for (entry in pipelines) {
        for (cmd in entry.pipeline.commands) {
            yield(cmd)
            // sudo / doas wrapper: expose the inner command too so rules match the underlying invocation.
            unwrapSudo(cmd)?.let { yield(it) }
            cmd.subshell?.let { yieldAll(it.allSimpleCommands()) }
            for (word in buildList { add(cmd.name); addAll(cmd.args); addAll(cmd.redirects.map { it.target }) }.filterNotNull()) {
                for (sub in word.substitutions) {
                    yieldAll(parseShellCommand(sub).allSimpleCommands())
                }
            }
            for (asg in cmd.assignments) {
                for (sub in asg.value.substitutions) {
                    yieldAll(parseShellCommand(sub).allSimpleCommands())
                }
            }
            // bash -c / sh -c / eval with a literal script body: re-parse.
            val nested = cmd.extractEmbeddedScripts()
            for (n in nested) yieldAll(n.allSimpleCommands())
        }
    }
}

/**
 * If [sc] is a `sudo`/`doas` invocation, returns a virtual [SimpleCommand] representing the
 * wrapped inner command so that rules can match on the real target. Returns null when the
 * wrapper is opaque or has no inner command.
 */
internal fun unwrapSudo(sc: SimpleCommand): SimpleCommand? {
    val name = sc.commandName
    if (name != "sudo" && name != "doas") return null
    val args = sc.args
    // Options that take an argument (user, group, prompt, ...).
    val pairedFlags = setOf("-u", "-g", "-p", "-C", "-r", "-t", "-U", "--user", "--group", "--prompt")
    var i = 0
    while (i < args.size) {
        val lit = args[i].literal ?: return null
        if (lit == "--") { i++; break }
        if (!lit.startsWith("-")) break
        i += if (lit in pairedFlags) 2 else 1
    }
    if (i >= args.size) return null
    val innerName = args[i]
    val innerArgs = args.subList(i + 1, args.size)
    return SimpleCommand(
        assignments = emptyList(),
        name = innerName,
        args = innerArgs,
        redirects = sc.redirects,
    )
}

/** Depth-first sum of every simple command in the chain (used for `countChainedCommands`). */
internal fun ParsedCommand.chainedCommandCount(): Int = allSimpleCommands().count()

/**
 * Returns all literal file-like argument and redirect-target strings from every simple command
 * reachable from this parsed command, including inside subshells and substitutions.
 */
internal fun ParsedCommand.allLiteralPaths(): List<String> {
    val out = mutableListOf<String>()
    for (cmd in allSimpleCommands()) {
        for (a in cmd.args) a.literal?.let { out.add(it) }
        for (r in cmd.redirects) r.target.literal?.let { out.add(it) }
    }
    return out
}

/** All redirect targets (literal values only) from every simple command. */
internal fun ParsedCommand.allLiteralRedirectTargets(): List<Pair<OpKind, String>> {
    val out = mutableListOf<Pair<OpKind, String>>()
    for (cmd in allSimpleCommands()) {
        for (r in cmd.redirects) r.target.literal?.let { out.add(r.op to it) }
    }
    return out
}

/** Returns the parsed command hiding inside a shell interpreter's `-c` arg (bash, sh, zsh, dash, eval). */
private fun SimpleCommand.extractEmbeddedScripts(): List<ParsedCommand> {
    val name = commandName ?: return emptyList()
    val isShellInterpreter = name == "bash" || name == "sh" || name == "zsh" || name == "dash"
    val isEval = name == "eval"
    if (!isShellInterpreter && !isEval) return emptyList()

    val result = mutableListOf<ParsedCommand>()
    if (isShellInterpreter) {
        // Find `-c` then take the next literal arg.
        val idx = args.indexOfFirst { it.literal == "-c" }
        if (idx >= 0 && idx + 1 < args.size) {
            args[idx + 1].literal?.let { result.add(parseShellCommand(it)) }
        }
    } else {
        // eval: concatenate all literal args and re-parse.
        val body = args.mapNotNull { it.literal }.joinToString(" ")
        if (body.isNotEmpty()) result.add(parseShellCommand(body))
    }
    return result
}

/**
 * Yields every [Pipeline] reachable from this parsed command, recursing into:
 *  - subshells `( ... )` / brace groups `{ ...; }`
 *  - command substitutions `$(...)` / backticks
 *  - process substitutions `<(...)` / `>(...)`
 *  - literal shell-interpreter bodies (`bash -c "..."`, `sh -c "..."`, `eval "..."`)
 *
 * Pipelines are yielded **as-written**. Rules that want to see through `sudo`/`doas` wrappers
 * (e.g. to detect `curl ... | sudo bash`) should call [Pipeline.effectiveCommands] on each
 * yielded pipeline. Rules that deliberately target the wrapper itself (e.g. `| sudo`) should
 * inspect `Pipeline.commands` directly.
 */
internal fun ParsedCommand.allPipelines(): Sequence<Pipeline> = sequence {
    for (entry in pipelines) {
        yield(entry.pipeline)
        for (cmd in entry.pipeline.commands) {
            cmd.subshell?.let { yieldAll(it.allPipelines()) }
            val words = (listOfNotNull(cmd.name) + cmd.args + cmd.redirects.map { it.target })
            for (w in words) {
                for (sub in w.substitutions) yieldAll(parseShellCommand(sub).allPipelines())
            }
            for (asg in cmd.assignments) {
                for (sub in asg.value.substitutions) yieldAll(parseShellCommand(sub).allPipelines())
            }
            for (body in cmd.embeddedShellBodies()) {
                yieldAll(parseShellCommand(body).allPipelines())
            }
        }
    }
}

/**
 * Returns the pipeline's commands with each `sudo`/`doas` wrapper replaced by the inner command
 * it invokes. Useful for structural pipeline checks that shouldn't be fooled by a privilege
 * wrapper (e.g. `curl ... | sudo bash`). Commands that are not wrappers pass through unchanged.
 */
internal fun Pipeline.effectiveCommands(): List<SimpleCommand> =
    commands.map { unwrapSudo(it) ?: it }

/** Returns literal shell script bodies for `bash -c`, `sh -c`, `zsh -c`, `dash -c`, and `eval`. */
internal fun SimpleCommand.embeddedShellBodies(): List<String> {
    val name = commandName ?: return emptyList()
    return when (name) {
        "bash", "sh", "zsh", "dash" -> {
            val idx = args.indexOfFirst { it.literal == "-c" }
            if (idx >= 0 && idx + 1 < args.size) listOfNotNull(args[idx + 1].literal) else emptyList()
        }
        "eval" -> {
            val body = args.mapNotNull { it.literal }.joinToString(" ")
            if (body.isEmpty()) emptyList() else listOf(body)
        }
        else -> emptyList()
    }
}

/** Convenience: tokenize + parse a source string into a [ParsedCommand]. */
internal fun parseShellCommand(source: String): ParsedCommand {
    val tokens = ShellTokenizer(source).tokenize()
    return ShellParser(tokens).parse()
}
