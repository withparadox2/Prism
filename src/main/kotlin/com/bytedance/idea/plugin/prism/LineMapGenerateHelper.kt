package com.bytedance.idea.plugin.prism

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.MultiDexContainer
import org.jf.dexlib2.iface.debug.LineNumber
import java.io.File

data class MethodLineInfo(
    val sourceStart: Int,
    val sourceEnd: Int,
    val runtimeStart: Int,
    val runtimeEnd: Int
)

private val primitiveMapping = mapOf(
    "boolean" to "Z",
    "byte" to "B",
    "char" to "C",
    "short" to "S",
    "int" to "I",
    "long" to "J",
    "float" to "F",
    "double" to "D",
    "void" to "V"
)

class LineMapGenerateHelper {
    private val jarPathToDexContainer = mutableMapOf<String, MultiDexContainer<out DexBackedDexFile>>()

    fun getMethodLineInfoMap(
        className: String,
        psiFile: PsiFile,
        runtimeJarPath: String
    ): Map<String, MethodLineInfo> {
        val resultMap = mutableMapOf<String, MethodLineInfo>()
        val sourceMap = getSourceMethodLineMap(psiFile)
        val runtimeMap = getRuntimeMethodLineMap(runtimeJarPath, className)
        sourceMap.forEach { (methodKey, sourceRange) ->
            val runtimeRange = runtimeMap[methodKey]
            if (runtimeRange != null) {
                resultMap[methodKey] = MethodLineInfo(
                    sourceRange.first,
                    sourceRange.second,
                    runtimeRange.first,
                    runtimeRange.second
                )
            }
        }
        return resultMap
    }

    /**
     * @param jarPath 传入 framework.jar 的路径
     * @param targetClassName 目标类名，如 "android.view.View"
     */
    private fun getRuntimeMethodLineMap(
        jarPath: String?,
        targetClassName: String
    ): Map<String, Pair<Int, Int>> {
        val methodMap: MutableMap<String, Pair<Int, Int>> = mutableMapOf()
        jarPath ?: return methodMap

        val dexClassName = "L" + targetClassName.replace(".", "/") + ";"
        try {
            val file = File(jarPath)
            if (!file.exists()) return methodMap

            val container = jarPathToDexContainer[jarPath] ?: DexFileFactory.loadDexContainer(
                file,
                Opcodes.getDefault()
            ).also {
                jarPathToDexContainer[jarPath] = it
            }
            // 遍历容器内所有的 dex 入口名称 (如 classes.dex, classes2.dex ...)
            for (entryName in container.dexEntryNames) {
                val dexFile = container.getEntry(entryName) ?: continue

                LOG.info("正在解析 Dex 入口: $entryName, 类数量: ${dexFile.classes.size}")

                for (classDef in dexFile.classes) {
                    if (classDef.type == dexClassName) {
                        parseClassMethods(classDef, methodMap)
                        return methodMap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return methodMap
    }

    private fun parseClassMethods(
        classDef: ClassDef,
        methodMap: MutableMap<String, Pair<Int, Int>>
    ) {
        for (method in classDef.methods) {
            val methodKey: String = method.name + method.parameters.toString()
            if (method.implementation != null && methodKey != "<clinit>[]") {
                var start = -1
                var end = -1
                for (item in method.implementation?.debugItems ?: emptyList()) {
                    if (item is LineNumber) {
                        val currentLine = item.lineNumber
                        if (start == -1) start = currentLine
                        end = end.coerceAtLeast(currentLine)
                    }
                }
                if (start != -1) {
                    methodMap[methodKey] = start to end
                }
            }
        }
    }

    private fun getSourceMethodLineMap(psiFile: PsiFile): Map<String, Pair<Int, Int>> {
        return runReadAction {
            val methodMap = mutableMapOf<String, Pair<Int, Int>>()
            val project = psiFile.project
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction methodMap

            val methods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            for (method in methods) {
                if (method.name == "<clinit>") continue

                val paramsList = method.parameterList.parameters.joinToString(", ") { param ->
                    getDexCompatibleTypeName(param.type)
                }

                val methodKey = "${method.name}[$paramsList]"

                val firstStatement = method.body?.statements?.firstOrNull()
                val startOffset = firstStatement?.textRange?.startOffset
                    ?: method.nameIdentifier?.textRange?.startOffset
                    ?: method.textRange.startOffset
                val startLine = document.getLineNumber(startOffset) + 1

                val lastStatement = method.body?.statements?.lastOrNull()
                val endOffset = lastStatement?.textRange?.endOffset
                    ?: method.body?.rBrace?.textRange?.startOffset
                    ?: method.textRange.endOffset
                val endLine = document.getLineNumber(endOffset) + 1

                methodMap[methodKey] = startLine to endLine
            }

            methodMap
        }
    }

    private fun getDexCompatibleTypeName(type: PsiType): String {
        return when (type) {
            is PsiPrimitiveType -> {
                // boolean -> Z, int -> I
                primitiveMapping[type.canonicalText] ?: type.canonicalText
            }

            is PsiArrayType -> {
                // 数组在 Dex 中是以 [ 开头的，例如 int[] -> [I
                "[" + getDexCompatibleTypeName(type.componentType)
            }

            else -> {
                // 引用类型转换为 Landroid/view/View; 格式
                // 1. 去掉泛型 2. 点换成斜杠 3. 前缀 L 后缀 ;
                val rawName = type.canonicalText.split("<")[0]
                "L${rawName.replace(".", "/")};"
            }
        }
    }
}
