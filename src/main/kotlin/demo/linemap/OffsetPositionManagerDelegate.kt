package demo.linemap

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest


class OffsetPositionManagerDelegate(private val delegate: PositionManager) : PositionManager {
    private val OFFSET = 52

    init {
        LOG.info("gsd-gsd call init OffsetPositionManagerDelegate")
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
        // 当你要下断点时：把 IDE 的源码行号 A 变成 A + 10 传给底层
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + OFFSET)
        val locations = delegate.locationsOfLine(type, offsetPos)
        LOG.info("gsd-gsd call locationsOfLine from OffsetPositionManagerDelegate, type=${type}, position=${position}, offsetPos=${offsetPos}, locations=${locations}")
        return locations
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
        // 当手机停住时：底层返回 B，我们把它变成 B - 10 给 IDE 显示
        val pos = delegate.getSourcePosition(location) ?: return null
        val sourcePos = SourcePosition.createFromLine(pos.file, pos.line - OFFSET)
        LOG.info("gsd-gsd call getSourcePosition from OffsetPositionManagerDelegate, location=${location}, pos=${pos}, sourcePos=${sourcePos}")
        return sourcePos
    }

    override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + OFFSET + 1)
        val classes = delegate.getAllClasses(offsetPos)
        LOG.info("gsd-gsd call getAllClasses from OffsetPositionManagerDelegate, position=${position}, offsetPos=${offsetPos}, classes=${classes}")
        return classes
    }

    override fun createPrepareRequest(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): ClassPrepareRequest? {
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + OFFSET)
        val request = delegate.createPrepareRequest(requestor, offsetPos)
        LOG.info("gsd-gsd call createPrepareRequest from OffsetPositionManagerDelegate, requestor=${requestor}, position=${position}, offsetPos=${offsetPos}, request=${request}")
        return request
    }
}