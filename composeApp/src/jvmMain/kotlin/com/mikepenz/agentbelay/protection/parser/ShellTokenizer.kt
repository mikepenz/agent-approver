package com.mikepenz.agentbelay.protection.parser

/**
 * Minimal bash-compatible tokenizer used by protection rules. Goal is structural fidelity for
 * security analysis, not full shell semantics: we recognize quoting (single/double/ANSI-C),
 * escapes, variable/command substitution, operators, redirects, and heredoc blocks well enough
 * that any word whose literal value cannot be recovered is marked `opaque` and rules can fail
 * closed on it.
 */
internal enum class OpKind {
    PIPE,            // |
    PIPE_AMP,        // |&
    AND,             // &&
    OR,              // ||
    SEMI,            // ;
    AMP,             // & (background)
    NEWLINE,         // \n
    LPAREN,          // (
    RPAREN,          // )
    LBRACE,          // { (reserved word)
    RBRACE,          // } (reserved word)
    REDIR_IN,        // <
    REDIR_OUT,       // >
    REDIR_APPEND,    // >>
    REDIR_FORCE_OUT, // >|
    REDIR_DUP_OUT,   // >&
    REDIR_HEREDOC,   // <<  /  <<-
    REDIR_HERE_STR,  // <<<
    REDIR_ALL_OUT,   // &>
    REDIR_ALL_APPEND,// &>>
    REDIR_PROC_IN,   // <(...)  recognized as inline
    REDIR_PROC_OUT,  // >(...)  recognized as inline
}

internal sealed class ShellToken {
    /** A word-expanded argument. If [literal] is null, the final value cannot be determined. */
    data class Word(
        val raw: String,
        val literal: String?,
        /** Raw source bodies of `$(...)` / backtick substitutions contained in this word. */
        val substitutions: List<String> = emptyList(),
        /** True if the first character of the raw source is a single quote or double quote. */
        val startsQuoted: Boolean = false,
    ) : ShellToken() {
        val isOpaque: Boolean get() = literal == null
    }

    data class Op(val kind: OpKind, val raw: String, val fd: Int? = null) : ShellToken()

    /** Body of a heredoc that followed a REDIR_HEREDOC operator (attached to the preceding redirect). */
    data class Heredoc(val delimiter: String, val body: String) : ShellToken()
}

internal class ShellTokenizer(private val source: String) {
    private var pos = 0
    private val tokens = mutableListOf<ShellToken>()

    /** Heredoc delimiters we still need to scan for (FIFO — first `<<` takes the next line block). */
    private data class PendingHeredoc(val delimiter: String, val stripTabs: Boolean, val quoted: Boolean)
    private val pendingHeredocs = ArrayDeque<PendingHeredoc>()

    fun tokenize(): List<ShellToken> {
        while (pos < source.length) {
            val c = source[pos]
            when {
                c == ' ' || c == '\t' -> pos++
                c == '\\' && pos + 1 < source.length && source[pos + 1] == '\n' -> pos += 2
                c == '\n' -> {
                    if (pendingHeredocs.isNotEmpty()) {
                        pos++ // consume the newline that terminated the heredoc-introducing line
                        drainHeredocs()
                        emit(ShellToken.Op(OpKind.NEWLINE, "\n"))
                    } else {
                        emit(ShellToken.Op(OpKind.NEWLINE, "\n"))
                        pos++
                    }
                }
                c == '#' && isWordBoundary() -> skipComment()
                c == '|' -> readPipe()
                c == '&' -> readAmp()
                c == ';' -> { emit(ShellToken.Op(OpKind.SEMI, ";")); pos++ }
                c == '(' -> { emit(ShellToken.Op(OpKind.LPAREN, "(")); pos++ }
                c == ')' -> { emit(ShellToken.Op(OpKind.RPAREN, ")")); pos++ }
                c == '<' -> readLeftRedir(null)
                c == '>' -> readRightRedir(null)
                c.isDigit() && looksLikeFdRedir() -> readFdRedir()
                else -> {
                    val word = readWord() ?: continue
                    // Brace-group reserved words are only significant at command position.
                    if (word.literal == "{" && !word.startsQuoted && isCommandPosition()) {
                        emit(ShellToken.Op(OpKind.LBRACE, "{"))
                    } else if (word.literal == "}" && !word.startsQuoted && isCommandPosition()) {
                        emit(ShellToken.Op(OpKind.RBRACE, "}"))
                    } else {
                        emit(word)
                    }
                }
            }
        }
        // If any heredocs were opened but never closed (unterminated), drop their bodies silently.
        return tokens
    }

