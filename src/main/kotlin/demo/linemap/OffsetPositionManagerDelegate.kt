package demo.linemap

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import com.intellij.openapi.diagnostic.Logger

val LOG = Logger.getInstance("Demo.LineMapper")

class OffsetPositionManagerDelegate(private val delegate: PositionManager) : PositionManager {

    init {
        LOG.info("gsd-gsd call init OffsetPositionManagerDelegate")
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
        val offset = sourceToRuntimeOffset(position)
        // 当你要下断点时：把 IDE 的源码行号 A 变成 A + 10 传给底层
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + offset)
        val locations = delegate.locationsOfLine(type, offsetPos)
        LOG.info("gsd-gsd call locationsOfLine from OffsetPositionManagerDelegate, type=${type}, position=${position}, offsetPos=${offsetPos}, locations=${locations}")
        return locations
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
        // 当手机停住时：底层返回 B，我们把它变成 B - 10 给 IDE 显示
        val pos = delegate.getSourcePosition(location) ?: return null
        val offset = runtimeToSourceOffset(pos)
        val sourcePos = SourcePosition.createFromLine(pos.file, pos.line + offset)
        LOG.info("gsd-gsd call getSourcePosition from OffsetPositionManagerDelegate, location=${location}, pos=${pos}, sourcePos=${sourcePos}")
        return sourcePos
    }

    override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
//        val offsetPos = SourcePosition.createFromLine(position.file, position.line)
        val classes = delegate.getAllClasses(position)
//        LOG.info("gsd-gsd call getAllClasses from OffsetPositionManagerDelegate, position=${position}, offsetPos=${offsetPos}, classes=${classes}")
        return classes
    }

    override fun createPrepareRequest(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): ClassPrepareRequest? {
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + sourceToRuntimeOffset(position))
        val request = delegate.createPrepareRequest(requestor, offsetPos)
        LOG.info("gsd-gsd call createPrepareRequest from OffsetPositionManagerDelegate, requestor=${requestor}, position=${position}, offsetPos=${offsetPos}, request=${request}")
        return request
    }

    private var cachedViewMap: Map<String, MethodMapping>? = null
    private fun getViewMethodMap(): Map<String, MethodMapping>? {
        if (cachedViewMap != null) {
            return cachedViewMap
        }
        val parser = DexParser()
        val className = "android.view.View"
        val runtimeJar = "/Users/bytedance/Desktop/xiaomi_framework.jar"
        val sourceJar = "/Users/bytedance/Desktop/runtime_framework.jar"
        cachedViewMap = parser.getMethodMap(className, sourceJar, runtimeJar)
        return cachedViewMap
    }

    private fun sourceToRuntimeOffset(position: SourcePosition): Int {
        val target = position.line + 1
        getViewMethodMap()?.forEach {
            if (target >= it.value.sourceStart && target <= it.value.sourceEnd) {
                return it.value.runtimeStart - it.value.sourceStart
            }
        }
        return 0
    }

    private fun runtimeToSourceOffset(position: SourcePosition): Int {
        val target = position.line + 1
        getViewMethodMap()?.forEach {
            if (target >= it.value.runtimeStart && target <= it.value.runtimeEnd) {
                return it.value.sourceStart - it.value.runtimeStart
            }
        }
        return 0
    }
}