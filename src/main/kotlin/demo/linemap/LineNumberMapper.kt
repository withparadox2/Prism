package demo.linemap


import com.intellij.openapi.vfs.VirtualFile


interface LineNumberMapper {
    fun mapSourceToJar(file: VirtualFile, sourceLine: Int): Int?
    fun mapJarToSource(file: VirtualFile, jarLine: Int): Int?
}