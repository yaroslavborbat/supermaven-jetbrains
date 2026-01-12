package supermaven.supermavenjetbrains.completion

import supermaven.supermavenjetbrains.binary.CompletionParams
import supermaven.supermavenjetbrains.binary.ResponseItem
import supermaven.supermavenjetbrains.binary.TextCompletion
import supermaven.supermavenjetbrains.binary.AnyCompletion
import supermaven.supermavenjetbrains.binary.ResponseItemKind

object Textual {
    private fun isWhitespace(char: Char): Boolean {
        return char == ' ' || char == '\t' || char == '\n' || char == '\r'
    }

    private fun trimStart(s: String): String {
        return s.dropWhile { isWhitespace(it) }
    }

    private fun trimEnd(s: String): String {
        return s.dropLastWhile { isWhitespace(it) }
    }

    private fun trim(s: String): String {
        return s.trim()
    }

    private fun findFirstNonEmptyNewline(s: String): Int? {
        var seenNonWhitespace = false
        for (i in s.indices) {
            val char = s[i]
            if (char == '\n' && seenNonWhitespace) {
                return i
            } else if (!isWhitespace(char)) {
                seenNonWhitespace = true
            }
        }
        return null
    }

    private fun findLastNewline(s: String): Int? {
        for (i in s.length - 1 downTo 0) {
            if (s[i] == '\n') {
                return i
            }
        }
        return null
    }

    private fun hasLeadingNewline(s: String): Boolean {
        for (char in s) {
            if (char == '\n') {
                return true
            } else if (!isWhitespace(char)) {
                return false
            }
        }
        return false
    }

    private fun isAllDust(line: String, dustStrings: List<String>): Boolean {
        var lineHolding = line
        while (lineHolding.isNotEmpty()) {
            val originalLength = lineHolding.length
            lineHolding = trimStart(lineHolding)
            for (dustString in dustStrings) {
                if (lineHolding.startsWith(dustString)) {
                    lineHolding = lineHolding.substring(dustString.length)
                }
            }
            if (lineHolding.length == originalLength) {
                return false
            }
        }
        return true
    }

    private fun canDelete(params: CompletionParams): Boolean {
        val trimmed = trim(params.lineBeforeCursor)
        if (trimmed.isEmpty() && !isAllDust(params.lineAfterCursor, params.dustStrings)) {
            return false
        }
        return true
    }

    private fun finishCompletion(
        output: String,
        dedent: String,
        params: CompletionParams,
        fullCompletionIndex: Int?
    ): TextCompletion? {
        if (!canDelete(params)) {
            return null
        }
        val hasTrailingCharacters = trim(params.lineAfterCursor).isNotEmpty()
        val outputTrimmed = trim(output)
        if (outputTrimmed.isEmpty()) {
            return null
        }

        if (hasLeadingNewline(output)) {
            val firstNonEmptyLine = findFirstNonEmptyNewline(output)
            val lastNewline = findLastNewline(output)
            if (firstNonEmptyLine != null && lastNewline != null) {
                val text = output.substring(0, lastNewline)
                return TextCompletion(
                    text = text,
                    dedent = dedent,
                    shouldRetry = null,
                    isIncomplete = false,
                    sourceStateId = params.sourceStateId,
                    completionIndex = fullCompletionIndex
                )
            }
            return null
        } else {
            val index = findFirstNonEmptyNewline(output)
            if (index != null) {
                val text = output.substring(0, index)
                return TextCompletion(
                    text = text,
                    dedent = dedent,
                    shouldRetry = true,
                    isIncomplete = false,
                    sourceStateId = params.sourceStateId,
                    completionIndex = null
                )
            }
            if (params.canRetry) {
                if (hasTrailingCharacters) {
                    return TextCompletion(
                        text = output,
                        dedent = dedent,
                        shouldRetry = true,
                        isIncomplete = true,
                        sourceStateId = params.sourceStateId,
                        completionIndex = null
                    )
                }
                if (trim(params.lineBeforeCursor).isEmpty()) {
                    return TextCompletion(
                        text = output,
                        dedent = dedent,
                        shouldRetry = true,
                        isIncomplete = true,
                        sourceStateId = params.sourceStateId,
                        completionIndex = null
                    )
                }
                return TextCompletion(
                    text = output,
                    dedent = dedent,
                    shouldRetry = true,
                    isIncomplete = true,
                    sourceStateId = params.sourceStateId,
                    completionIndex = null
                )
            }

            if (hasTrailingCharacters) {
                return null
            }
            if (trim(params.lineBeforeCursor).isEmpty()) {
                return null
            }
            if (params.canShowPartialLine) {
                return TextCompletion(
                    text = output,
                    dedent = dedent,
                    shouldRetry = true,
                    isIncomplete = true,
                    sourceStateId = params.sourceStateId,
                    completionIndex = null
                )
            }
            return null
        }
    }

