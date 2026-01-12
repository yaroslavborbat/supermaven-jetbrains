package supermaven.supermavenjetbrains.binary

import com.google.gson.annotations.SerializedName

// =====================
// Completion types
// =====================

interface CompletionBase {
    val isIncomplete: Boolean
    val sourceStateId: Int
    val completionIndex: Int?
}

data class TextCompletion(
    val kind: String = "text",
    val text: String,
    val dedent: String,
    val shouldRetry: Boolean? = null,
    override val isIncomplete: Boolean,
    override val sourceStateId: Int,
    override val completionIndex: Int? = null
) : CompletionBase

data class JumpCompletion(
    val kind: String = "jump",
    val fileName: String,
    val lineNumber: Int,
    val verify: String? = null,
    val precede: List<String>,
    val follow: List<String>,
    val isCreateFile: Boolean,
    override val isIncomplete: Boolean,
    override val sourceStateId: Int,
    override val completionIndex: Int? = null
) : CompletionBase

data class DeleteCompletion(
    val kind: String = "delete",
    val lines: List<String>,
    override val completionIndex: Int,
    override val sourceStateId: Int,
    override val isIncomplete: Boolean = false
) : CompletionBase

data class SkipCompletion(
    val kind: String = "skip",
    val n: Int,
    override val completionIndex: Int,
    override val sourceStateId: Int,
    override val isIncomplete: Boolean = false
) : CompletionBase

typealias AnyCompletion = CompletionBase

// =====================
// Completion parameters
// =====================

data class CompletionParams(
    val lineBeforeCursor: String,
    val lineAfterCursor: String,
    val getFollowingLine: (Int) -> String,
    val dustStrings: List<String>,
    val canShowPartialLine: Boolean,
    val canRetry: Boolean,
    val sourceStateId: Int
)

// =====================
// Response types
// =====================

enum class ResponseItemKind {
    @SerializedName("text") Text,
    @SerializedName("delete") Delete,
    @SerializedName("dedent") Dedent,
    @SerializedName("end") End,
    @SerializedName("barrier") Barrier,
    @SerializedName("finish_edit") FinishEdit,
    @SerializedName("skip") Skip,
    @SerializedName("jump") Jump,
}

data class ResponseItem(
    val kind: ResponseItemKind,
    val text: String? = null,
    val n: Int? = null,
    val fileName: String? = null,
    val lineNumber: Int? = null,
    val verify: String? = null,
    val precede: List<String>? = null,
    val follow: List<String>? = null,
    val isCreateFile: Boolean? = null
)

// =====================
// Chain information
// =====================

enum class ChainInfoKind {
    @SerializedName("text") Text,
    @SerializedName("delete") Delete,
    @SerializedName("skip") Skip,
    @SerializedName("jump") Jump,
}

data class ChainInfo(
    val completionIndex: Int,
    val sourceStateId: Int,
    val insertNewline: Boolean,
    val kind: ChainInfoKind
)

data class TimeStampedChainInfo(
    val expectedLine: String,
    val timestamp: Long,
    val chainInfo: ChainInfo
)

// =====================
// Outgoing messages
// =====================

data class InformFileChangedMessage(
    val kind: String = "inform_file_changed",
    val path: String
)

data class GreetingMessage(
    val kind: String = "greeting",
    val allowGitignore: Boolean = false
)

data class UseFreeVersionMessage(
    val kind: String = "use_free_version"
)

data class LogoutMessage(
    val kind: String = "logout"
)

// =====================
// State update messages
// =====================

sealed interface UpdateMessage

data class FileUpdateMessage(
    val kind: String = "file_update",
    val path: String,
    val content: String
): UpdateMessage

data class CursorUpdateMessage(
    val kind: String = "cursor_update",
    val path: String,
    val offset: Int
): UpdateMessage

data class StateUpdateMessage(
    val kind: String = "state_update",
    val newId: String,
    val updates: List<UpdateMessage>
)

data class LastState(
    val cursor: CursorUpdateMessage,
    val document: FileUpdateMessage
)

data class DocumentState(
    val path: String,
    val content: String,
    val cursor: CursorUpdateMessage
)

// =====================
// Incoming messages
// =====================

enum class MessageKind {
    @SerializedName("response") Response,
    @SerializedName("metadata") Metadata,
    @SerializedName("activation_request") ActivationRequest,
    @SerializedName("activation_success") ActivationSuccess,
    @SerializedName("passthrough") Passthrough,
    @SerializedName("popup") Popup,
    @SerializedName("task_status") TaskStatus,
    @SerializedName("active_repo") ActiveRepo,
    @SerializedName("service_tier") ServiceTier,
    @SerializedName("apology") Apology,
    @SerializedName("set") Set,
    @SerializedName("set_v2") SetV2,
    @SerializedName("connection_status") ConnectionStatus,
    @SerializedName("user_status") UserStatus
}

data class Message(
    val kind: MessageKind,
    val stateId: Int? = null,
    val activateUrl: String? = null,
    val display: String? = null,
    val passthrough: Message? = null,
    val items: List<ResponseItem>? = null,
    val dustStrings: List<String>? = null,
)

