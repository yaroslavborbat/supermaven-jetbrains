package supermaven.supermavenjetbrains

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import supermaven.supermavenjetbrains.actions.DynamicActionManager
import supermaven.supermavenjetbrains.config.SupermavenConfigService


class SupermavenProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val state = SupermavenConfigService.getInstance().state
        DynamicActionManager.instance.registerDynamicActions(state)

        if (!state.enabled) {
            return
        }

        try {
            val service = SupermavenService.getInstance()
            if (!service.isRunning()) {
                service.start()
            }
        } catch (e: Exception) {
            Logger.getInstance(SupermavenProjectActivity::class.java).error("Failed to auto-start Supermaven", e)
        }
    }
}