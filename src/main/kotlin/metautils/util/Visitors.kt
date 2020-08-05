package metautils.util


interface Mappable<T : Mappable<T>> {
//    fun map(thisRef)
}

interface Tree {
    fun visitDirectChildren(visitor: (Tree) -> Unit)
}

fun Tree.getDirectChildren() = mutableListOf<Tree>().apply { visitDirectChildren { add(it) } }

fun <T> Tree.mapNames(map: (QualifiedName) -> T): List<T> = mutableListOf<T>().apply { visitNames { add(map(it)) } }
fun Tree.getContainedNamesRecursively() : List<QualifiedName> = mutableListOf<QualifiedName>().apply { visitNames { add(it) } }
//inline fun <reified T, R> Tree.mapTypeOf(map: (T) -> R): List<R> = mutableListOf<R>().apply { visitNames { add(map(it)) } }

fun Tree.visitNames(visitor: (QualifiedName) -> Unit) {
    // This helps avoid visiting the same thing twice, which also means no infinite recursion
    val visited = mutableSetOf<Tree>()
    visitNames(visitor, visited)
}

private fun Tree.visitNames(visitor: (QualifiedName) -> Unit, visited: MutableSet<Tree>) {
    if (this in visited) return
    if (this is QualifiedName) visitor(this)
    visited.add(this)
    visitDirectChildren { it.visitNames(visitor, visited) }
}

//private val leaf = Branches(listOf())

interface Leaf : Tree {
    override fun visitDirectChildren(visitor: (Tree) -> Unit) {

    }
}

//class Branches internal constructor(val visitorDispatcher: ((Tree) -> Unit) -> Unit) : Tree {
//    override fun visitChildren(visitor: (Tree) -> Unit) {
//        visitorDispatcher(visitor)
//    }
//}

private inline fun branches(crossinline visitorDispatcher: ((Tree) -> Unit) -> Unit) = object : Tree {
    override fun visitDirectChildren(visitor: (Tree) -> Unit) {
        visitorDispatcher(visitor)
    }
}


//fun branch(branch: Tree) = Branches(listOf(branch))

//fun branch(branch: Tree) = object : Tree {
//    override fun visitChildren(visitor: (Tree) -> Unit) {
//        visitor(branch)
//    }
//
//}


fun branch(branch: Tree) = branches { it(branch) }

fun <T1 : Tree, L1 : Collection<T1>> branches(list1: L1?, single: Tree? = null) = branches {
    list1?.forEach(it)
    if (single != null) it(single)
}

fun <T1 : Tree, L1 : Collection<T1>, T2 : Tree, L2 : Collection<T2>> branches(list1: L1?, list2: L2?, single: Tree?) =
    branches {
        list1?.forEach(it)
        list2?.forEach(it)
        if (single != null) it(single)
    }

fun <T1 : Tree, L1 : Collection<T1>, T2 : Tree, L2 : Collection<T2>, T3 : Tree, L3 : Collection<T3>>
        branches(list1: L1?, list2: L2?, list3: L3?, single1: Tree?) = branches {
    list1?.forEach(it)
    list2?.forEach(it)
    list3?.forEach(it)
    if (single1 != null) it(single1)
}

fun <T1 : Tree, L1 : Collection<T1>, T2 : Tree, L2 : Collection<T2>, T3 : Tree, L3 : Collection<T3>, T4 : Tree, L4 : Collection<T4>>
        branches(list1: L1?, list2: L2?, list3: L3?, list4: L4?, single1: Tree?, single2: Tree? = null) = branches {
    list1?.forEach(it)
    list2?.forEach(it)
    list3?.forEach(it)
    list4?.forEach(it)
    if (single1 != null) it(single1)
    if (single2 != null) it(single2)
}

//fun ClassGenericType.visitNames(visitor: (QualifiedName) -> Unit) {
//    visitor(toJvmQualifiedName())
//    classNameSegments.forEach { segment -> segment.typeArguments?.forEach { it.visitNames(visitor) } }
//}
//
//fun JavaType<*>.visitNames(visitor: (QualifiedName) -> Unit) {
//    type.visitNames(visitor)
//    annotations.forEach { it.visitNames(visitor) }
//}
//
//fun JavaType<*>.getContainedNamesRecursively(): List<QualifiedName> =
//        mutableListOf<QualifiedName>().apply { visitNames { add(it) } }
//
//
//fun MethodDescriptor.visitNames(visitor: (QualifiedName) -> Unit) {
//    parameterDescriptors.forEach { it.visitNames(visitor) }
//    returnDescriptor.visitNames(visitor)
//}
//
//fun ReturnDescriptor.visitNames(visitor: (QualifiedName) -> Unit): Unit = when (this) {
//    is ObjectType -> visitor(fullClassName)
//    is ArrayType -> componentType.visitNames(visitor)
//    ReturnDescriptor.Void, is JvmPrimitiveType -> {
//    }
//}
//
//fun JavaAnnotation.visitNames(visitor: (QualifiedName) -> Unit) {
//    type.visitNames(visitor)
//    parameters.values.forEach { it.visitNames(visitor) }
//}
//
//fun AnnotationValue.visitNames(visitor: (QualifiedName) -> Unit): Unit = when (this) {
//    is AnnotationValue.Array -> components.forEach { it.visitNames(visitor) }
//    is AnnotationValue.Annotation -> annotation.visitNames(visitor)
//    is AnnotationValue.Primitive -> {
//    }
//    is AnnotationValue.Enum -> visitor(type.fullClassName)
//    is AnnotationValue.ClassType -> type.visitNames(visitor)
//}