    private fun emit(t: ShellToken) {
        tokens.add(t)
    }

    /** Are we at a position where a word would start a new command (vs. continue an argument list)? */
    private fun isCommandPosition(): Boolean {
        for (i in tokens.indices.reversed()) {
            return when (val t = tokens[i]) {
                is ShellToken.Op -> when (t.kind) {
                    OpKind.PIPE, OpKind.PIPE_AMP, OpKind.AND, OpKind.OR,
                    OpKind.SEMI, OpKind.AMP, OpKind.NEWLINE,
                    OpKind.LPAREN, OpKind.LBRACE, OpKind.RPAREN, OpKind.RBRACE -> true
                    else -> false
                }
                is ShellToken.Word -> false
                is ShellToken.Heredoc -> false
            }
        }
        return true
    }

    private fun isWordBoundary(): Boolean {
        if (pos == 0) return true
        val prev = source[pos - 1]
        return prev == ' ' || prev == '\t' || prev == '\n' || prev == ';' || prev == '|' || prev == '&'
    }

    private fun skipComment() {
        while (pos < source.length && source[pos] != '\n') pos++
    }

    private fun readPipe() {
        // `||` > `|&` > `|`
        if (pos + 1 < source.length && source[pos + 1] == '|') {
            emit(ShellToken.Op(OpKind.OR, "||"))
            pos += 2
        } else if (pos + 1 < source.length && source[pos + 1] == '&') {
            emit(ShellToken.Op(OpKind.PIPE_AMP, "|&"))
            pos += 2
        } else {
            emit(ShellToken.Op(OpKind.PIPE, "|"))
            pos++
        }
    }

    private fun readAmp() {
        if (pos + 1 < source.length && source[pos + 1] == '&') {
            emit(ShellToken.Op(OpKind.AND, "&&"))
            pos += 2
        } else if (pos + 1 < source.length && source[pos + 1] == '>') {
            if (pos + 2 < source.length && source[pos + 2] == '>') {
                emit(ShellToken.Op(OpKind.REDIR_ALL_APPEND, "&>>"))
                pos += 3
            } else {
                emit(ShellToken.Op(OpKind.REDIR_ALL_OUT, "&>"))
                pos += 2
            }
        } else {
            emit(ShellToken.Op(OpKind.AMP, "&"))
            pos++
        }
    }

    private fun looksLikeFdRedir(): Boolean {
        // A leading digit run followed by `<` or `>` with no intervening space.
        var i = pos
        while (i < source.length && source[i].isDigit()) i++
        if (i == pos) return false
        return i < source.length && (source[i] == '<' || source[i] == '>')
    }

    private fun readFdRedir() {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        val fd = source.substring(start, pos).toInt()
        when (source[pos]) {
            '>' -> readRightRedir(fd)
            '<' -> readLeftRedir(fd)
        }
    }

    private fun readLeftRedir(fd: Int?) {
        // `<<<`, `<<-`, `<<`, `<(`, `<`
        when {
            pos + 2 < source.length && source[pos + 1] == '<' && source[pos + 2] == '<' -> {
                emit(ShellToken.Op(OpKind.REDIR_HERE_STR, "<<<", fd))
                pos += 3
            }
            pos + 2 < source.length && source[pos + 1] == '<' && source[pos + 2] == '-' -> {
                emit(ShellToken.Op(OpKind.REDIR_HEREDOC, "<<-", fd))
                pos += 3
                queueHeredoc(stripTabs = true)
            }
            pos + 1 < source.length && source[pos + 1] == '<' -> {
                emit(ShellToken.Op(OpKind.REDIR_HEREDOC, "<<", fd))
                pos += 2
                queueHeredoc(stripTabs = false)
            }
            pos + 1 < source.length && source[pos + 1] == '(' -> {
                // Process substitution <(...) — capture the inner source and emit as a Word marked opaque.
                val body = captureBalanced('(', ')') ?: return
                emit(
                    ShellToken.Word(
                        raw = "<($body)",
                        literal = null,
                        substitutions = listOf(body),
                    )
                )
            }
            else -> { emit(ShellToken.Op(OpKind.REDIR_IN, "<", fd)); pos++ }
        }
    }

