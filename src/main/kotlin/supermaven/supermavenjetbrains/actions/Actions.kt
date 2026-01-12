package  supermaven.supermavenjetbrains.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import supermaven.supermavenjetbrains.SupermavenService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import supermaven.supermavenjetbrains.config.Config

class StartAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SupermavenService.getInstance().start()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = SupermavenService.getInstance().isRunning() != true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class StopAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SupermavenService.getInstance().stop()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = SupermavenService.getInstance().isRunning() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class SupermavenToggleAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
        return SupermavenService.getInstance().isRunning()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = SupermavenService.getInstance()

        if (state) {
            service.start()
        } else {
            service.stop()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val running = SupermavenService.getInstance().isRunning()

        e.presentation.text = if (running) "Stop Supermaven" else "Start Supermaven"
        e.presentation.icon = if (running) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class UseFreeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SupermavenService.getInstance().useFreeVersion()
    }
}

class UseProAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SupermavenService.getInstance().usePro(e.project)
    }
}


class LogoutAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SupermavenService.getInstance().logout()
    }
}


class DynamicActionManager {
    companion object {
        private const val ACCEPT_KEY_ID = "SupermavenAcceptKeyAction"
        private const val CLEAR_KEY_ACTION_ID = "SupermavenClearKeyAction"
        private const val ACCEPT_WORD_KEY_ACTION_ID = "SupermavenAcceptWordKeyAction"

        val instance = DynamicActionManager()
    }

    private val logger = Logger.getInstance(DynamicActionManager::class.java)

    fun registerDynamicActions(config: Config) {
        registerActionsOnce()
        updateKeymapShortcuts(config)
    }

    private fun registerActionsOnce() {
        val actionManager = ActionManager.getInstance()

        if (actionManager.getAction(ACCEPT_KEY_ID) == null) {
            actionManager.registerAction(ACCEPT_KEY_ID, SupermavenAcceptAction())
        }
        if (actionManager.getAction(CLEAR_KEY_ACTION_ID) == null) {
            actionManager.registerAction(CLEAR_KEY_ACTION_ID, SupermavenClearAction())
        }
        if (actionManager.getAction(ACCEPT_WORD_KEY_ACTION_ID) == null) {
            actionManager.registerAction(ACCEPT_WORD_KEY_ACTION_ID, SupermavenAcceptWordAction())
        }
    }

    private fun updateKeymapShortcuts(config: Config) {
        val keymap = KeymapManager.getInstance().activeKeymap

        listOf(ACCEPT_KEY_ID, CLEAR_KEY_ACTION_ID, ACCEPT_WORD_KEY_ACTION_ID).forEach { actionId ->
            keymap.getShortcuts(actionId).forEach { shortcut ->
                keymap.removeShortcut(actionId, shortcut)
            }
        }

        setShortcutInKeymap(keymap, ACCEPT_KEY_ID, config.acceptKey)
        setShortcutInKeymap(keymap, CLEAR_KEY_ACTION_ID, config.clearKey)
        setShortcutInKeymap(keymap, ACCEPT_WORD_KEY_ACTION_ID, config.acceptWordKey)
    }

    private fun setShortcutInKeymap(keymap: Keymap, actionId: String, shortcut: String) {
        if (shortcut.isBlank()) return

        try {
            KeyboardShortcut.fromString(shortcut)?.let {
                keymap.addShortcut(actionId, it)
            }
        } catch (e: Exception) {
            logger.error("Error to set shortcut", e)
        }
    }
}

class SupermavenAcceptAction : AnAction("Accept Supermaven Suggestion") {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        SupermavenService.getInstance().completionRenderAccept(editor)
    }

    override fun update(e: AnActionEvent) {
        supermavenSuggestGenericUpdate(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class SupermavenClearAction: AnAction("Clear Supermaven Suggestion") {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        SupermavenService.getInstance().completionRenderClear(editor)
    }

    override fun update(e: AnActionEvent) {
        supermavenSuggestGenericUpdate(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class SupermavenAcceptWordAction: AnAction("Accept Word from Supermaven") {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        SupermavenService.getInstance().completionRenderAcceptByWordKey(editor)
    }

    override fun update(e: AnActionEvent) {
        supermavenSuggestGenericUpdate(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

private fun supermavenSuggestGenericUpdate(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val service = SupermavenService.getInstance()
    val hasCompletion = editor != null && service.hasActiveCompletion(editor)
    val running = service.isRunning()

    e.presentation.isEnabled = hasCompletion && running
    e.presentation.isVisible = hasCompletion && running
}