package supermaven.supermavenjetbrains

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import supermaven.supermavenjetbrains.binary.BinaryFetcher
import supermaven.supermavenjetbrains.binary.BinaryHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.EditorEventMulticaster
import java.util.Timer
import java.util.TimerTask
import supermaven.supermavenjetbrains.binary.CursorUpdateMessage
import supermaven.supermavenjetbrains.binary.FileUpdateMessage
import supermaven.supermavenjetbrains.completion.CompletionRenderer
import supermaven.supermavenjetbrains.completion.Textual
import supermaven.supermavenjetbrains.binary.LastState
import supermaven.supermavenjetbrains.binary.DocumentState
import supermaven.supermavenjetbrains.binary.AnyCompletion
import supermaven.supermavenjetbrains.binary.ResponseItemKind
import java.util.concurrent.ConcurrentHashMap

@Service
class SupermavenService : Disposable {
    private val logger = Logger.getInstance(SupermavenService::class.java)
    private var binaryHandler: BinaryHandler? = null
    private val binaryFetcher = BinaryFetcher()
    private val completionRenderer = CompletionRenderer()

    private var lastText: String? = null
    private var lastPath: String? = null
    private var lastContext: DocumentState? = null
    private var currentEditor: Editor? = null
    private var currentFile: VirtualFile? = null
    private var wantsPolling: Boolean = false
    private var lastProvideTime: Long = 0
    private var pollingTimer: Timer? = null
    
    // State management (like BinaryLifecycle in nvim)
    private val changedDocumentList = ConcurrentHashMap<String, DocumentState>()
    private var lastState: LastState? = null
    
    // Disposable for listeners lifecycle management
    private var listenersDisposable: Disposable? = null

    private val activationNotificationGroup: NotificationGroup
        get() = NotificationGroupManager.getInstance().getNotificationGroup("SupermavenNotification")

    companion object {
        val SUPERMAVEN_TOPIC: Topic<SupermavenListener> = Topic.create(
            "Supermaven state",
            SupermavenListener::class.java
        )
        fun getInstance(): SupermavenService {
            return ApplicationManager.getApplication().getService(SupermavenService::class.java)
        }
    }

    fun start() {
        val binaryPath = binaryFetcher.fetch()
        if (binaryPath == null) {
            logger.error("Error fetching sm-agent")
            return
        }


        fun createActivateNotificationWithoutProject(activateUrl: String, includeFree: Boolean) {
            createActivateNotification(activateUrl, includeFree, null)
        }

        binaryHandler = BinaryHandler(binaryPath, ::createActivateNotificationWithoutProject)
        binaryHandler?.start()
        setupDocumentListeners()
        startPolling()

        logger.info("Supermaven service started")

        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(SUPERMAVEN_TOPIC)
            .stateChanged(true)
    }

    fun stop() {
        stopPolling()
        disposeListeners()
        binaryHandler?.stop()
        binaryHandler = null
        completionRenderer.clear()
        changedDocumentList.clear()
        lastState = null
        lastContext = null
        currentEditor = null
        currentFile = null
        lastText = null
        lastPath = null
        logger.info("Supermaven service stopped")

        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(SUPERMAVEN_TOPIC)
            .stateChanged(false)
    }
    
    override fun dispose() {
        stop()
    }

    fun isRunning(): Boolean {
        return binaryHandler?.isRunning() == true
    }

    fun usePro(project: Project?) {
        val activateUrl = binaryHandler?.getActivateUrl()
        if (activateUrl!!.isNotEmpty()) {
            logger.debug("Visit $activateUrl to set up Supermaven Pro")
            createActivateNotification(activateUrl, false, project)
            return
        }

        logger.error("Could not find an activation URL")
    }

    fun useFreeVersion() {
        binaryHandler?.useFreeVersion()
    }

    fun logout() {
        binaryHandler?.logout()
    }

    fun hasActiveCompletion(editor: Editor): Boolean {
        return completionRenderer.hasActiveCompletion(editor)
    }

    fun completionRenderAccept(editor: Editor) {
        val accepted = completionRenderer.acceptCompletion(editor, false)
        if (!accepted) {
            // If no completion was accepted, pass Tab through
            // This is handled by the action system, but we can log it
            logger.debug("No active completion to accept")
        }
    }

    fun completionRenderClear(editor: Editor) {
        completionRenderer.clear(editor)
    }

    fun completionRenderAcceptByWordKey(editor: Editor) {
        val accepted = completionRenderer.acceptCompletion(editor, true)
        if (!accepted) {
            logger.debug("No active completion to accept (word)")
        }
    }

