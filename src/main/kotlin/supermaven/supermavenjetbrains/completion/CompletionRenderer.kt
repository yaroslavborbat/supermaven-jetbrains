package supermaven.supermavenjetbrains.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D

data class CompletionInstance(
    val completionText: String,
    val priorDelete: Int,
    val isActive: Boolean
)

class CompletionRenderer {
    private val inlays = mutableMapOf<Editor, MutableList<Inlay<*>>>()
    private val completionInstances = mutableMapOf<Editor, CompletionInstance>()

    fun render(editor: Editor, completionText: String, priorDelete: Int = 0) {
        clear(editor)
        
        if (completionText.isEmpty()) return
        
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val columnInLine = caretOffset - lineStartOffset
        
        // Split completion into first line and remaining lines
        val firstNewline = completionText.indexOf('\n')
        val firstLine = if (firstNewline >= 0) completionText.substring(0, firstNewline) else completionText
        val remainingLines = if (firstNewline >= 0) completionText.substring(firstNewline + 1) else ""
        
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, document.getLineEndOffset(lineNumber)))
        val lineBeforeCursor = if (columnInLine > 0) lineText.substring(0, columnInLine) else ""
        val lineAfterCursor = if (columnInLine < lineText.length) lineText.substring(columnInLine) else ""
        
        // Check if completion should be active (similar to nvim logic)
        val isActive = shouldCompletionBeActive(completionText, lineBeforeCursor, firstLine)
        
        // Check if we should show floating completion (when there's text after cursor)
        val isFloating = lineAfterCursor.isNotEmpty() && !firstLine.contains(lineAfterCursor)
        
        // Store completion instance for acceptance
        completionInstances[editor] = CompletionInstance(
            completionText = completionText,
            priorDelete = priorDelete,
            isActive = isActive
        )
        
        val inlayModel = editor.inlayModel
        // Use bright red color for completion to make it very visible
        val textColor = JBColor.GRAY.brighter()

        if (isFloating && firstLine.isNotEmpty()) {
            // Show floating completion at end of line
            val lineEndOffset = document.getLineEndOffset(lineNumber)
            val inlay = inlayModel.addInlineElement(lineEndOffset, true, SimpleTextRenderer(firstLine, textColor))
            inlay?.let { getInlays(editor).add(it) }
        } else if (firstLine.isNotEmpty()) {
            // Show inline completion at cursor (relatesToPrecedingText = true keeps cursor before inlay)
            val inlay = inlayModel.addInlineElement(caretOffset, true, SimpleTextRenderer(firstLine, textColor))
            inlay?.let { getInlays(editor).add(it) }
            
            // Add remaining lines as block inlays
            if (remainingLines.isNotEmpty()) {
                val lines = remainingLines.split('\n')
                val totalLines = document.lineCount
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        val targetLineNumber = lineNumber + index + 1
                        // Check if target line exists in document
                        if (targetLineNumber < totalLines) {
                            val nextLineOffset = document.getLineStartOffset(targetLineNumber)
                            val blockInlay = inlayModel.addBlockElement(
                                nextLineOffset,
                                false,
                                false,
                                0,
                                SimpleTextRenderer(line, textColor)
                            )
                            blockInlay?.let { getInlays(editor).add(it) }
                        } else {
                            // If line doesn't exist, add block inlay at end of document
                            val documentEndOffset = document.textLength
                            val blockInlay = inlayModel.addBlockElement(
                                documentEndOffset,
                                false,
                                false,
                                0,
                                SimpleTextRenderer(line, textColor)
                            )
                            blockInlay?.let { getInlays(editor).add(it) }
                        }
                    }
                }
            }
        }
    }
    
    private class SimpleTextRenderer(
        private val text: String,
        private val color: java.awt.Color
    ) : EditorCustomElementRenderer {
        
        private fun getFont(editor: Editor): Font {
            val baseFont = editor.contentComponent.font
            return baseFont.deriveFont(Font.ITALIC, baseFont.size.toFloat())
        }
        
        override fun paint(
            inlay: Inlay<*>,
            g: Graphics,
            targetRegion: Rectangle,
            textAttributes: TextAttributes
        ) {
            g.font = getFont(inlay.editor)
            g.color = color
            g.drawString(text, targetRegion.x, targetRegion.y + inlay.editor.ascent)
        }
        
        override fun paint(
            inlay: Inlay<*>,
            g: Graphics2D,
            targetRegion: Rectangle2D,
            textAttributes: TextAttributes
        ) {
            g.font = getFont(inlay.editor)
            g.color = color
            g.drawString(text, targetRegion.x.toFloat(), (targetRegion.y + inlay.editor.ascent).toFloat())
        }
        
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val font = getFont(inlay.editor)
            val metrics = inlay.editor.contentComponent.getFontMetrics(font)
            return metrics.stringWidth(text)
        }
    }

    fun clear(editor: Editor) {
        getInlays(editor).forEach { it.dispose() }
        getInlays(editor).clear()
        completionInstances.remove(editor)
    }

    private fun getInlays(editor: Editor): MutableList<Inlay<*>> {
        return inlays.getOrPut(editor) { mutableListOf() }
    }
    
    fun clear() {
        inlays.keys.forEach { clear(it) }
        inlays.clear()
        completionInstances.clear()
    }
    
    fun hasActiveCompletion(editor: Editor): Boolean {
        val instance = completionInstances[editor]
        return instance != null && instance.isActive && instance.completionText.isNotEmpty()
    }
    
    fun acceptCompletion(editor: Editor, acceptWordOnly: Boolean): Boolean {
        val instance = completionInstances[editor] ?: return false
        if (!instance.isActive || instance.completionText.isEmpty()) {
            return false
        }
        
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val columnInLine = caretOffset - lineStartOffset
        
        var completionText = instance.completionText
        if (acceptWordOnly) {
            // Accept only until next word boundary
            val spaceIndex = completionText.indexOf(' ')
            val newlineIndex = completionText.indexOf('\n')
            when {
                spaceIndex >= 0 && (newlineIndex < 0 || spaceIndex < newlineIndex) -> {
                    completionText = completionText.substring(0, spaceIndex)
                }
                newlineIndex >= 0 -> {
                    completionText = completionText.substring(0, newlineIndex)
                }
            }
        }
        
        val startChar = maxOf(0, columnInLine - instance.priorDelete)
        val endOffset = document.getLineEndOffset(lineNumber)
        
        // Clear completion first
        clear(editor)
        
        val project = editor.project ?: return false
        
        // Apply text edit in write command action
        WriteCommandAction.runWriteCommandAction(project) {
            val startOffset = lineStartOffset + startChar
            
            document.replaceString(startOffset, endOffset, completionText)
            
            // Calculate new cursor position (handle multi-line completion)
            val lines = completionText.split('\n')
            val lastLine = lines.lastOrNull() ?: ""
            val newLineNumber = lineNumber + lines.size - 1
            val newColumnInLine = if (lines.size > 1) {
                lastLine.length
            } else {
                startChar + completionText.length
            }
            
            val newLineStartOffset = document.getLineStartOffset(newLineNumber)
            val newOffset = newLineStartOffset + newColumnInLine
            editor.caretModel.moveToOffset(newOffset)
        }
        
        return true
    }
    
    private fun shouldCompletionBeActive(completionText: String, lineBeforeCursor: String, firstLine: String): Boolean {
        // Similar to nvim logic
        if (completionText.isEmpty() || !completionText.first().isWhitespace()) {
            return true
        }
        
        if (lineBeforeCursor.trim().isNotEmpty()) {
            return true
        }
        
        if (firstLine.trim().isEmpty()) {
            return true
        }
        
        return false
    }
}
