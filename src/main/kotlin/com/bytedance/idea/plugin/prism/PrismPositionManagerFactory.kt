package com.bytedance.idea.plugin.prism

import com.intellij.debugger.PositionManager
import com.intellij.debugger.PositionManagerFactory
import com.intellij.debugger.engine.CompoundPositionManager
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.io.File

class PrismPositionManagerFactory : PositionManagerFactory() {

    override fun createPositionManager(process: DebugProcess): PositionManager? {
        LOG.info("createPositionManager enabled=${PrismSettings.getInstance().enabled} path=${PrismSettings.getInstance().frameworkJarPath}")

        if (PrismSettings.getInstance().enabled.not()) {
            return null
        }

        if (PrismSettings.getInstance().frameworkJarPath.isEmpty()) {
            Notifications.Bus.notify(
                Notification(
                    "Prism",
                    "Framework.jar not set",
                    "Please configure it in Settings > Prism",
                    NotificationType.WARNING
                )
            )
            return null
        }

        if (File(PrismSettings.getInstance().frameworkJarPath).exists().not()) {
            Notifications.Bus.notify(
                Notification(
                    "Prism",
                    "Framework.jar not exist",
                    "Please check it in Settings > Prism",
                    NotificationType.WARNING
                )
            )
            return null
        }

        val manager = PrismPositionManager(PositionManagerImpl(process as DebugProcessImpl))

        // 延迟执行插队逻辑，确保在所有 Manager 初始化完成后执行。将我们的 PositionManager 插入到列表的第一个位置。
        process.addDebugProcessListener(object : DebugProcessListener {
            override fun processAttached(process: DebugProcess) {
                reorderPositionManagers(process as DebugProcessImpl, manager)
            }
        })

        LOG.info("createPositionManager create proxy position manager")

        return manager
    }

    private fun reorderPositionManagers(process: DebugProcessImpl, myManager: PositionManager) {
        try {
            val field = CompoundPositionManager::class.java.getDeclaredField("myPositionManagers")
            field.isAccessible = true
            val managers = field.get(process.positionManager) as? MutableList<PositionManager>
            if (managers != null && managers.contains(myManager)) {
                managers.remove(myManager)
                managers.add(0, myManager)
            }
            LOG.info("successfully reorder position manager first=${managers?.firstOrNull()}")
        } catch (e: Exception) {
            LOG.error("fail to reorder position manager", e)
        }
    }
}