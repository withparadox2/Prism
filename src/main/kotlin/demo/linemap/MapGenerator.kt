package demo.linemap

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.debug.LineNumber
import java.io.File

data class MethodMapping(
    val sourceStart: Int,
    val sourceEnd: Int,
    val runtimeStart: Int,
    val runtimeEnd: Int
)

class DexParser {

    private val map = mutableMapOf<String, Map<String, MethodMapping>>()

    fun getMethodMap(
        className: String,
        psiFile: PsiFile,
        runtimeJarPath: String
    ): Map<String, MethodMapping>? {
        if (!map.containsKey(className)) {
            map[className] = calculateOffsets(className, psiFile, runtimeJarPath)
        }
        return map[className]
    }

    private fun calculateOffsets(
        className: String,
        psiFile: PsiFile,
        runtimeJarPath: String
    ): Map<String, MethodMapping> {
        val resultMap = mutableMapOf<String, MethodMapping>()
        println("call calculateOffsets source")
        // 1. 获取模拟器（Source）端的行号范围：Map<MethodKey, Pair<Start, End>>
        val sourceMap = generateMethodLineMap(psiFile)

        println("call calculateOffsets runtime")
        // 2. 获取手机（Runtime）端的起始行：Map<MethodKey, Pair<Start, End>>
        // 注意：计算偏移量只需要手机端的 Start 即可
        val runtimeMap = getMethodStartLinesFromJar(runtimeJarPath, className)

        // 3. 以模拟器端为基准进行遍历
        sourceMap.forEach { (methodKey, sourceRange) ->
            // 查找手机端是否存在该方法
            val runtimeRange = runtimeMap[methodKey]

            if (runtimeRange != null) {
                resultMap[methodKey] = MethodMapping(sourceRange.first, sourceRange.second, runtimeRange.first, runtimeRange.second)
            } else {
                System.err.println("Method $methodKey not found in runtime JAR")
            }
        }

        return resultMap
    }

    /**
     * @param jarPath 传入 framework.jar 的路径
     * @param targetClassName 目标类名，如 "android.view.View"
     */
    private fun getMethodStartLinesFromJar(
        jarPath: String?,
        targetClassName: String
    ): Map<String, Pair<Int, Int>> {
        val methodMap: MutableMap<String, Pair<Int, Int>> = HashMap()
        val dexClassName = "L" + targetClassName.replace(".", "/") + ";"

        try {
            val file = File(jarPath)
            if (!file.exists()) return methodMap

            // 1. 使用 DexFileFactory 加载整个容器（JAR/ZIP）
            val container = DexFileFactory.loadDexContainer(file, Opcodes.getDefault())

            // 2. 遍历容器内所有的 dex 入口名称 (如 classes.dex, classes2.dex ...)
            for (entryName in container.dexEntryNames) {
                val dexFile = container.getEntry(entryName) ?: continue

                println("正在解析 Dex 入口: $entryName, 类数量: ${dexFile.classes.size}")

                // 3. 寻找目标类
                for (classDef in dexFile.classes) {
                    if (classDef.type == dexClassName) {
                        println("命中目标类: ${classDef.type} (位于 $entryName)")
                        parseClassMethods(classDef, methodMap)
                        return methodMap // 找到后立即返回
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
            if (methodKey == "loop[]") {
                LOG.info("gsd-gsd parse loop")
            }
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

    private fun generateMethodLineMap(psiFile: PsiFile): Map<String, Pair<Int, Int>> {
        return runReadAction {
            val methodMap = mutableMapOf<String, Pair<Int, Int>>()
            val project = psiFile.project
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@runReadAction methodMap

            // 使用访问者模式遍历所有方法节点
            psiFile.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethod(method: PsiMethod) {
                    super.visitMethod(method)
                    val paramsList = method.parameterList.parameters.joinToString(", ") { param ->
                        getDexCompatibleTypeName(param.type)
                    }

                    val methodKey = "${method.name}[$paramsList]"

                    // 过滤掉静态构造
                    if (method.name != "<clinit>") {
                        // 寻找方法体内第一个真正的语句（Statement）
                        val firstStatement = PsiTreeUtil.getChildOfType(method.body, PsiStatement::class.java)
                        val startOffset = firstStatement?.textRange?.startOffset ?: method.nameIdentifier?.textRange?.startOffset ?: method.textRange.startOffset
                        val startLine = document.getLineNumber(startOffset) + 1
                        val endLine = document.getLineNumber(method.textRange.endOffset) + 1
                        methodMap[methodKey] = startLine to endLine
                    }
                }
            })
            methodMap
        }
    }

    private val primitiveMapping = mapOf(
        "boolean" to "Z",
        "byte"    to "B",
        "char"    to "C",
        "short"   to "S",
        "int"     to "I",
        "long"    to "J",
        "float"   to "F",
        "double"  to "D",
        "void"    to "V"
    )

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

fun main() {
    val parser = DexParser()
    val className = "android.view.View"
    val sourceJar = "/Users/bytedance/Desktop/xiaomi_framework.jar"
    val runtimeJar = "/Users/bytedance/Desktop/runtime_framework.jar"

//    println("开始解析...")
//    val mapping = parser.getMethodMap(className, sourceJar, runtimeJar)
//    mapping?.forEach { (method, info) ->
//        println("方法: $method")
//        println("  source: ${info.sourceStart} -> ${info.sourceEnd}")
//        println("  runtime: ${info.runtimeStart} -> ${info.runtimeEnd}")
//    }
}