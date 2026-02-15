package com.bytedance.idea.plugin.prism

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.application.runReadAction
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

val LOG = Logger.getInstance("PrismLogger")

class PrismPositionManager(private val delegate: PositionManagerImpl) :
    MultiRequestPositionManager {

    private var cachedMap: MutableMap<String, Map<String, MethodLineInfo>> = mutableMapOf()
    private val mapGenerateHelper = LineMapGenerateHelper()

    init {
        LOG.info("call init PrismPositionManager")
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
        val offset = sourceToRuntimeOffset(position)
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + offset)
        val locations = delegate.locationsOfLine(type, offsetPos)
        LOG.info("call locationsOfLine, type=${type}, position=${position}, offsetPos=${offsetPos}, locations=${locations}")
        return locations
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
        // 当手机停住时：底层返回 B，我们把它变成 B - 10 给 IDE 显示
        val pos = delegate.getSourcePosition(location) ?: return null
        val offset = runtimeToSourceOffset(pos)
        val sourcePos = SourcePosition.createFromLine(pos.file, pos.line + offset)
        LOG.info("call getSourcePosition, location=${location}, pos=${pos}, sourcePos=${sourcePos}")
        return sourcePos
    }

    override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
        return delegate.getAllClasses(position)
    }

    override fun createPrepareRequest(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): ClassPrepareRequest? {
        val offsetPos = SourcePosition.createFromLine(
            position.file,
            position.line + sourceToRuntimeOffset(position)
        )
        return delegate.createPrepareRequest(requestor, offsetPos)
    }

    override fun createPrepareRequests(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): MutableList<ClassPrepareRequest> {
        val offsetPos = SourcePosition.createFromLine(
            position.file,
            position.line + sourceToRuntimeOffset(position)
        )
        return delegate.createPrepareRequests(requestor, offsetPos)
    }

    private fun getMethodMap(psiFile: PsiFile): Map<String, MethodLineInfo>? {
        val className = getFullClassName(psiFile) ?: return null
        if (!className.startsWith("android.")) {
            return null
        }
        val methodMap = cachedMap[className]
        if (methodMap != null) {
            return methodMap
        }
        val runtimeJar =  PrismSettings.getInstance().frameworkJarPath
        return mapGenerateHelper.getMethodLineInfoMap(className, psiFile, runtimeJar).also {
            cachedMap[className] = it
        }
    }

    private fun getFullClassName(psiFile: PsiFile): String? {
        return runReadAction {
            (psiFile as? PsiJavaFile)?.classes?.firstOrNull()?.qualifiedName
        }
    }

    private fun sourceToRuntimeOffset(position: SourcePosition): Int {
        val target = position.line + 1
        getMethodMap(position.file)?.forEach {
            if (target >= it.value.sourceStart && target <= it.value.sourceEnd) {
                return it.value.runtimeStart - it.value.sourceStart
            }
        }
        return 0
    }

    private fun runtimeToSourceOffset(position: SourcePosition): Int {
        val target = position.line + 1
        getMethodMap(position.file)?.forEach {
            if (target >= it.value.runtimeStart && target <= it.value.runtimeEnd) {
                return it.value.sourceStart - it.value.runtimeStart
            }
        }
        return 0
    }
}