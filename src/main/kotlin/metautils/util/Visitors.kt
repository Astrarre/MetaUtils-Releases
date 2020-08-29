@file:Suppress("UNCHECKED_CAST")

package metautils.util

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



interface Visitable {
    fun visitDirectChildren(visitor: (Visitable) -> Unit)
}

interface Tree<out T : Tree<T, M>, M> : Visitable, OldMappable<T, M>

interface VisitLeaf : Visitable {
    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {}
}

interface OldLeaf<T : OldLeaf<T, M>, M> : VisitLeaf, OldMappable<T, M> {
    override fun map(mapper: (M) -> M): T {
        return this as T
    }
}

typealias NameTree<T> = Tree<T, QualifiedName>
typealias NameMappable<T> = OldMappable<T, QualifiedName>
typealias NameLeaf<T> = OldLeaf<T, QualifiedName>


fun Visitable.getDirectChildren() = mutableListOf<Visitable>().apply { visitDirectChildren { add(it) } }


fun Visitable.getContainedNamesRecursively(): List<QualifiedName> =
    mutableListOf<QualifiedName>().apply { visitNames { add(it) } }


fun Visitable.visitNames(visitor: (QualifiedName) -> Unit) {
    // This helps avoid visiting the same thing twice, which also means no infinite recursion
    val visited = mutableSetOf<Visitable>()
    visitNamesInternal(visitor, visited)
}



interface Mappable<out This : Mappable<This>> : Visitable {
    val fields: List<Mappable<*>?>
    fun constructor(newValues: List<Mappable<*>?>): This

    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {
        for (field in fields) {
            if (field != null) visitor(field)
        }
    }
}

interface Leaf<This : Mappable<This>> : Mappable<This> {
    override val fields: List<Mappable<*>?> get() = listOf()
    override fun constructor(newValues: List<Mappable<*>?>): This = this as This
}

fun <T : Mappable<T>> T.map(mapping: (QualifiedName) -> QualifiedName): T = genericMap(mapping)

inline fun <T : Mappable<T>, reified In : Mappable<In>, Out: Mappable<Out>> T.genericMap(noinline mapping: (In) -> Out): T {
    return unsafeMap(mapping, In::class) as T
}

inline fun <T : Mappable<T>, I : Iterable<T>, reified In : Mappable<In>, Out: Mappable<Out>>
        I.genericMapElements(noinline mapper: (In) -> Out): List<T> {
    return this.collectionMap { it.genericMap(mapper) }
}

fun <T : Mappable<T>, I : Iterable<T>> I.mapElements(mapper: (QualifiedName) -> QualifiedName): List<T> =
    genericMapElements(mapper)

@PublishedApi
internal fun <M : Any, T> Any?.unsafeMap(mapping: (M) -> T, mappedClass: KClass<M>): Any? {
    this as Mappable<*>
    return constructor(fields.map {
        when {
            mappedClass.isInstance(it) -> mapping(it as M)
            it == null -> it
            else -> it.unsafeMap(mapping, mappedClass)
        }
    } as List<Mappable<*>?>)
}
