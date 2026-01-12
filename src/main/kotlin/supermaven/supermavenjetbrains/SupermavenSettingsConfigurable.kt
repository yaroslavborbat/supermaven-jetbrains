package supermaven.supermavenjetbrains

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.JBUI
import supermaven.supermavenjetbrains.actions.DynamicActionManager
import supermaven.supermavenjetbrains.config.Config
import supermaven.supermavenjetbrains.config.SupermavenConfigService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class SupermavenSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private lateinit var acceptKeyField: JTextField
    private lateinit var clearKeyField: JTextField
    private lateinit var acceptWordKeyField: JTextField
    private lateinit var colorField: JTextField
    private lateinit var ignoreFileTypesField: JTextField
    private lateinit var execCommandAgentWrapperField: JTextField
    private lateinit var enabledCheckBox: JCheckBox

    override fun getDisplayName(): String = "Supermaven Settings"

    override fun createComponent(): JPanel {
        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.insets = JBUI.insets(4)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        var row = 0

        gbc.gridy = row++
        panel!!.add(JLabel("Accept Key:"), gbc)
        gbc.gridy = row++
        acceptKeyField = JTextField()
        panel!!.add(acceptKeyField, gbc)

        gbc.gridy = row++
        panel!!.add(JLabel("Clear Key:"), gbc)
        gbc.gridy = row++
        clearKeyField = JTextField()
        panel!!.add(clearKeyField, gbc)

        gbc.gridy = row++
        panel!!.add(JLabel("Accept Word Key:"), gbc)
        gbc.gridy = row++
        acceptWordKeyField = JTextField()
        panel!!.add(acceptWordKeyField, gbc)

        gbc.gridy = row++
        panel!!.add(JLabel("Completion color:"), gbc)
        gbc.gridy = row++
        colorField = JTextField()
        panel!!.add(colorField, gbc)

        gbc.gridy = row++
        panel!!.add(JLabel("Ignore file types:"), gbc)
        gbc.gridy = row++
        ignoreFileTypesField = JTextField()
        panel!!.add(ignoreFileTypesField, gbc)

        gbc.gridy = row++
        panel!!.add(JLabel("Agent command wrapper (e.g., your-wrapper <agent-path> [agent-args]):"), gbc)
        gbc.gridy = row++
        execCommandAgentWrapperField = JTextField()
        panel!!.add(execCommandAgentWrapperField, gbc)

        gbc.gridy = row++
        enabledCheckBox = JCheckBox("Enabled")
        panel!!.add(enabledCheckBox, gbc)

        gbc.gridy = row++
        gbc.weighty = 1.0
        panel!!.add(Box.createVerticalGlue(), gbc)

        reset()
        return panel!!
    }


    override fun isModified(): Boolean {
        val state = SupermavenConfigService.getInstance().state
        return acceptKeyField.text != state.acceptKey ||
                clearKeyField.text != state.clearKey ||
                acceptWordKeyField.text != state.acceptWordKey ||
                colorField.text != state.color ||
                ignoreFileTypesField.text != state.ignoreFileTypes ||
                execCommandAgentWrapperField.text != state.execCommandAgentWrapper ||
                enabledCheckBox.isSelected != state.enabled
    }

    override fun apply() {
        val config = Config(
            acceptKey = acceptKeyField.text,
            clearKey = clearKeyField.text,
            acceptWordKey = acceptWordKeyField.text,
            color = colorField.text,
            ignoreFileTypes = ignoreFileTypesField.text,
            execCommandAgentWrapper = execCommandAgentWrapperField.text,
            enabled = enabledCheckBox.isSelected
        )
        DynamicActionManager.instance.registerDynamicActions(config)
        SupermavenConfigService.getInstance().loadState(config)
    }

    override fun reset() {
        val state = SupermavenConfigService.getInstance().state
        acceptKeyField.text = state.acceptKey
        clearKeyField.text = state.clearKey
        acceptWordKeyField.text = state.acceptWordKey
        colorField.text = state.color
        ignoreFileTypesField.text = state.ignoreFileTypes
        execCommandAgentWrapperField.text = state.execCommandAgentWrapper
        enabledCheckBox.isSelected = state.enabled
    }

    override fun disposeUIResources() {
        panel = null
    }
}
