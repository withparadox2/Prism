package demo.linemap

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XSourcePositionImpl

class MappedStackFrame(
    private val delegate: XStackFrame,
    private val mapper: LineNumberMapper
) : XStackFrame() {


    override fun getSourcePosition(): XSourcePosition? {
        val original = delegate.sourcePosition ?: return null
        val file = original.file
        val mappedLine = mapper.mapJarToSource(file, original.line) ?: original.line
        return XSourcePositionImpl.create(file, mappedLine)
    }


    override fun computeChildren(node: com.intellij.xdebugger.frame.XCompositeNode) {
        delegate.computeChildren(node)
    }


    override fun customizePresentation(component: com.intellij.ui.ColoredTextContainer) {
        delegate.customizePresentation(component)
        component.append(" [mapped]", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}