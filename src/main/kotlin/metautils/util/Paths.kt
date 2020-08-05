package metautils.util

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.*
import java.util.jar.JarOutputStream
import kotlin.streams.asSequence
import kotlin.streams.toList

fun Path.exists() = Files.exists(this)
fun Path.deleteIfExists() = Files.deleteIfExists(this)
fun Path.delete() = Files.delete(this)
fun Path.deleteRecursively() = toFile().deleteRecursively()
inline fun <T> Path.openJar(usage: (FileSystem) -> T): T = FileSystems.newFileSystem(this, null).use(usage)
fun Path.walk(): Sequence<Path> = Files.walk(this).asSequence()

//fun Path.walk(): Sequence<Path> = Files.walk(this).asSequence()
fun <T> Path.walkJar(usage: (Sequence<Path>) -> T): T = openJar { usage(it.getPath("/").walk()) }
fun Path.createJar(contents: (JarOutputStream) -> Unit = {}) =
    JarOutputStream(Files.newOutputStream(this)).use(contents)

fun Path.isDirectory() = Files.isDirectory(this)
fun Path.createDirectory(): Path = Files.createDirectory(this)
fun Path.createDirectories(): Path = Files.createDirectories(this)
fun Path.createParentDirectories(): Path = parent.createDirectories()
fun Path.inputStream(): InputStream = Files.newInputStream(this)
fun Path.writeBytes(bytes: ByteArray): Path = Files.write(this, bytes)
fun Path.writeString(str: String): Path = Files.write(this, str.toByteArray())
fun Path.readToString() = Files.readAllBytes(this).toString(Charset.defaultCharset())
inline fun openJars(jar1: Path, jar2: Path, jar3: Path, usage: (FileSystem, FileSystem, FileSystem) -> Unit) =
    jar1.openJar { j1 -> jar2.openJar { j2 -> jar3.openJar { j3 -> usage(j1, j2, j3) } } }

fun Path.isClassfile() = hasExtension(".class")
fun Path.isExecutableClassfile() = isClassfile() && fileName.toString() != "module-info.class"
fun Path.directChildren() = Files.list(this).toList()
fun Path.recursiveChildren() = Files.walk(this).toList()
//inline fun <T> Path.recursiveChildren(usage: (Sequence<Path>) -> T): T = Files.walk(this).use {
//    usage(it.asSequence())
//}
//
//inline fun Path.forEachRecursiveChild(usage: (Path) -> Unit){
//    recursiveChildren { it.forEach(usage) }
//}

//inline fun Path.forEachRecursiveChild(usage: (Path) -> Unit){
//    recursiveChildren { it.forEach(usage) }
//}



fun Path.hasExtension(extension: String) = toString().endsWith(extension)

fun Path.unzipJar(
    destination: Path = toAbsolutePath().parent.resolve(fileName.toString().removeSuffix(".jar")),
    overwrite: Boolean = true
): Path {
    if (overwrite) destination.deleteRecursively()
    walkJar { paths ->
        paths.forEach {
            it.copyTo(destination.resolve(it.toString().removePrefix("/")))
        }
    }
    return destination
}

fun Path.convertDirToJar(destination: Path = parent.resolve("$fileName.jar"), overwrite: Boolean = true): Path {
    if (overwrite) destination.deleteIfExists()
    destination.createJar()
    println("Creating jar at $destination")
    destination.openJar { destJar ->
        this.walk().forEach {
            val relativePath = this.relativize(it)
            if (relativePath.toString().isEmpty()) return@forEach
            val targetPath = destJar.getPath(relativePath.toString())
            it.copyTo(targetPath)
        }
    }

    return destination
}

fun Path.copyTo(target: Path, overwrite: Boolean = true): Path? {
    var args = arrayOf<CopyOption>()
    if (overwrite) args += StandardCopyOption.REPLACE_EXISTING
    return Files.copy(this, target, *args)
}

//fun getJvmBootClasses(): List<Path> = if (System.getProperty("java.version").toInt() <= 8) {
//    System.getProperty("sun.boot.class.path").split(';').map { it.metautils.metautils.internal.toPath() }
//} else listOf<Path>() // It's part of class.path in jdk9+

fun String.toPath(): Path = Paths.get(this)

