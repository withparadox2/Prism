package demo.linemap

import com.intellij.openapi.vfs.VirtualFile

class FakeLineNumberMapper : LineNumberMapper {
    override fun mapSourceToJar(file: VirtualFile, sourceLine: Int): Int? {
        LOG.info("gsd-gsd call mapSourceToJar, file: $file, sourceLine: $sourceLine")
        return sourceLine + 10 // 示例：所有行号 +10
    }


    override fun mapJarToSource(file: VirtualFile, jarLine: Int): Int? {
        LOG.info("gsd-gsd call mapJarToSource, file: $file, jarLine: $jarLine")
        return jarLine - 10 // 示例：映射回源码
    }
}