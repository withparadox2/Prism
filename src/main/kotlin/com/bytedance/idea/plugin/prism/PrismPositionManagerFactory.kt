package com.bytedance.idea.plugin.prism

import com.intellij.debugger.PositionManager
import com.intellij.debugger.PositionManagerFactory
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl

class PrismPositionManagerFactory : PositionManagerFactory() {
    override fun createPositionManager(process: DebugProcess): PositionManager? {
        LOG.info("gsd-gsd call createPositionManager from FrameworkOffsetFactory process=${process}")
        if (process is DebugProcessImpl) {
            return PrismPositionManager(PositionManagerImpl(process))
        }
        return null
    }
}