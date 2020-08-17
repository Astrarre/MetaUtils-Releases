package metautils.internal

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

internal fun <T1 : Visitable, L1 : Collection<T1>, T2 : Visitable, L2 : Collection<T2>> visiting(list1: L1?, list2: L2?, single: Visitable?) =
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

