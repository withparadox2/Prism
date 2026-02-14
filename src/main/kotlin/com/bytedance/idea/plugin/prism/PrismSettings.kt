package com.bytedance.idea.plugin.prism

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
    name = "PrismSettings",
    storages = [Storage("PrismSettings.xml")]
)
@Service(Service.Level.APP)
class PrismSettings : PersistentStateComponent<PrismSettings.State> {

    data class State(
        var frameworkJarPath: String = "",
        var enabled: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var frameworkJarPath: String
        get() = state.frameworkJarPath
        set(value) { state.frameworkJarPath = value }

    var enabled: Boolean
        get() = state.enabled
        set(value) { state.enabled = value }

    companion object {
        fun getInstance(): PrismSettings {
            return service()
        }
    }
}
