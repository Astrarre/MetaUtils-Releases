@file:Suppress("SimplifyBooleanWithConstants", "DataClassPrivateConstructor")

package metautils.util

import metautils.internal.ClassQualifier
import metautils.internal.PackageQualifier
import metautils.internal.parseQualifiedName
import metautils.internal.stringifyQualifiedName
import java.nio.file.Path
import java.nio.file.Paths

data class QualifiedName internal constructor(val packageName: PackageName, val shortName: ShortClassName) : /*VisitLeaf,*/ Leaf<QualifiedName>{
    companion object {
        fun fromClassName(
            name: String,
            slashQualified: Boolean = true,
            dollarQualified: Boolean = true
        ): QualifiedName =
            if (name == ClassNames.Object) Object else parseQualifiedName(slashQualified, dollarQualified, name)

        fun fromPackageAndShortName(packageName: PackageName, shortName: ShortClassName): QualifiedName {
            return if (packageName == Object.packageName
                && shortName == Object.shortName
            ) Object
            else QualifiedName(packageName, shortName)
        }

        val Object = parseQualifiedName(slashQualified = true, dollarQualified = true, name = ClassNames.Object)

    }

    fun toString(slashQualified: Boolean = true, dollarQualified: Boolean = true): String {
        return stringifyQualifiedName(dollarQualified, slashQualified)
    }

    override fun toString(): String = toDotString(dollarQualified = true)

}

sealed class AbstractQualifiedString {
    abstract val components: List<String>
}

data class PackageName private constructor(override val components: List<String>) : AbstractQualifiedString() {
    companion object {
        val Empty = PackageName(listOf())
        fun fromPackageString(string: String, slashQualified: Boolean = true): PackageName {
            if (string == "") return Empty
            return PackageName(string.split(PackageQualifier(slashQualified)))
        }

        fun fromComponents(components: List<String>) = if (components.isEmpty()) Empty
        else PackageName(components)
    }

    fun toString(slashQualified: Boolean): String = components.joinToString(PackageQualifier(slashQualified))
    val isEmpty = components.isEmpty()
    override fun toString(): String = toSlashString()
}

data class ShortClassName private constructor(override val components: List<String>) : AbstractQualifiedString() {
    companion object {
        fun fromClassString(name: String, dollarQualified: Boolean = true): ShortClassName {
            return ShortClassName(name.split(ClassQualifier(dollarQualified)))
        }

        fun fromComponents(components: List<String>) = ShortClassName(components)
    }

    fun toString(dollarQualified: Boolean = true): String =
        components.joinToString(ClassQualifier(dollarQualified))

    init {
        require(components.isNotEmpty())
    }

    override fun toString(): String = toDollarString()
}


fun String.toQualifiedName(slashQualified: Boolean, dollarQualified: Boolean = true): QualifiedName =
    QualifiedName.fromClassName(this, slashQualified, dollarQualified)

fun String.toSlashQualifiedName() = toQualifiedName(slashQualified = true)

fun String.prependToQualified(qualifiedString: PackageName) =
    qualifiedString.copy(components = this.prependTo(qualifiedString.components))


fun QualifiedName.packageStartsWith(vararg startingComponents: String): Boolean =
    packageName.startsWith(*startingComponents) == true

// JavaPoet, reflection
fun QualifiedName.toDotString(dollarQualified: Boolean = true) =
    toString(slashQualified = false, dollarQualified = dollarQualified)

// ASM, JVM
fun QualifiedName.toSlashString(dollarQualified: Boolean = true) =
    toString(slashQualified = true, dollarQualified = dollarQualified)

fun QualifiedName.innerClass(name: String) = copy(
    shortName = shortName.copy(components = shortName.components + name)
)

fun QualifiedName.outerClass() = copy(shortName = shortName.outerClass())

fun QualifiedName.thisToOuterClasses(): List<QualifiedName> = mutableListOf<QualifiedName>().apply {
    visitThisToOuterClasses { add(it) }
}

fun QualifiedName.toPath(suffix: String = ""): Path = packageName.toPath()
    .resolve(shortName.toDollarString() + suffix)


fun AbstractQualifiedString.startsWith(vararg startingComponents: String): Boolean {
    require(startingComponents.isNotEmpty())
    for (i in startingComponents.indices) {
        if (i >= components.size || startingComponents[i] != this.components[i]) return false
    }
    return true
}


operator fun PackageName.plus(other: PackageName) = copy(components = this.components + other.components)
fun PackageName.toDotString() = toString(slashQualified = false)
fun PackageName.toSlashString() = toString(slashQualified = true)
fun PackageName?.toPath(): Path = if (this == null || components.isEmpty()) Paths.get("") else {
    Paths.get(components[0], *components.drop(1).toTypedArray())
}


fun ShortClassName.toDollarString() = toString(dollarQualified = true)
fun ShortClassName.toDotString() = toString(dollarQualified = false)
fun ShortClassName.outerMostClass() = components[0]
fun ShortClassName.outerClass(): ShortClassName {
    require(components.size >= 2)
    return copy(components = components.dropLast(1))
}

fun ShortClassName.innerClasses() = components.drop(1)
fun ShortClassName.innermostClass() = components.last()

inline fun ShortClassName.mapOutermostClassName(mapping: (String) -> String): ShortClassName {
    val newOuterClass = mapping(outerMostClass())
    return copy(
        components = if (components.size == 1) listOf(newOuterClass)
        else newOuterClass.prependTo(innerClasses())
    )
}


private fun QualifiedName.visitThisToOuterClasses(visitor: (QualifiedName) -> Unit) {
    visitor(this)
    if (shortName.components.size > 1) outerClass().visitThisToOuterClasses(visitor)
}



