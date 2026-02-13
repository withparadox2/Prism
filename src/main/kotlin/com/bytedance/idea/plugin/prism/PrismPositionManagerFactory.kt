package com.bytedance.idea.plugin.prism

import com.intellij.debugger.PositionManager
import com.intellij.debugger.PositionManagerFactory
import com.intellij.debugger.engine.CompoundPositionManager
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.PositionManagerImpl

class PrismPositionManagerFactory : PositionManagerFactory() {
//    override fun createPositionManager(process: DebugProcess): PositionManager? {
//        LOG.info("gsd-gsd call createPositionManager from FrameworkOffsetFactory process=${process}")
//        if (process is DebugProcessImpl) {
//            return PrismPositionManager(PositionManagerImpl(process))
//        }
//        return null
//    }

    override fun createPositionManager(process: DebugProcess): PositionManager? {
        val manager = PrismPositionManager(PositionManagerImpl(process as DebugProcessImpl))

        // 延迟执行插队逻辑，确保在所有 Manager 初始化完成后执行
        process.addDebugProcessListener(object : DebugProcessListener {
            override fun processAttached(process: DebugProcess) {
                reorderPositionManagers(process as DebugProcessImpl, manager)
            }
        })

        return manager
    }

    private fun reorderPositionManagers(process: DebugProcessImpl, myManager: PositionManager) {
        try {
            // 通过反射获取 DebugProcessImpl 内部存储 PositionManager 的列表
            // 在 IntelliJ 2024.1 中，该字段通常名为 "myPositionManagers"
            val field = CompoundPositionManager::class.java.getDeclaredField("myPositionManagers")
            field.isAccessible = true

            val managers = field.get(process.positionManager) as? MutableList<PositionManager>
            if (managers != null && managers.contains(myManager)) {
                // 1. 先移除自己
                managers.remove(myManager)
                // 2. 插入到索引 0 的位置，确保绝对领先
                managers.add(0, myManager)
                println("gsd-gsd: Successfully hijacked PositionManager order. Current first: ${managers[0]}")
            }
        } catch (e: Exception) {
            println("gsd-gsd: Hijack failed: ${e.message}")
        }
    }
}