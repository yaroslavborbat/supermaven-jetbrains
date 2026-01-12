package supermaven.supermavenjetbrains.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent

data class Config(
    var acceptKey: String = "TAB",
    var clearKey: String = "ESCAPE",
    var acceptWordKey: String = "control RIGHT",
    var color: String = "GRAY",
    var ignoreFileTypes: String = "",
    var execCommandAgentWrapper : String = "",
    var enabled: Boolean = true
)

@State(name = "SupermavenConfig", storages = [Storage("supermaven.xml")])
@Service
class SupermavenConfigService : PersistentStateComponent<Config> {

    private var state = Config()

    override fun getState(): Config {
        return state
    }

    override fun loadState(state: Config) {
        this.state = state
    }

    companion object {
        fun getInstance(): SupermavenConfigService {
            return ApplicationManager.getApplication().getService(SupermavenConfigService::class.java)
        }
    }
}
