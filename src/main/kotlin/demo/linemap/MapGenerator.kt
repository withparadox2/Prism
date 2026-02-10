package demo.linemap

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
        sourceJarPath: String,
        runtimeJarPath: String
    ): Map<String, MethodMapping>? {
        if (!map.containsKey(className)) {
            map[className] = calculateOffsets(className, sourceJarPath, runtimeJarPath)
        }
        return map[className]
    }

    private fun calculateOffsets(
        className: String,
        sourceJarPath: String,
        runtimeJarPath: String
    ): Map<String, MethodMapping> {
        val resultMap = mutableMapOf<String, MethodMapping>()
        println("call calculateOffsets source")
        // 1. 获取模拟器（Source）端的行号范围：Map<MethodKey, Pair<Start, End>>
        val sourceMap = getMethodStartLinesFromJar(sourceJarPath, className)

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
                // 如果手机端没这个方法，可以标记为 0 偏移或记录错误
                System.err.println("Method $methodKey not found in runtime JAR")
                resultMap[methodKey] = MethodMapping(sourceRange.first, sourceRange.second, 0, 0)
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
            if (method.implementation != null && methodKey != "<clinit>[]") {
                var start = -1
                var end = -1
                for (item in method.implementation?.debugItems ?: emptyList()) {
                    if (item is LineNumber) {
                        val currentLine = item.lineNumber
                        if (start == -1) start = currentLine
                        end = currentLine // 不断更新，最后一次就是结束行
                    }
                }
                if (start != -1) {
                    methodMap[methodKey] = start to end
                }
            }
        }
    }
}

fun main() {
    val parser = DexParser()
    val className = "android.view.View"
    val sourceJar = "/Users/bytedance/Desktop/xiaomi_framework.jar"
    val runtimeJar = "/Users/bytedance/Desktop/runtime_framework.jar"

    println("开始解析...")
    val mapping = parser.getMethodMap(className, sourceJar, runtimeJar)
    mapping?.forEach { (method, info) ->
        println("方法: $method")
        println("  source: ${info.sourceStart} -> ${info.sourceEnd}")
        println("  runtime: ${info.runtimeStart} -> ${info.runtimeEnd}")
    }
}