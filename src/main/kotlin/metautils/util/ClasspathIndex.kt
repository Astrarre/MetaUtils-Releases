package metautils.util

import metautils.asm.opCode
import metautils.asm.readToClassNode
import metautils.types.MethodDescriptor
import metautils.types.classFileName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.lang.reflect.Method
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class MethodEntry(val id: MethodIdentifier, val access: Int) {
//    val descriptorParsed = lazy { MethodDescriptor.fromDescriptorString(id.descriptor) }
}

data class MethodIdentifier(val name: String, val descriptor: String)

data class ClassEntry(
    val methods: Map<MethodIdentifier, MethodEntry>,
    val superClass: QualifiedName?,
    val superInterfaces: List<QualifiedName>,
    val access: Int,
    val name: QualifiedName
)

private val ClassEntry.directSuperTypes: List<QualifiedName>
    get() = if (superClass != null) superInterfaces + superClass else superInterfaces

private const val DotClassLength = 6

/**
 * class names use slash/separated/format
 */
class ClasspathIndex @PublishedApi internal constructor(
    classPath: List<Path>,
    additionalEntries: Map<QualifiedName, ClassEntry>
) :
    AutoCloseable {

    companion object {
        fun <T> index(
            classPath: List<Path>, additionalEntries: Map<QualifiedName, ClassEntry>,
            usage: (ClasspathIndex) -> T
        ): T = ClasspathIndex(classPath, additionalEntries).let {
            val result = usage(it)
            it.close()
            result
        }
    }

    private data class PathInfo(val classes: List<Pair<QualifiedName, Path>>, val openedJar: FileSystem?)

    private val classpathMap: Map<QualifiedName, Path>
    private val openedJars: List<FileSystem>
    private val classesCache = ConcurrentHashMap(additionalEntries)

    init {
        val classes = classPath.map { it.getClassPaths() }
        classpathMap = classes.flatMap { it.classes }.toMap()
        openedJars = classes.mapNotNull { it.openedJar }
    }

    override fun close() {
        openedJars.forEach { it.close() }
    }


    private fun Path.toQualifiedName() = toString().let { it.substring(1, it.length - DotClassLength) }
        .toQualifiedName(slashQualified = true)

    // We store the paths to all non-jdk libraries, and later on we parse them on demand and cache the result.
    private fun Path.getClassPaths(): PathInfo = when {
        !exists() -> PathInfo(listOf(), null)
        isDirectory() -> PathInfo(recursiveChildren().filter { it.isExecutableClassfile() }
            .map { it.relativize(this).toQualifiedName() to it }
            .toList(), openedJar = null)
        hasExtension(".jar") -> {
            val jar = FileSystems.newFileSystem(this, null)
            PathInfo(jar.getPath("/").walk().filter { it.isExecutableClassfile() }
                .map { it.toQualifiedName() to it }
                .toList(), openedJar = jar)
        }
        else -> error("Got a classpath element which is not a jar or directory: $this")
    }

//    private inline fun <T> ClassPath.open(usage: (Path) -> T): T {
//        return if (jarIn == null) usage(path)
//        else {
//            synchronized(this) {
//                jarIn.openJar { usage(it.getPath(path.toString())) }
//            }
//        }
//    }

    private fun readClassEntry(path: Path): ClassEntry {
        val classNode = readToClassNode(path)
        val name = classNode.name.toQualifiedName(slashQualified = true)
        return ClassEntry(
            methods = classNode.methods.map {
                val id = MethodIdentifier(it.name, it.desc)
                id to MethodEntry(id, it.access)
            }.toMap(),
            superClass = classNode.superName.toQualifiedName(slashQualified = true),
            superInterfaces = classNode.interfaces.map { it.toQualifiedName(slashQualified = true) },
            access = classNode.getActualAccess(),
            name = name
        )
    }

    // If we just do .access it won't include the "static" modifier of static inner classes. That information only exists in the INNERCLASS field inside the class.
    private fun ClassNode.getActualAccess(): Int {
        val selfInnerClassInformation = innerClasses.find { it.name == this.name } ?: return access
        // Make sure the "static" opcode information is added when it exists
        if (selfInnerClassInformation.access opCode Opcodes.ACC_STATIC) return access or Opcodes.ACC_STATIC
        else return access
    }

    private fun createJdkClassEntry(className: QualifiedName): ClassEntry {
        val clazz = Class.forName(className.toDotString())
        return ClassEntry(
            methods = clazz.methods.map {
                val id = MethodIdentifier(it.name, getMethodDescriptor(it))
                id to MethodEntry(id, it.modifiers)
            }.toMap(),
            superInterfaces = clazz.interfaces.map { it.name.toQualifiedName(slashQualified = false) },
            superClass = clazz.superclass?.name?.toQualifiedName(slashQualified = false),
            access = clazz.modifiers, // I think this is the same
            name = className
        )
    }

    private fun createNonJdkClassEntry(className: QualifiedName): ClassEntry {
        val path = classpathMap[className]
            ?: error("Attempt to find class not in the specified classpath: $className")
        return readClassEntry(path)
    }


    private fun QualifiedName.isJavaClass(): Boolean = packageName.startsWith("java")
            || packageName.startsWith("javax")

    private fun getClassEntry(className: QualifiedName): ClassEntry = classesCache.computeIfAbsent(className) {
        if (className.isJavaClass()) createJdkClassEntry(className) else createNonJdkClassEntry(className)
    }

    fun getMethod(className: QualifiedName, methodName: String, methodDescriptor: MethodDescriptor): MethodEntry? {
        return getClassEntry(className).methods[MethodIdentifier(methodName, methodDescriptor.classFileName)]
    }

    fun accessOf(className: QualifiedName) = getClassEntry(className).access


    fun getSuperTypesRecursively(className: QualifiedName): Set<QualifiedName> {
        return (getSuperTypesRecursivelyImpl(className) + QualifiedName.Object).toSet()
    }

    private fun getSuperTypesRecursivelyImpl(className: QualifiedName): List<QualifiedName> {
        val directSupers = getClassEntry(className).directSuperTypes
        return (directSupers + directSupers.filter { it != QualifiedName.Object }
            .flatMap { getSuperTypesRecursivelyImpl(it) })
    }

    private fun getDescriptorForClass(c: Class<*>): String {
        if (c.isPrimitive) {
            return when (c.name) {
                "byte" -> "B"
                "char" -> "C"
                "double" -> "D"
                "float" -> "F"
                "int" -> "I"
                "long" -> "J"
                "short" -> "S"
                "boolean" -> "Z"
                "void" -> "V"
                else -> error("Unrecognized primitive $c")
            }
        }
        if (c.isArray) return c.name.replace('.', '/')
        return ('L' + c.name + ';').replace('.', '/')
    }

    private fun getMethodDescriptor(m: Method): String {
        val s = buildString {
            append('(')
            for (c in m.parameterTypes) {
                append(getDescriptorForClass(c))
            }
            append(')')
        }
        return s + getDescriptorForClass(m.returnType)
    }


}