@file:Suppress("UNCHECKED_CAST")

package metautils.util

import metautils.internal.visitNamesInternal


interface Mappable<out T : Mappable<T, M>, M> {
    fun map(mapper: (M) -> M): T
}

//fun <T : Mappable<T,M>, M> T.remap(remapper : (M) -> M) : T= map(remapper)

private fun <T, R> Iterable<T>.collectionMap(map: (T) -> R) = map(map)

fun <T : Mappable<T, M>, M, I :Iterable<T>> I.mapElements(mapper: (M) -> M): List<T> {
    return this.collectionMap { it.map(mapper) }
}


interface Visitable {
    fun visitDirectChildren(visitor: (Visitable) -> Unit)
}

interface Tree<out T : Tree<T, M>, M> : Visitable, Mappable<T, M>

interface VisitLeaf : Visitable {
    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {}
}

interface Leaf<T : Leaf<T, M>, M> : VisitLeaf, Mappable<T, M> {
    override fun map(mapper: (M) -> M): T {
        return this as T
    }
}

typealias NameTree<T> = Tree<T,QualifiedName>
typealias NameMappable<T> = Mappable<T, QualifiedName>
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



