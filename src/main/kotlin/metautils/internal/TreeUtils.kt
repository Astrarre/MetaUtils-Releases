@file:Suppress("UNCHECKED_CAST")

package metautils.internal

import metautils.util.Mappable
import metautils.util.QualifiedName
import metautils.util.Visitable

internal fun Visitable.visitNamesInternal(visitor: (QualifiedName) -> Unit, visited: MutableSet<Visitable>) {
    if (this in visited) return
    if (this is QualifiedName) visitor(this)
    visited.add(this)
    visitDirectChildren { it.visitNamesInternal(visitor, visited) }
}

private inline fun visiting(crossinline visitorDispatcher: ((Visitable) -> Unit) -> Unit) = object : Visitable {
    override fun visitDirectChildren(visitor: (Visitable) -> Unit) {
        visitorDispatcher(visitor)
    }
}

internal fun visiting(branch: Visitable) = visiting { it(branch) }

internal fun <T1 : Visitable, L1 : Collection<T1>> visiting(list1: L1?, single: Visitable? = null) = visiting {
    list1?.forEach(it)
    if (single != null) it(single)
}

internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>> visiting(
    list1: L1?,
    list2: L2?,
    single: Visitable?
) =
    visiting {
        list1?.forEach(it)
        list2?.forEach(it)
        if (single != null) it(single)
    }

internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>, T3 : Visitable, L3 : Collection<T3>>
        visiting(list1: L1?, list2: L2?, list3: L3?, single1: Visitable?) = visiting {
    list1?.forEach(it)
    list2?.forEach(it)
    list3?.forEach(it)
    if (single1 != null) it(single1)
}

internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>, T3 : Visitable, L3 : Collection<T3>, T4 : Visitable, L4 : Collection<T4>>
        visiting(list1: L1?, list2: L2?, list3: L3?, list4: L4?, single1: Visitable?, single2: Visitable? = null) =
    visiting {
        list1?.forEach(it)
        list2?.forEach(it)
        list3?.forEach(it)
        list4?.forEach(it)
        if (single1 != null) it(single1)
        if (single2 != null) it(single2)
    }


internal fun <T : Mappable<T>, Owner : Mappable<Owner>> property(value: T, constructor: (T) -> Owner): Mappable<Owner> =
    propertiesImpl(listOf(value)) { constructor(this[0] as T) }

internal fun <T : Mappable<T>, Owner : Mappable<Owner>> property(value: List<T>, constructor: (List<T>) -> Owner): Mappable<Owner> =
    properties1(value,constructor)

internal fun <T1 : Mappable<T1>, T2 : Mappable<T2>, S : Mappable<S>, Owner : Mappable<Owner>>
        properties(list1: List<T1>, list2: List<T2>, single: S, constructor: (List<T1>, List<T2>, S) -> Owner
): Mappable<Owner> {
    return properties3(list1, list2, single, constructor)
}

internal fun <T1 : Mappable<T1>, T2 : Mappable<T2>, Owner : Mappable<Owner>>
        properties(prop1: T1, prop2: T2, constructor: (T1,T2) -> Owner): Mappable<Owner> {
    return properties2(prop1, prop2, constructor)
}

internal fun <T1 : Mappable<T1>, T2 : Mappable<T2>, I : Iterable<T2>, Owner : Mappable<Owner>>
        properties(prop1: T1, prop2: I, constructor: (T1,I) -> Owner): Mappable<Owner> {
    return properties2(prop1, prop2, constructor)
}
internal fun <T1 : Mappable<T1>, T2 : Mappable<T2>, Owner : Mappable<Owner>>
        nullableProperties(prop1: T1?, prop2: List<T2>, constructor: (T1?,List<T2>) -> Owner): Mappable<Owner> {
    return properties2(prop1, prop2, constructor)
}

//data class Flat()

private fun calculateRanges(vararg elements: Any?): List<IntRange> {
    var currentIndex = 0
    return elements.map {
        when (it) {
            is Mappable<*>? -> {
                val range = currentIndex..currentIndex
                currentIndex++
                range
            }
            is Collection<*> -> {
                val range = currentIndex until currentIndex + it.size
                currentIndex += it.size
                range
            }
            else -> error("Only expecting lists or Mappables as elements")
        }
    }
}