    private fun readRightRedir(fd: Int?) {
        when {
            pos + 1 < source.length && source[pos + 1] == '>' -> {
                emit(ShellToken.Op(OpKind.REDIR_APPEND, ">>", fd))
                pos += 2
            }
            pos + 1 < source.length && source[pos + 1] == '|' -> {
                emit(ShellToken.Op(OpKind.REDIR_FORCE_OUT, ">|", fd))
                pos += 2
            }
            pos + 1 < source.length && source[pos + 1] == '&' -> {
                emit(ShellToken.Op(OpKind.REDIR_DUP_OUT, ">&", fd))
                pos += 2
            }
            pos + 1 < source.length && source[pos + 1] == '(' -> {
                val body = captureBalanced('(', ')') ?: return
                emit(
                    ShellToken.Word(
                        raw = ">($body)",
                        literal = null,
                        substitutions = listOf(body),
                    )
                )
            }
            else -> { emit(ShellToken.Op(OpKind.REDIR_OUT, ">", fd)); pos++ }
        }
    }

    /** Called when seeing `<(`, `>(`, or after `$(`. Skips the opening paren, returns the inner body. */
    private fun captureBalanced(open: Char, close: Char): String? {
        // pos points at < or > or $; skip the opening run up to and including `(`.
        while (pos < source.length && source[pos] != open) pos++
        if (pos >= source.length) return null
        pos++ // skip `(`
        val start = pos
        var depth = 1
        while (pos < source.length && depth > 0) {
            val c = source[pos]
            when (c) {
                '\\' -> pos += if (pos + 1 < source.length) 2 else 1
                '\'' -> { pos++; while (pos < source.length && source[pos] != '\'') pos++; if (pos < source.length) pos++ }
                '"' -> {
                    pos++
                    while (pos < source.length && source[pos] != '"') {
                        if (source[pos] == '\\' && pos + 1 < source.length) pos += 2 else pos++
                    }
                    if (pos < source.length) pos++
                }
                open -> { depth++; pos++ }
                close -> { depth--; if (depth == 0) break else pos++ }
                else -> pos++
            }
        }
        val end = pos
        if (pos < source.length) pos++ // skip `)`
        return source.substring(start, end)
    }

