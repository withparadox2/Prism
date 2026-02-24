package com.bytedance.idea.plugin.prism

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
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
        if (offset == null) {
            if (shouldHandle(position.file)) {
                LOG.info("call locationsOfLine, offset=null, type=${type.signature()}, position=${position}")
            }
            throw NoDataException.INSTANCE
        }
        val offsetPos = SourcePosition.createFromLine(position.file, position.line + offset)
        val locations = delegate.locationsOfLine(type, offsetPos)
        LOG.info("call locationsOfLine, type=${type.signature()}, position=${position}, offsetPos=${offsetPos}, locations=${locations}")
        return locations
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
        val position = delegate.getSourcePosition(location) ?: throw NoDataException.INSTANCE
        val offset = runtimeToSourceOffset(position)
        if (offset == null) {
            if (shouldHandle(position.file)) {
                LOG.info("call getSourcePosition, offset=null, location=${location}, pos=${position.file.name + ":" + position.line}")
            }
            throw NoDataException.INSTANCE
        }
        val sourcePos = SourcePosition.createFromLine(position.file, position.line + offset)
        LOG.info("call getSourcePosition, location=${location}, pos=${position.file.name + ":" + position.line}, sourcePos=${sourcePos}")
        return sourcePos
    }

    override fun getAcceptedFileTypes(): MutableSet<out FileType>? {
        return mutableSetOf(JavaFileType.INSTANCE)
    }

    override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
        return delegate.getAllClasses(position).takeIf { it.isNotEmpty() }
            ?: throw NoDataException.INSTANCE
    }

    override fun createPrepareRequest(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): ClassPrepareRequest? {
        val offset = sourceToRuntimeOffset(position) ?: throw NoDataException.INSTANCE
        val offsetPos = SourcePosition.createFromLine(
            position.file,
            position.line + offset
        )
        return delegate.createPrepareRequest(requestor, offsetPos)
            ?: throw NoDataException.INSTANCE
    }

    override fun createPrepareRequests(
        requestor: ClassPrepareRequestor,
        position: SourcePosition
    ): MutableList<ClassPrepareRequest> {
        val offset = sourceToRuntimeOffset(position) ?: throw NoDataException.INSTANCE
        val offsetPos = SourcePosition.createFromLine(
            position.file,
            position.line + offset
        )
        return delegate.createPrepareRequests(requestor, offsetPos).takeIf { it.isNotEmpty() }
            ?: throw NoDataException.INSTANCE
    }

    private fun getMethodMap(psiFile: PsiFile): Map<String, MethodLineInfo>? {
        val className = getFullClassName(psiFile) ?: return null
        if (!shouldHandle(psiFile)) {
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

    private fun shouldHandle(psiFile: PsiFile): Boolean {
        return getFullClassName(psiFile)?.startsWith("android.") == true
    }

    private fun getFullClassName(psiFile: PsiFile): String? {
        return runReadAction {
            (psiFile as? PsiJavaFile)?.classes?.firstOrNull()?.qualifiedName
        }
    }

    private fun sourceToRuntimeOffset(position: SourcePosition): Int? {
        val target = position.line + 1
        getMethodMap(position.file)?.forEach {
            if (target >= it.value.sourceStart && target <= it.value.sourceEnd) {
                LOG.info("call sourceToRuntimeOffset file=${position.file} method=${it.key} info=${it.value}")
                return it.value.runtimeStart - it.value.sourceStart
            }
        }
        return null
    }

    private fun runtimeToSourceOffset(position: SourcePosition): Int? {
        val target = position.line + 1
        getMethodMap(position.file)?.forEach {
            if (target >= it.value.runtimeStart && target <= it.value.runtimeEnd) {
                LOG.info("call runtimeToSourceOffset file=${position.file} method=${it.key} info=${it.value}")
                return it.value.sourceStart - it.value.runtimeStart
            }
        }
        return null
    }
}