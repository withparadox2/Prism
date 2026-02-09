package demo.linemap


import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener

val LOG = Logger.getInstance("Demo.LineMapper")

@Service(Service.Level.PROJECT)
class DebugSessionInitializer(project: Project) {
    init {
        LOG.info("gsd-gsd call DebugSessionInitializer init")

        project.messageBus.connect().subscribe(
            XDebuggerManager.TOPIC,
            object : XDebuggerManagerListener {
                override fun processStarted(debugProcess: XDebugProcess) {
                    LOG.info("gsd-gsd call processStarted")

                    val session = debugProcess.session
                    val mapper = FakeLineNumberMapper() // 真实映射

                    // 监听断点下发
                    @Suppress("removal")
                    XDebuggerManager.getInstance(project).breakpointManager.addBreakpointListener(object : XBreakpointListener<XBreakpoint<*>> {
                        override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
                            breakpoint.sourcePosition
                        }
                    })


                    session.addSessionListener(object : XDebugSessionListener {
                        override fun stackFrameChanged() {
                            LOG.info("gsd-gsd call stackFrameChanged v2")
                            val current = session.currentStackFrame ?: return

                            // 避免重复包装
                            if (current is MappedStackFrame) return

                            // 创建映射后的栈帧
                            val mappedFrame = MappedStackFrame(current, mapper)

                            // 核心修复：主动更新会话的当前栈帧
                            session.suspendContext.activeExecutionStack?.let {
                                LOG.info("gsd-gsd call setCurrentStackFrame")
                                session.setCurrentStackFrame(it, mappedFrame)
                            }

                            LOG.info("gsd-gsd mapped stack frame line from ${current.sourcePosition} to ${mappedFrame.sourcePosition}")
                        }
                    })
                }
            }
        )
    }

}