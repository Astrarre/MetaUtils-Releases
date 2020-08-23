@file:Suppress("UNCHECKED_CAST")

package metautils.util

import metautils.internal.properties
import metautils.internal.property
import metautils.internal.visitNamesInternal
import kotlin.reflect.KClass


interface OldMappable<out T : OldMappable<T, M>, M> {
    fun map(mapper: (M) -> M): T
}

//fun <T : Mappable<T,M>, M> T.remap(remapper : (M) -> M) : T= map(remapper)

@PublishedApi
internal fun <T, R> Iterable<T>.collectionMap(map: (T) -> R) = map(map)

fun <T : OldMappable<T, M>, M, I : Iterable<T>> I.mapElementsOld(mapper: (M) -> M): List<T> {
    return this.collectionMap { it.map(mapper) }
}

inline fun <T : Mappable<T>, I : Iterable<T>, reified M : Mappable<M>> I.genericMapElements(noinline mapper: (M) -> M): List<T> {
    return this.collectionMap { it.genericMap(mapper) }
}

fun <T : Mappable<T>, I : Iterable<T>> I.mapElements(mapper: (QualifiedName) -> QualifiedName): List<T> =
    genericMapElements(mapper)

interface Visitable {
    fun visitDirectChildren(visitor: (Visitable) -> Unit)
}

interface Tree<out T : Tree<T, M>, M> : Visitable, OldMappable<T, M>

interface VisitLeaf : Visitable {
    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {}
}

interface Leaf<T : Leaf<T, M>, M> : VisitLeaf, OldMappable<T, M> {
    override fun map(mapper: (M) -> M): T {
        return this as T
    }
}

typealias NameTree<T> = Tree<T, QualifiedName>
typealias NameMappable<T> = OldMappable<T, QualifiedName>
typealias NameLeaf<T> = Leaf<T, QualifiedName>


fun Visitable.getDirectChildren() = mutableListOf<Visitable>().apply { visitDirectChildren { add(it) } }

//fun <T> Visitable.mapNames(map: (QualifiedName) -> T): List<T> =
//    mutableListOf<T>().apply { visitNames { add(map(it)) } }

fun Visitable.getContainedNamesRecursively(): List<QualifiedName> =
    mutableListOf<QualifiedName>().apply { visitNames { add(it) } }


fun Visitable.visitNames(visitor: (QualifiedName) -> Unit) {
    // This helps avoid visiting the same thing twice, which also means no infinite recursion
    val visited = mutableSetOf<Visitable>()
    visitNamesInternal(visitor, visited)
}


//data class UnsafeProperty(val value: Any?, val copyFunc: Any?.(Any?) -> Any?)

data class Properties<Owner>(val values: List<Any?>, val constructor: (List<Any?>) -> Owner)

sealed class Property<T, Owner> {
    abstract val copyFunc: (T) -> Owner
}

data class SingleProperty<T : Mappable<T>, Owner>(val value: T, override val copyFunc: (T) -> Owner) :
    Property<T, Owner>()

data class ListProperty<T : Mappable<T>, Owner>(
    val list: Iterable<T>,
    override val copyFunc: (List<T>) -> Owner
) : Property<List<T>, Owner>()

//TODO: kinda a useless abstraction, replace with simple values later to fight generics instead
//internal fun <T : BetterMappable<T>, Owner> Property<T, Owner>.toUnsafe() =
//    UnsafeProperty(value as BetterMappable<*>, copyFunc as Any?.(Any?) -> Any?)

interface Mappable<out This : Mappable<This>> : Visitable {
    val fields: List<Mappable<*>?>
    fun constructor(newValues: List<Mappable<*>?>): This

    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {
        for (field in fields) {
            if (field != null) visitor(field)
        }
    }
}
//TODO: general case interface includes a list of values and a copy func for the list of values,
// for ease of use provide a bunch of 1-property,2-property,3-property, etc type-safe adaptations.
// the adapations build a list out of the finite amount of properties, or query out of the list.
// this might be able to generalize out list properties!

interface MappableLeaf<This : Mappable<This>> : Mappable<This> {
    override val fields: List<Mappable<*>?> get() = listOf()
    override fun constructor(newValues: List<Mappable<*>?>): This = this as This
}

fun <T : Mappable<T>> T.map(mapping: (QualifiedName) -> QualifiedName): T = genericMap(mapping)

inline fun <T : Mappable<T>, reified M : Mappable<M>> T.genericMap(noinline mapping: (M) -> M): T {
    return unsafeMap(mapping, M::class) as T
}

//fun <T : Mappable<T>, M> T.map(mapping: (M) -> M): T {
//    return map {  }
//}

@PublishedApi
internal fun <M : Any> Any?.unsafeMap(mapping: (M) -> M, mappedClass: KClass<M>): Any? {
    this as Mappable<*>
    return constructor(fields.map {
        when {
            mappedClass.isInstance(it) -> mapping(it as M)
            it == null -> it
            else -> it.unsafeMap(mapping, mappedClass)
        }
    } as List<Mappable<*>?>)
}

//private fun <T : BetterMappable<T>> unsafeMap(
//    mappable: BetterMappable<T>,
//    mapping: (QualifiedName) -> QualifiedName
//): Any? {
//    val prop = mappable.property ?: return mappable
//
//    val copyFunc = prop.copyFunc as (Any?) -> Any?
//
//    val newValue: Any? = when (prop) {
//        is SingleProperty -> mapValue(prop.value, mapping)
//        is ListProperty<*, *> -> prop.list.map { mapValue(it, mapping) }
//    }
//    return copyFunc(newValue)
//}
//
//private fun mapValue(
//    value: Any?,
//    mapping: (QualifiedName) -> QualifiedName
//) = if (value is QualifiedName) mapping(value)
//else unsafeMap(value as BetterMappable<*>, mapping)

data class SomeDto(private val children: List<QualifiedName>) : Mappable<SomeDto> {
    override val fields = children

    override fun constructor(newValues: List<Mappable<*>?>): SomeDto {
        return SomeDto(newValues as List<QualifiedName>)
    }
}

data class SomeDto2(private val child: QualifiedName) : Mappable<SomeDto2> by property(child, ::SomeDto2)
/*data*/ class ClassSignature2(
    val typeArguments: List<QualifiedName>,
    val superInterfaces: List<QualifiedName>,
    val superClass: QualifiedName
) : Mappable<ClassSignature2> by properties(typeArguments, superInterfaces, superClass, ::ClassSignature2)


//private data class ListWrapper<T : BetterMappable<T>>(val list: List<T>) : BetterMappable<ListWrapper<T>>{
//    override val property: Property<*, ListWrapper<T>>? = Property()
//
//}

//fun <T : BetterMappable<T>>listProperty(list: Iterable<T>)  = Property

fun main() {
//    val dto = SomeDto(listOf(QualifiedName.Object, QualifiedName.fromClassName("foo/bar/baz")))
//    val newDto = dto.map { it.copy(packageName = PackageName.fromPackageString("x/d")) }

    val sig = ClassSignature2(
        listOf(QualifiedName.Object, QualifiedName.fromClassName("foo/bar/baz")),
        listOf(QualifiedName.Object, QualifiedName.fromClassName("a/b/c")),
        QualifiedName.fromClassName("the/lone/wolf")
    )
    val newDto = sig.map { it.copy(packageName = PackageName.fromPackageString("x/d")) }
    val x = 2
}