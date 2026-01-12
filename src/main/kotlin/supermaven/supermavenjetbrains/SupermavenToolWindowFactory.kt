package supermaven.supermavenjetbrains

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.Box
import javax.swing.JLabel

class SupermavenToolWindowFactory : ToolWindowFactory, DumbAware {

    private val service = SupermavenService.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val root = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6)
        }

        val actionManager = ActionManager.getInstance()

        val actionGroup = DefaultActionGroup(
            actionManager.getAction(
                "supermaven.supermavenjetbrains.actions.SupermavenToggleAction"
            )
        )

        val toolbar = actionManager.createActionToolbar(
            "SupermavenToolbar",
            actionGroup,
            true
        )

        val statusLabel = JLabel()
        updateStatus(statusLabel)

        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(toolbar.component)
            add(Box.createHorizontalStrut(8))
            add(statusLabel)
        }

        root.add(row, BorderLayout.NORTH)

        root.minimumSize = JBUI.size(180, 32)
        root.preferredSize = JBUI.size(220, 36)

        val content = ContentFactory.getInstance()
            .createContent(root, null, false)

        toolWindow.contentManager.addContent(content)

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(SupermavenService.SUPERMAVEN_TOPIC, object : SupermavenListener {
                override fun stateChanged(running: Boolean) {
                    ApplicationManager.getApplication().invokeLater {
                        updateStatus(statusLabel)
                        toolbar.updateActionsAsync()
                    }
                }
            })
    }

    private fun updateStatus(label: JLabel) {
        label.text = if (service.isRunning()) {
            "● Supermaven running"
        } else {
            "○ Supermaven stopped"
        }
    }
}
