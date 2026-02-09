package demo.linemap

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class PluginStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        LOG.info("gsd-gsd PluginStartupActivity runActivity called")
        DebugSessionInitializer(project) // 手动触发初始化
    }
}