    private fun queueHeredoc(stripTabs: Boolean) {
        // The next word is the delimiter; we'll peek without consuming operator splitting.
        // Skip inline whitespace, then read the delimiter word.
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) pos++
        val delimWord = readWord() ?: return
        // Determine if delimiter was quoted (then body is raw, not expanded — we don't care about expansion anyway).
        val raw = delimWord.raw
        val quoted = raw.startsWith("'") || raw.startsWith("\"")
        val delim = delimWord.literal ?: raw.trim('\'', '"', '\\')
        emit(delimWord)
        pendingHeredocs.addLast(PendingHeredoc(delim, stripTabs, quoted))
    }

    private fun drainHeredocs() {
        // Consume the input line-by-line, matching each against the front-of-queue delimiter.
        while (pendingHeredocs.isNotEmpty()) {
            val spec = pendingHeredocs.removeFirst()
            val body = StringBuilder()
            while (pos < source.length) {
                // Read one line.
                val lineStart = pos
                while (pos < source.length && source[pos] != '\n') pos++
                val line = source.substring(lineStart, pos)
                val check = if (spec.stripTabs) line.trimStart('\t') else line
                if (check == spec.delimiter) {
                    if (pos < source.length) pos++ // consume newline after delimiter line
                    break
                }
                body.append(line)
                if (pos < source.length) {
                    body.append('\n')
                    pos++
                }
            }
            emit(ShellToken.Heredoc(spec.delimiter, body.toString()))
        }
    }

    /** Reads a single word token. Returns null if nothing was consumed (shouldn't happen at call sites). */
    private fun readWord(): ShellToken.Word? {
        if (pos >= source.length) return null
        val startPos = pos
        val raw = StringBuilder()
        val lit = StringBuilder()
        var opaque = false
        val subs = mutableListOf<String>()
        var started = false
        var startedQuoted = false

        loop@ while (pos < source.length) {
            val c = source[pos]
            when {
                !started && (c == '\'' || c == '"') -> startedQuoted = true
                else -> {}
            }
            when {
                c == ' ' || c == '\t' || c == '\n' -> break@loop
                c == '|' || c == '&' || c == ';' || c == '(' || c == ')' || c == '<' || c == '>' -> break@loop
                c == '\\' -> {
                    if (pos + 1 < source.length) {
                        val nxt = source[pos + 1]
                        if (nxt == '\n') { pos += 2; started = true; continue@loop }
                        raw.append(c).append(nxt)
                        lit.append(nxt)
                        pos += 2
                    } else {
                        raw.append(c); lit.append(c); pos++
                    }
                }
                c == '\'' -> {
                    raw.append(c); pos++
                    while (pos < source.length && source[pos] != '\'') {
                        raw.append(source[pos]); lit.append(source[pos]); pos++
                    }
                    if (pos < source.length) { raw.append('\''); pos++ }
                }
                c == '"' -> {
                    raw.append(c); pos++
                    while (pos < source.length && source[pos] != '"') {
                        val dc = source[pos]
                        when {
                            dc == '\\' && pos + 1 < source.length -> {
                                val next = source[pos + 1]
                                raw.append(dc).append(next)
                                if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                                    if (next != '\n') lit.append(next)
                                } else {
                                    lit.append(dc).append(next)
                                }
                                pos += 2
                            }
                            dc == '$' -> {
                                if (!consumeDollar(raw, lit, subs, inDoubleQuotes = true)) {
                                    // literal `$`
                                    raw.append(dc); lit.append(dc); pos++
                                } else {
                                    opaque = true
                                }
                            }
                            dc == '`' -> {
                                val body = readBacktickBody(raw)
                                subs.add(body)
                                opaque = true
                            }
                            else -> { raw.append(dc); lit.append(dc); pos++ }
                        }
                    }
                    if (pos < source.length) { raw.append('"'); pos++ }
                }
                c == '$' -> {
                    if (pos + 1 < source.length && source[pos + 1] == '\'') {
                        // ANSI-C quoting — decode a limited set of escapes into literal.
                        raw.append('$').append('\''); pos += 2
                        while (pos < source.length && source[pos] != '\'') {
                            val dc = source[pos]
                            if (dc == '\\' && pos + 1 < source.length) {
                                val next = source[pos + 1]
                                raw.append(dc).append(next)
                                decodeAnsiCEscape(next)?.let { lit.append(it) }
                                pos += 2
                            } else {
                                raw.append(dc); lit.append(dc); pos++
                            }
                        }
                        if (pos < source.length) { raw.append('\''); pos++ }
                    } else if (pos + 1 < source.length && source[pos + 1] == '"') {
                        // $"..." — locale string, treat as double-quoted literal.
                        raw.append('$'); pos++
                        // fall through to double-quote branch by continuing the loop at `"`.
                        continue@loop
                    } else if (!consumeDollar(raw, lit, subs, inDoubleQuotes = false)) {
                        // literal `$`
                        raw.append(c); lit.append(c); pos++
                    } else {
                        opaque = true
                    }
                }
                c == '`' -> {
                    val body = readBacktickBody(raw)
                    subs.add(body)
                    opaque = true
                }
                c == '#' && !started -> break@loop // comment at word start
                else -> {
                    raw.append(c); lit.append(c); pos++
                }
            }
            started = true
        }

        if (pos == startPos) return null
        return ShellToken.Word(
            raw = raw.toString(),
            literal = if (opaque) null else lit.toString(),
            substitutions = subs.toList(),
            startsQuoted = startedQuoted,
        )
    }

    /**
     * Called at a `$` character. Consumes `$VAR`, `${...}`, `$(...)`, `$((...))`.
     * Returns true if a substitution/variable was recognized (caller marks the word opaque),
     * false if the `$` is literal (caller should consume it as literal).
     */
    private fun consumeDollar(
        raw: StringBuilder,
        lit: StringBuilder,
        subs: MutableList<String>,
        inDoubleQuotes: Boolean,
    ): Boolean {
        val next = source.getOrNull(pos + 1) ?: return false
        when {
            next == '(' -> {
                if (source.getOrNull(pos + 2) == '(') {
                    // $(( arithmetic )) — skip balanced doubled parens.
                    raw.append("$((")
                    pos += 3
                    var depth = 2
                    while (pos < source.length && depth > 0) {
                        val c = source[pos]
                        if (c == '(') { depth++ }
                        else if (c == ')') { depth--; if (depth == 0) break }
                        raw.append(c)
                        pos++
                    }
                    if (pos < source.length) { raw.append(')'); pos++ }
                    return true
                }
                // $(...) — capture body via balanced paren scan.
                raw.append("$(")
                pos += 2
                val start = pos
                var depth = 1
                while (pos < source.length && depth > 0) {
                    val c = source[pos]
                    when (c) {
                        '\\' -> {
                            raw.append(c)
                            pos++
                            if (pos < source.length) { raw.append(source[pos]); pos++ }
                        }
                        '\'' -> {
                            raw.append(c); pos++
                            while (pos < source.length && source[pos] != '\'') { raw.append(source[pos]); pos++ }
                            if (pos < source.length) { raw.append('\''); pos++ }
                        }
                        '"' -> {
                            raw.append(c); pos++
                            while (pos < source.length && source[pos] != '"') {
                                if (source[pos] == '\\' && pos + 1 < source.length) {
                                    raw.append(source[pos]).append(source[pos + 1]); pos += 2
                                } else { raw.append(source[pos]); pos++ }
                            }
                            if (pos < source.length) { raw.append('"'); pos++ }
                        }
                        '(' -> { depth++; raw.append(c); pos++ }
                        ')' -> { depth--; if (depth == 0) break else { raw.append(c); pos++ } }
                        else -> { raw.append(c); pos++ }
                    }
                }
                val body = source.substring(start, pos)
                if (pos < source.length) { raw.append(')'); pos++ }
                subs.add(body)
                return true
            }
            next == '{' -> {
                raw.append('$'); raw.append('{')
                pos += 2
                var depth = 1
                while (pos < source.length && depth > 0) {
                    val c = source[pos]
                    if (c == '{') depth++
                    else if (c == '}') { depth--; if (depth == 0) break }
                    raw.append(c)
                    pos++
                }
                if (pos < source.length) { raw.append('}'); pos++ }
                return true
            }
            next.isLetter() || next == '_' || next.isDigit() || next == '@' || next == '*' || next == '#' || next == '?' || next == '!' || next == '-' -> {
                raw.append('$')
                pos++
                // $1 style — single char; $VAR style — full identifier
                if (source[pos].isLetter() || source[pos] == '_') {
                    while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) {
                        raw.append(source[pos]); pos++
                    }
                } else {
                    raw.append(source[pos]); pos++
                }
                return true
            }
            else -> return false
        }
    }

    /** Reads a backtick substitution body, appending the raw text to [raw]. */
    private fun readBacktickBody(raw: StringBuilder): String {
        raw.append('`'); pos++
        val body = StringBuilder()
        while (pos < source.length && source[pos] != '`') {
            if (source[pos] == '\\' && pos + 1 < source.length) {
                val nxt = source[pos + 1]
                raw.append(source[pos]).append(nxt)
                if (nxt != '`' && nxt != '\\' && nxt != '$') body.append(source[pos])
                body.append(nxt)
                pos += 2
            } else {
                body.append(source[pos]); raw.append(source[pos]); pos++
            }
        }
        if (pos < source.length) { raw.append('`'); pos++ }
        return body.toString()
    }

    private fun decodeAnsiCEscape(next: Char): String? {
        return when (next) {
            'n' -> "\n"
            't' -> "\t"
            'r' -> "\r"
            '\\' -> "\\"
            '\'' -> "'"
            '"' -> "\""
            '0' -> "\u0000"
            'a' -> "\u0007"
            'b' -> "\b"
            'e', 'E' -> "\u001B"
            'f' -> "\u000C"
            'v' -> "\u000B"
            else -> null
        }
    }
}