    private fun createActivateNotification(activateUrl: String, includeFree: Boolean, project: Project?) {
        val intro = buildString {
            append("Please visit the following URL to set up Supermaven Pro")
            if (includeFree) {
                append(" (or use Free version)")
            }
        }

        val content = """
        $intro<br><br>
        <a href="$activateUrl">$activateUrl</a>
    """.trimIndent()

        val activationNotification =
            activationNotificationGroup.createNotification(
                "Supermaven activation required",
                content,
                NotificationType.INFORMATION
            )
        activationNotification.addAction(
            NotificationAction.createSimple("Open activation page") {
                BrowserUtil.browse(activateUrl)
            }
        )

        activationNotification.notify(project)
    }

    private fun setupDocumentListeners() {
        // Dispose previous listeners if any
        listenersDisposable?.let { Disposer.dispose(it) }
        
        // Create new disposable for this session
        val disposable = Disposer.newDisposable("SupermavenListeners")
        listenersDisposable = disposable
        
        val multicaster: EditorEventMulticaster = EditorFactory.getInstance().eventMulticaster
        
        logger.info("Supermaven: Setting up document listeners")
        
        // Global document listener - triggered for ALL documents
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (!isRunning()) return
                
                // Get the file for this document
                val file = FileDocumentManager.getInstance().getFile(event.document)
                if (file == null) {
                    return
                }
                
                // Find the active editor for this document
                val editor = findEditorForDocument(event.document) ?: return
                
                handleDocumentChange(editor, file)
            }
        }, disposable)
        
        // Global caret listener - triggered for ALL editors
        multicaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (!isRunning()) return
                
                val editor = event.editor
                val file = FileDocumentManager.getInstance().getFile(editor.document)
                if (file == null) {
                    return
                }
                
                // Логируем только каждое 10-е событие чтобы не спамить
                handleCursorChange(editor, file)
            }
        }, disposable)
    }
    
    private fun findEditorForDocument(document: com.intellij.openapi.editor.Document): Editor? {
        // First, try to find in open projects' selected editors
        for (project in ProjectManager.getInstance().openProjects) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val selectedEditor = fileEditorManager.selectedTextEditor
            if (selectedEditor?.document == document) {
                return selectedEditor
            }
        }
        
        // Fallback: find any editor with this document
        val editors = EditorFactory.getInstance().getEditors(document)
        return editors.firstOrNull { !it.isViewer }
    }
    
    private fun disposeListeners() {
        listenersDisposable?.let { 
            Disposer.dispose(it) 
        }
        listenersDisposable = null
    }

    private fun handleDocumentChange(editor: Editor, file: VirtualFile) {
        onUpdate(editor, file, "text_changed")
    }

    private fun handleCursorChange(editor: Editor, file: VirtualFile) {
        onUpdate(editor, file, "cursor")
    }
    
    // Similar to BinaryLifecycle:on_update in nvim
    private fun onUpdate(editor: Editor, file: VirtualFile, eventType: String) {
        val document = editor.document
        val bufferText = document.text
        val filePath = file.path
        
        if (bufferText.length > 10_000_000) {
            logger.debug("File is too large, skipping")
            return
        }
        
        // ALWAYS update current editor and file - this is critical for polling!
        currentEditor = editor
        currentFile = file
        
        documentChanged(filePath, bufferText, editor)
        
        val cursorOffset = editor.caretModel.offset
        val cursor = CursorPosition(editor.document.getLineNumber(cursorOffset), cursorOffset - editor.document.getLineStartOffset(editor.document.getLineNumber(cursorOffset)))
        
        val completionIsAllowed = (bufferText != lastText) && (lastPath == filePath)
        val context = DocumentState(
            path = filePath,
            content = bufferText,
            cursor = CursorUpdateMessage(path = filePath, offset = cursorOffset)
        )
        
        if (completionIsAllowed) {
            provideInlineCompletionItems(editor, cursor, context)
        } else if (!sameContext(context)) {
            completionRenderer.clear(editor)
        }
        
        lastPath = filePath
        lastText = bufferText
        lastContext = context
    }
    
    private data class CursorPosition(val line: Int, val column: Int)
    
    private fun sameContext(context: DocumentState): Boolean {
        if (lastContext == null) return false
        val last = lastContext!!
        return context.cursor.path == last.cursor.path &&
               context.cursor.offset == last.cursor.offset &&
               context.path == last.path &&
               context.content == last.content
    }
    
    // Similar to BinaryLifecycle:document_changed in nvim
    private fun documentChanged(fullPath: String, bufferText: String, editor: Editor) {
        val cursorOffset = editor.caretModel.offset
        changedDocumentList[fullPath] = DocumentState(
            path = fullPath,
            content = bufferText,
            cursor = CursorUpdateMessage(path = fullPath, offset = cursorOffset)
        )
        binaryHandler?.sendFileChanged(fullPath)
    }
    
    // Similar to BinaryLifecycle:provide_inline_completion_items in nvim
    private fun provideInlineCompletionItems(editor: Editor, cursor: CursorPosition, context: DocumentState) {
        currentEditor = editor
        currentFile = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.document)
        lastContext = context
        lastProvideTime = System.currentTimeMillis()
        pollOnce()
    }
    
    private fun startPolling() {
        stopPolling()
        pollingTimer = Timer(true)
        pollingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (wantsPolling) {
                    ApplicationManager.getApplication().invokeLater {
                        pollOnce()
                    }
                }
            }
        }, 0, 25) // Poll every 25ms like nvim
    }
    
    private fun stopPolling() {
        pollingTimer?.cancel()
        pollingTimer = null
        wantsPolling = false
    }
    
    // Similar to BinaryLifecycle:poll_once in nvim
    private fun pollOnce() {
        val now = System.currentTimeMillis()
        if (now - lastProvideTime > 5000) {
            wantsPolling = false
            return
        }
        wantsPolling = true
        
        val editor = currentEditor
        val file = currentFile
        
        if (editor == null || file == null) {
            return
        }
        
        if (!editor.component.isShowing) {
            wantsPolling = false
            return
        }
        
        val document = editor.document
        val cursorOffset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(cursorOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val columnInLine = cursorOffset - lineStartOffset
        
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, document.getLineEndOffset(lineNumber)))
        val lineBeforeCursor = if (columnInLine > 0) lineText.substring(0, columnInLine) else ""
        val lineAfterCursor = if (columnInLine < lineText.length) lineText.substring(columnInLine) else ""
        
        val prefix = getPrefix(document.text, cursorOffset)
        val getFollowingLine: (Int) -> String = { index ->
            val targetLine = lineNumber + index
            if (targetLine < document.lineCount) {
                val start = document.getLineStartOffset(targetLine)
                val end = document.getLineEndOffset(targetLine)
                document.getText(com.intellij.openapi.util.TextRange(start, end))
            } else {
                ""
            }
        }
        
        val dustStrings = binaryHandler?.getDustStrings() ?: emptyList()
        val queryStateId = submitQuery(editor, file, prefix, cursorOffset) ?: return

        val params = supermaven.supermavenjetbrains.binary.CompletionParams(
            lineBeforeCursor = lineBeforeCursor,
            lineAfterCursor = lineAfterCursor,
            getFollowingLine = getFollowingLine,
            dustStrings = dustStrings,
            canShowPartialLine = true,
            canRetry = false,
            sourceStateId = queryStateId
        )
        
        val maybeCompletion = checkState(prefix, params, queryStateId)
        
        if (maybeCompletion == null) {
            completionRenderer.clear(editor)
            return
        }
        
        when (maybeCompletion) {
            is supermaven.supermavenjetbrains.binary.JumpCompletion,
            is supermaven.supermavenjetbrains.binary.DeleteCompletion,
            is supermaven.supermavenjetbrains.binary.SkipCompletion -> {
                return
            }
            is supermaven.supermavenjetbrains.binary.TextCompletion -> {
                val dedent = maybeCompletion.dedent
                
                if (dedent.isEmpty() || (dedent.isNotEmpty() && lineBeforeCursor.endsWith(dedent))) {
                    // Remove common prefix from dedent and text
                    var text = maybeCompletion.text
                    var dedentStr = dedent
                    while (dedentStr.isNotEmpty() && text.isNotEmpty() && 
                           dedentStr[0] == text[0]) {
                        text = text.substring(1)
                        dedentStr = dedentStr.substring(1)
                    }
                    
                    val priorDelete = dedent.length
                    val trimmedText = text.trimEnd()
                    renderCompletion(editor, trimmedText, priorDelete, lineAfterCursor, lineBeforeCursor)
                    wantsPolling = maybeCompletion.isIncomplete
                }
            }
        }
    }
    
    // Similar to BinaryLifecycle:check_state in nvim - searches all states for best completion
    private fun checkState(
        prefix: String,
        params: supermaven.supermavenjetbrains.binary.CompletionParams,
        queryStateId: Int
    ): AnyCompletion? {
        var bestCompletion: List<supermaven.supermavenjetbrains.binary.ResponseItem>? = null
        var bestLength = 0
        var bestStateId = -1
        
        // Iterate through all states in BinaryHandler's stateMap
        val allStates = binaryHandler?.getAllStates() ?: return null
        
        for ((stateId, state) in allStates) {
            val statePrefix = state.prefix
            // Allow empty prefix (like in nvim: state_prefix ~= nil)
            // Check: prefix.length >= statePrefix.length (always true if statePrefix is empty)
            if (prefix.length >= statePrefix.length) {
                if (prefix.startsWith(statePrefix)) {
                    val userInput = prefix.substring(statePrefix.length)
                    // Copy the list to avoid ConcurrentModificationException
                    val completionSnapshot = state.completion.toList()
                    
                    // Skip states with no completions
                    if (completionSnapshot.isEmpty()) {
                        continue
                    }
                    
                    val remainingCompletion = stripPrefix(completionSnapshot, userInput)
                    if (remainingCompletion != null) {
                        val totalLength = completionTextLength(remainingCompletion)
                        if (totalLength > bestLength || (totalLength == bestLength && stateId > bestStateId)) {
                            bestCompletion = remainingCompletion
                            bestLength = totalLength
                            bestStateId = stateId
                        }
                    }
                }
            }
        }
        
        if (bestCompletion != null) {
            val updatedParams = params.copy(sourceStateId = bestStateId)
            return Textual.deriveCompletion(bestCompletion, updatedParams)
        }
        
        return null
    }
    
    private fun completionTextLength(completion: List<supermaven.supermavenjetbrains.binary.ResponseItem>): Int {
        var length = 0
        for (item in completion) {
            if (item.kind == ResponseItemKind.Text) {
                length += (item.text?.length ?: 0)
            }
        }
        return length
    }
    
    // Similar to BinaryLifecycle:strip_prefix in nvim
    private fun stripPrefix(
        completion: List<supermaven.supermavenjetbrains.binary.ResponseItem>,
        originalPrefix: String
    ): List<supermaven.supermavenjetbrains.binary.ResponseItem>? {
        var prefix = originalPrefix
        val remainingResponseItems = mutableListOf<supermaven.supermavenjetbrains.binary.ResponseItem>()
        
        for (responseItem in completion) {
            when (responseItem.kind) {
                ResponseItemKind.Text -> {
                    val text = responseItem.text ?: ""
                    if (!sharesCommonPrefix(text, prefix)) {
                        return null
                    }
                    val trimLength = minOf(text.length, prefix.length)
                    var remainingText = text.substring(trimLength)
                    prefix = prefix.substring(trimLength)
                    if (remainingText.isNotEmpty()) {
                        remainingResponseItems.add(responseItem.copy(text = remainingText))
                    }
                }
                ResponseItemKind.Delete -> {
                    remainingResponseItems.add(responseItem)
                }
                ResponseItemKind.Dedent -> {
                    if (prefix.isNotEmpty()) {
                        return null
                    }
                    remainingResponseItems.add(responseItem)
                }
                else -> {
                    if (prefix.isEmpty()) {
                        remainingResponseItems.add(responseItem)
                    }
                }
            }
        }
        
        return remainingResponseItems
    }
    
    private fun sharesCommonPrefix(str1: String, str2: String): Boolean {
        val minLength = minOf(str1.length, str2.length)
        return str1.substring(0, minLength) == str2.substring(0, minLength)
    }
    
    // Similar to BinaryLifecycle:submit_query in nvim
    private fun submitQuery(editor: Editor, file: VirtualFile, prefix: String, cursorOffset: Int): Int? {
        val document = editor.document
        val bufferText = document.text
        // Use the actual cursor offset, not the truncated prefix length
        val offset = cursorOffset
        
        val documentState = FileUpdateMessage(
            path = file.path,
            content = bufferText
        )
        val cursorState = CursorUpdateMessage(
            path = file.path,
            offset = offset
        )
        
        // Optimization: reuse state if nothing changed
        if (lastState != null) {
            if (changedDocumentList.isEmpty()) {
                if (lastState!!.cursor.path == cursorState.path &&
                    lastState!!.cursor.offset == cursorState.offset &&
                    lastState!!.document.path == documentState.path &&
                    lastState!!.document.content == documentState.content) {
                    // Return current state ID (reuse)
                    return binaryHandler?.getCurrentStateId()
                }
            }
        }
        
        val updates = mutableListOf<supermaven.supermavenjetbrains.binary.UpdateMessage>(cursorState)
        
        // Add all changed documents to updates (already populated by onUpdate -> documentChanged)
        for (documentValue in changedDocumentList.values) {
            updates.add(FileUpdateMessage(
                path = documentValue.path,
                content = documentValue.content
            ))
        }
        changedDocumentList.clear()
        
        val stateId = binaryHandler?.sendUpdates(updates)
        if (stateId != null) {
            binaryHandler?.updateStatePrefix(stateId, prefix)
            lastState = LastState(
                cursor = cursorState,
                document = documentState
            )
        }
        
        return stateId
    }
    
    private fun renderCompletion(
        editor: Editor,
        text: String,
        priorDelete: Int,
        lineAfterCursor: String,
        lineBeforeCursor: String
    ) {
        completionRenderer.render(editor, text, priorDelete)
    }

    private fun getPrefix(text: String, offset: Int): String {
        if (offset <= 0) return ""
        val start = maxOf(0, offset - 1000) // Limit prefix size
        return text.substring(start, offset)
    }
}

interface SupermavenListener {
    fun stateChanged(running: Boolean)
}