private fun combineProperties(vararg properties: Any?): List<Mappable<*>?> {
    return mutableListOf<Mappable<*>?>().apply {
        for (property in properties) {
            when (property) {
                is Mappable<*>? -> {
                    add(property)
                }
                is Collection<*> -> {
                    check(property.firstOrNull() is Mappable<*>?) { "Only expecting lists of Mappables" }
                    addAll(property as Collection<Mappable<*>>)
                }
                else -> error("Only expecting lists or Mappables as elements")
            }
        }
    }
}

@Suppress("IMPLICIT_CAST_TO_ANY")
private fun <T> List<Mappable<*>?>.prop(range: IntRange, prop: T): T {
    return if (prop is Mappable<*>?) {
        check(range.first == range.last)
        this[range.first]
    } else {
        check(prop is Collection<*>)
        this.subList(range.first, range.last + 1)
    } as T
}

private inline fun <T1, T2, T3, Owner : Mappable<Owner>> properties3(
    prop1: T1,
    prop2: T2,
    prop3: T3,
    crossinline constructor: (T1, T2, T3) -> Owner
): Mappable<Owner> {
    val ranges = calculateRanges(prop1, prop2, prop3)
    return propertiesImpl(prop1, prop2, prop3) {
        constructor(prop(ranges[0], prop1), prop(ranges[1], prop2), prop(ranges[2], prop3))
    }
}

private inline fun <T1, T2, Owner : Mappable<Owner>> properties2(
    prop1: T1,
    prop2: T2,
    crossinline constructor: (T1, T2) -> Owner
): Mappable<Owner> {
    val ranges = calculateRanges(prop1, prop2)
    return propertiesImpl(prop1, prop2) {
        constructor(prop(ranges[0], prop1), prop(ranges[1], prop2))
    }
}

private inline fun <T1, Owner : Mappable<Owner>> properties1(
    prop1: T1,
    crossinline constructor: (T1) -> Owner
): Mappable<Owner> {
    val ranges = calculateRanges(prop1)
    return propertiesImpl(prop1) {
        constructor(prop(ranges[0], prop1))
    }
}



//
//internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>, T3 : Visitable, L3 : Collection<T3>>
//        visiting(list1: L1?, list2: L2?, list3: L3?, single1: Visitable?) = visiting {
//    list1?.forEach(it)
//    list2?.forEach(it)
//    list3?.forEach(it)
//    if (single1 != null) it(single1)
//}
//
//internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>, T3 : Visitable, L3 : Collection<T3>, T4 : Visitable, L4 : Collection<T4>>
//        visiting(list1: L1?, list2: L2?, list3: L3?, list4: L4?, single1: Visitable?, single2: Visitable? = null) =
//    visiting {
//        list1?.forEach(it)
//        list2?.forEach(it)
//        list3?.forEach(it)
//        list4?.forEach(it)
//        if (single1 != null) it(single1)
//        if (single2 != null) it(single2)
//    }

//private fun <Owner : BetterMappable<Owner>> properties(
//    values: List<BetterMappable<*>>,
//    lists: List<List<BetterMappable<*>>>,
//    constructor: (List<BetterMappable<*>?>) -> Owner
//) = object : BetterMappable<Owner> {
//    override val fields: List<BetterMappable<*>?> = combineLists(lists,values)
//    override fun constructor(newValues: List<BetterMappable<*>?>): Owner = constructor(newValues)
//}

private fun <Owner : Mappable<Owner>> propertiesImpl(
    vararg values: Any?,
    constructor: List<Mappable<*>?>.() -> Owner
) = propertiesImpl(combineProperties(*values),constructor)

private fun <Owner : Mappable<Owner>> propertiesImpl(
    values: List<Mappable<*>?>,
    constructor: List<Mappable<*>?>.() -> Owner
) = object : Mappable<Owner> {
    override val fields: List<Mappable<*>?> = values
    override fun constructor(newValues: List<Mappable<*>?>): Owner = constructor(newValues)
}