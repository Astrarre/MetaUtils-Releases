package metautils.testing

import metautils.util.*
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

@PublishedApi
internal class DummyClass


fun <T> getResources(path1: String, path2: String, usage: (Path, Path) -> T): T {
    getResource(path1) { r1 ->
//        return null!!
          return getResource(path2) { r2 ->
             usage(r1, r2)
        }
    }
}

inline fun <T> getResource(path: String, usage: (Path) -> T): T {
//    return null!!
    val classLoader = DummyClass::class.java.classLoader
    val uri = classLoader.getResource("dummyResource")!!.toURI()
    return uri.open { usage(Paths.get(uri).resolveSpecificResource(path)) }
}

@PublishedApi
internal inline fun <T> URI.open(usage: (URI) -> T): T {
    return try {
        if (scheme == "jar") FileSystems.newFileSystem(this, mapOf("create" to true)).use {
            usage(this)
        } else usage(this)
    } catch (e: FileSystemAlreadyExistsException) {
        usage(this)
    }
}

@PublishedApi
internal fun Path.resolveSpecificResource(path: String) = parent.resolve(path).also {
    check(it.exists()) {
        "Resource '$this' at $it does not exist. Other resources in resources directory ${it.parent}: " + it.parent.directChildren()
                .toList()
    }
}


fun verifyClassFiles(dir: Path, classpath: List<Path>) {
    require(dir.isDirectory())

    URLClassLoader(
            arrayOf(dir.toUri().toURL()) + classpath.map { it.toUri().toURL() }
            /* , this::class.java.classLoader*/
    ).use { classLoader ->
        dir.recursiveChildren().forEach {
            if (!it.isClassfile()) return@forEach
            val relativePath = dir.relativize(it)
            val className = relativePath.toString().replace(File.separator, ".").removeSuffix(".class")
            try {
                Class.forName(className, false, classLoader)
            } catch (e: Throwable) {
                println("Error in class $className")
                throw e
            }
        }
    }
}


//@DslMarker
//annotation class JarDsl

//@JarDsl
//fun testJars(vararg jars:  Path, init: TestJar.() -> Unit): TestJar {
//    return TestJar(
//        URLClassLoader(
//            jars.map { it.toUri().toURL() }.toTypedArray(),
//            DummyClass::class.java.classLoader
//        )
//    ).apply(init)
//}

//@JarDsl
//class TestJar(private val classLoader: ClassLoader) {
//    fun inClass(className: String, init: TestClass.() -> Unit = {}) =
//        TestClass(Class.forName(className, true, classLoader), classLoader).apply(init)
//
////    fun instance()
//}

//val Member.isPublic get() = Modifier.isPublic(this.modifiers)
//val Member.isPrivate get() = Modifier.isPrivate(this.modifiers)

//interface ClassContext {
//    val clazz: Class<*>
//    val instance: Any?
//
//    fun field(name: String) = clazz.getDeclaredField(name)
//    fun method(name: String, argTypes: List<Class<*>> = listOf()) = try {
//        clazz.getDeclaredMethod(name, *argTypes.toTypedArray())
//    } catch (e: NoSuchMethodException) {
//        clazz.getMethod(name, *argTypes.toTypedArray())
//    }
//
//    operator fun <T> String.invoke(argTypes: List<Class<*>> = listOf(), vararg args: Any?) =
//        method(this, argTypes).let {
//            it.isAccessible = true
//            it.invoke(instance, *args)
//        } as T
//
//    operator fun <T> get(name: String) = field(name).let {
//        it.isAccessible = true
//        it.get(instance)
//    } as T
//
//    operator fun set(name: String, value: Any) = field(name).let {
//        it.isAccessible = true
//        it.set(instance, value)
//    }
//}
//
//@JarDsl
//class TestClass(override val clazz: Class<*>, val classLoader: ClassLoader) : ClassContext {
//    override val instance: Any? = null
//    inline fun withInstance(className: String? = null, init: TestInstance.() -> Unit = {}) = TestInstance(clazz,
//        className?.let { Class.forName(className, true, classLoader) } ?: clazz.newInstance()
//    ).apply(init)
//}
//
//@JarDsl
//class TestInstance(override val clazz: Class<*>, override val instance: Any) : ClassContext