package io.rsbox.toolbox.updater.decompile

import io.rsbox.toolbox.updater.asm.ClassEntry
import io.rsbox.toolbox.updater.asm.ClassGroup
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.jetbrains.java.decompiler.main.extern.IResultSaver
import org.objectweb.asm.ClassWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class Decompiler(private val cls: ClassEntry) : IBytecodeProvider, IResultSaver {

    private var result: String? = null

    fun decompile(): String {
        val log = ByteArrayOutputStream()
        try {
            val options = hashMapOf<String, Any>()
            options.putAll(IFernflowerPreferences.DEFAULTS)

            options["dgs"] = "1"
            options["asc"] = "1"

            val fernflower = Fernflower(this, this, options, PrintStreamLogger(PrintStream(log)))

            val file = writeTempJar(cls)
            fernflower.addSource(file)
            fernflower.decompileContext()
            file.delete()
        } catch (e: Exception) {
        }

        if(result == null || result == "") {
            result = String(log.toByteArray())
        }

        return result!!
    }

    override fun getBytecode(externalPath: String, internalPath: String): ByteArray {
        return cls.toBytes()
    }


    override fun saveFolder(path: String?) {}
    override fun copyFile(source: String?, path: String?, entryName: String?) {}
    override fun closeArchive(path: String?, archiveName: String?) {}
    override fun copyEntry(source: String?, path: String?, archiveName: String?, entry: String?) {}
    override fun createArchive(path: String?, archiveName: String?, manifest: Manifest?) {}
    override fun saveDirEntry(path: String?, archiveName: String?, entryName: String?) {}

    override fun saveClassFile(
        path: String?,
        qualifiedName: String?,
        entryName: String?,
        content: String,
        mapping: IntArray?
    ) {
        result = content
    }

    override fun saveClassEntry(
        path: String?,
        archiveName: String?,
        qualifiedName: String?,
        entryName: String?,
        content: String?
    ) {
        result = content
    }


    private companion object {
        private fun writeTempJar(cls: ClassEntry): File {
            val file = File.createTempFile("temp-jar", ".jar")
            val jos = JarOutputStream(file.outputStream())
            jos.putNextEntry(JarEntry("${cls.name}.class"))
            jos.write(cls.toBytes(ClassWriter.COMPUTE_MAXS))
            jos.closeEntry()
            jos.close()
            return file
        }
    }
}