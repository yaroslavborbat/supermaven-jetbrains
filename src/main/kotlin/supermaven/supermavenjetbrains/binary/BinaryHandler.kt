package supermaven.supermavenjetbrains.binary

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BinaryHandler(
    private val binaryPath: String,
    private val activateRequestCallback: (String, Boolean) -> Unit,
) {

    private val logger = Logger.getInstance(BinaryHandler::class.java)
    private val gson = Gson()
    private var process: Process? = null
    private var stdin: PrintWriter? = null
    private var stdout: BufferedReader? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activateUrl: String = ""
    private var serviceMessageDisplayed = false

    private val currentStateId = AtomicInteger(0)
    private val maxStateIdRetention = 50

    data class CompletionState(
        var prefix: String,
        val completion: MutableList<ResponseItem> = mutableListOf(),
        val hasEnded: Boolean = false
    )

    private val stateMap = ConcurrentHashMap<Int, CompletionState>()
    private var dustStrings: List<String>? = null

    fun start() {
        if (isRunning()) {
            logger.warn("Binary already running")
            return
        }
        try {
            val processBuilder = ProcessBuilder(binaryPath, "stdio")
            process = processBuilder.start()

            stdin = PrintWriter(BufferedWriter(OutputStreamWriter(process!!.outputStream)), true)
            stdout = BufferedReader(InputStreamReader(process!!.inputStream))

            logger.info("Started Supermaven binary")

             startReadLoop()
             sendGreeting()

        } catch (e: Exception) {
            logger.error("Error starting process", e)
            stop()
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
        stdin?.close()
        stdout?.close()
        stdin = null
        stdout = null

        logger.info("Stopped Supermaven binary")
    }

    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    fun getActivateUrl(): String {
        return activateUrl
    }

    fun useFreeVersion() {
        sendJson(UseFreeVersionMessage())
    }

    fun logout() {
        serviceMessageDisplayed = false
        sendJson(LogoutMessage())
    }

    fun sendFileChanged(path: String) {
        sendJson(InformFileChangedMessage(path = path))
    }

    fun sendUpdates(updates: List<UpdateMessage>): Int {
        purgeOldStates()
        val stateId = currentStateId.incrementAndGet()
        sendMessage(updates, stateId)
        stateMap[stateId] = CompletionState(prefix = "")
        return stateId
    }

    fun getState(stateId: Int): CompletionState? {
        return stateMap[stateId]
    }

    fun updateStatePrefix(stateId: Int, prefix: String) {
        stateMap[stateId]?.prefix = prefix
    }
    
    fun getAllStates(): Map<Int, CompletionState> {
        return stateMap.toMap()
    }
    
    fun getCurrentStateId(): Int {
        return currentStateId.get()
    }
    
    fun getDustStrings(): List<String> {
        return dustStrings ?: emptyList()
    }

    private fun startReadLoop() {
        scope.launch {
            try {
                while (isRunning()) {
                    val line = stdout?.readLine() ?: break
                    processLine(line)
                }
            } catch (e: Exception) {
                logger.error("Error in read loop", e)
            }
        }
    }

    private fun sendGreeting() {
        sendJson(GreetingMessage())
    }

    private fun sendMessage(updates: List<UpdateMessage>, stateId: Int) {
        val msg = StateUpdateMessage(
            newId = stateId.toString(),
            updates = updates,
        )
        sendJson(msg)
    }

    private fun sendJson(message: Any) {
        try {
            val json = gson.toJson(message)
            stdin?.println(json)
            stdin?.flush()
        } catch (e: Exception) {
            logger.error("Failed to send JSON message", e)
        }
    }

    private fun processLine(line: String) {
        if (line.startsWith("SM-MESSAGE ")) {
            try {
                val jsonStr = line.substring(11)
                val message = gson.fromJson(jsonStr, Message::class.java)
                processMessage(message)
            } catch (e: JsonSyntaxException) {
                logger.warn("Failed to parse message: $line", e)
            } catch (e: Exception) {
                logger.error("Error processing message: $line", e)
            }
            return
        }
    }

    private fun processMessage(message: Message) {
        when (message.kind) {
            MessageKind.Response -> updateStatusID(message)
            MessageKind.Metadata -> updateMetadata(message)
            MessageKind.ActivationRequest -> updateActivateRequest(message)
            MessageKind.ActivationSuccess -> finalizeActivation()
            MessageKind.Passthrough -> message.passthrough?.let { processMessage(it) }
            MessageKind.ServiceTier -> updateServiceTier(message)
            MessageKind.Popup -> {
                // unused
            }
            MessageKind.TaskStatus, MessageKind.ActiveRepo, MessageKind.Set, MessageKind.SetV2, MessageKind.ConnectionStatus, MessageKind.UserStatus -> {
                // unused, no staus bar is displayed
            }
            MessageKind.Apology -> {
                // legacy
            }
        }
    }

    private fun updateStatusID(message: Message) {
        val stateId = message.stateId ?: return
        val currentState = stateMap[stateId] ?: return
        val items = message.items
        if (items.isNullOrEmpty()) {
            return
        }
        currentState.completion.addAll(items)
    }

    private fun updateMetadata(message: Message) {
        if (message.dustStrings != null) {
            dustStrings = message.dustStrings
        }
    }

    private fun updateActivateRequest(message: Message) {
        if (message.activateUrl != null) {
            activateUrl = message.activateUrl

            if (activateUrl.isNotEmpty()) {
                activateRequestCallback(activateUrl, true)
            }
        }
    }

    private fun finalizeActivation() {
        activateUrl = ""
        logger.trace("Supermaven was activated successfully")
    }

    private fun updateServiceTier(message: Message) {
        if (!serviceMessageDisplayed) {
            if (message.display != null) {
                logger.trace("Supermaven ${message.display} is running")
            }
            serviceMessageDisplayed = true
        }
    }

    private fun purgeOldStates() {
        val currentId = currentStateId.get()
        val toRemove = stateMap.keys.filter { it < currentId - maxStateIdRetention }
        toRemove.forEach { stateMap.remove(it) }
    }
}