    private fun forceComplete(
        output: String,
        dedent: String,
        params: CompletionParams,
        completionIndex: Int
    ): TextCompletion {
        val result = finishCompletion(output + "\n", dedent, params, completionIndex)
        return result ?: TextCompletion(
            text = "",
            dedent = "",
            isIncomplete = false,
            sourceStateId = params.sourceStateId,
            completionIndex = completionIndex
        )
    }

    fun deriveCompletion(completion: List<ResponseItem>, params: CompletionParams): AnyCompletion? {
        var output = ""
        val deleteLines = mutableListOf<String>()
        var dedent = ""

        for ((completionIndex, responseItem) in completion.withIndex()) {

            if (responseItem.kind == ResponseItemKind.End) {
                return if (output.contains("\n")) {
                    forceComplete(output, dedent, params, completionIndex)
                } else {
                    null
                }
            }

            if (deleteLines.isNotEmpty() && responseItem.kind != ResponseItemKind.Delete) {
                return supermaven.supermavenjetbrains.binary.DeleteCompletion(
                    lines = deleteLines,
                    completionIndex = completionIndex,
                    sourceStateId = params.sourceStateId,
                    isIncomplete = false
                )
            }

            when (responseItem.kind) {
                ResponseItemKind.Text -> {
                    output += (responseItem.text ?: "")
                }
                ResponseItemKind.Barrier, ResponseItemKind.FinishEdit -> {
                    if (trim(output).isNotEmpty()) {
                        return forceComplete(output, dedent, params, completionIndex)
                    }
                }
                ResponseItemKind.Dedent -> {
                    dedent += (responseItem.text ?: "")
                }
                ResponseItemKind.Jump -> {
                    if (trim(output).isNotEmpty()) {
                        return supermaven.supermavenjetbrains.binary.JumpCompletion(
                            fileName = responseItem.fileName ?: "",
                            lineNumber = responseItem.lineNumber ?: 0,
                            verify = responseItem.verify,
                            precede = responseItem.precede ?: emptyList(),
                            follow = responseItem.follow ?: emptyList(),
                            isCreateFile = responseItem.isCreateFile ?: false,
                            isIncomplete = false,
                            sourceStateId = params.sourceStateId,
                            completionIndex = completionIndex + 1
                        )
                    } else {
                        break
                    }
                }
                ResponseItemKind.Delete -> {
                    if (trim(output).isNotEmpty()) {
                        return forceComplete(output, dedent, params, completionIndex)
                    }
                    val followingLine = params.getFollowingLine(deleteLines.size)
                    if (trimEnd(responseItem.text ?: "") == trimEnd(followingLine)) {
                        deleteLines.add(followingLine)
                    }
                }
                ResponseItemKind.Skip -> {
                    if (trim(output).isNotEmpty()) {
                        return forceComplete(output, dedent, params, completionIndex)
                    }
                    return supermaven.supermavenjetbrains.binary.SkipCompletion(
                        n = responseItem.n ?: 0,
                        completionIndex = completionIndex + 1,
                        sourceStateId = params.sourceStateId,
                        isIncomplete = false
                    )
                }

                else -> {}
            }
        }

        output = trimEnd(output)
        val index = findFirstNonEmptyNewline(output)
        if (index != null) {
            output = output.substring(0, index)
        }

        return finishCompletion(output, dedent, params, null)
    }
}
