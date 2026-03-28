package com.bytedance.idea.plugin.prism

import com.intellij.openapi.application.PathManager
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

/**
 * 本地磁盘缓存管理器，用于持久化 jar 中解析出的类方法行号映射。
 *
 * 缓存结构:
 *   <IDE_SYSTEM>/prism-cache/<jarFileName>-<jarSize>-<jarLastModified>/
 *       <className>.bin   -- 二进制格式存储 Map<String, Pair<Int,Int>>
 *
 * 缓存失效策略：通过 jar 的文件大小 + lastModified 时间戳联合作为版本标识，
 * jar 文件发生变化时会自动使用新的缓存目录，旧缓存不会被主动读取。
 */
private object DiskCache {

    private const val CACHE_DIR_NAME = "prism-cache"
    private const val MAGIC = 0x505249534DL // "PRISM"
    private const val VERSION = 1

    /**
     * 获取缓存根目录：<IDE system path>/prism-cache/
     */
    private fun getCacheRoot(): File {
        return File(PathManager.getSystemPath(), CACHE_DIR_NAME)
    }

    /**
     * 根据 jar 文件属性生成版本化的缓存子目录。
     * 目录名格式: <jarFileName>-<fileSize>-<lastModified>
     * 当 jar 更新后，size 或 lastModified 变化，自然切换到新目录。
     */
    private fun getJarCacheDir(jarFile: File): File {
        val dirName = "${jarFile.name}-${jarFile.length()}-${jarFile.lastModified()}"
        return File(getCacheRoot(), dirName)
    }

    /**
     * 获取某个类对应的缓存文件路径。
     * 类名中的 '.' 和 '/' 替换为 '_' 避免路径问题。
     */
    private fun getCacheFile(jarFile: File, className: String): File {
        val safeClassName = className.replace('.', '_').replace('/', '_')
        return File(getJarCacheDir(jarFile), "$safeClassName.bin")
    }

    /**
     * 从磁盘缓存读取类的方法行号映射。
     * @return 缓存命中时返回 Map；缓存不存在或格式异常时返回 null。
     */
    fun load(jarFile: File, className: String): Map<String, Pair<Int, Int>>? {
        val cacheFile = getCacheFile(jarFile, className)
        if (!cacheFile.exists()) return null

        return try {
            DataInputStream(cacheFile.inputStream().buffered()).use { dis ->
                val magic = dis.readLong()
                val version = dis.readInt()
                if (magic != MAGIC || version != VERSION) return null

                val count = dis.readInt()
                val map = LinkedHashMap<String, Pair<Int, Int>>(count)
                repeat(count) {
                    val key = dis.readUTF()
                    val start = dis.readInt()
                    val end = dis.readInt()
                    map[key] = start to end
                }
                map
            }
        } catch (e: Exception) {
            LOG.warn("Prism: failed to load disk cache for $className", e)
            // 缓存损坏则删除
            cacheFile.delete()
            null
        }
    }

    /**
     * 将类的方法行号映射写入磁盘缓存。
     */
    fun save(jarFile: File, className: String, methodMap: Map<String, Pair<Int, Int>>) {
        try {
            val cacheFile = getCacheFile(jarFile, className)
            cacheFile.parentFile?.mkdirs()

            DataOutputStream(cacheFile.outputStream().buffered()).use { dos ->
                dos.writeLong(MAGIC)
                dos.writeInt(VERSION)
                dos.writeInt(methodMap.size)
                methodMap.forEach { (key, value) ->
                    dos.writeUTF(key)
                    dos.writeInt(value.first)
                    dos.writeInt(value.second)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Prism: failed to save disk cache for $className", e)
        }
    }
}

class LineMapGenerateHelper {
    private val jarPathToDexContainer = mutableMapOf<String, MultiDexContainer<out DexBackedDexFile>>()

    /**
     * 内存级缓存: key = "jarPath::className", value = 该类的方法行号映射。
     * 使用 ConcurrentHashMap 保证线程安全（调试场景可能有并发访问）。
     */
    private val runtimeMethodCache = ConcurrentHashMap<String, Map<String, Pair<Int, Int>>>()

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
     *
     * 优化策略（三级缓存）:
     *   1. 内存缓存（ConcurrentHashMap）—— 同一 session 内零开销命中
     *   2. 磁盘缓存（二进制文件）—— 跨 session / IDE 重启后快速恢复，无需重新解析 jar
     *   3. 原始解析（遍历 dex）—— 仅在首次访问时执行，结果同时回写到内存 + 磁盘
     */
    private fun getRuntimeMethodLineMap(
        jarPath: String?,
        targetClassName: String
    ): Map<String, Pair<Int, Int>> {
        jarPath ?: return emptyMap()

        val cacheKey = "$jarPath::$targetClassName"

        // --- 第 1 级：内存缓存 ---
        runtimeMethodCache[cacheKey]?.let { return it }

        val file = File(jarPath)
        if (!file.exists()) return emptyMap()

        // --- 第 2 级：磁盘缓存 ---
        DiskCache.load(file, targetClassName)?.let { cached ->
            LOG.info("Prism: disk cache hit for $targetClassName")
            runtimeMethodCache[cacheKey] = cached
            return cached
        }

        // --- 第 3 级：原始解析 ---
        val methodMap: MutableMap<String, Pair<Int, Int>> = mutableMapOf()
        val dexClassName = "L" + targetClassName.replace(".", "/") + ";"
        try {
            val container = jarPathToDexContainer[jarPath] ?: DexFileFactory.loadDexContainer(
                file,
                Opcodes.getDefault()
            ).also {
                jarPathToDexContainer[jarPath] = it
            }
            // 遍历容器内所有的 dex 入口名称 (如 classes.dex, classes2.dex ...)
            for (entryName in container.dexEntryNames) {
                val dexEntry = container.getEntry(entryName) ?: continue

                LOG.info("正在解析 Dex 入口: $entryName, 类数量: ${dexEntry.dexFile.classes.size}")

                for (classDef in dexEntry.dexFile.classes) {
                    if (classDef.type == dexClassName) {
                        parseClassMethods(classDef, methodMap)
                        // 解析完成，回写到内存缓存 + 磁盘缓存
                        runtimeMethodCache[cacheKey] = methodMap
                        DiskCache.save(file, targetClassName, methodMap)
                        LOG.info("Prism: parsed and cached $targetClassName (${methodMap.size} methods)")
                        return methodMap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 即使未找到目标类，也缓存空结果，避免反复遍历
        runtimeMethodCache[cacheKey] = methodMap
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
