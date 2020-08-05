package metautils.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path


fun String.includeIf(boolean: Boolean) = if (boolean) this else ""

fun <T> T.applyIf(boolean: Boolean, apply: (T) -> T): T {
    return if (boolean) apply(this) else this
}

fun <T, U> T.ifNotNull(obj: U?, apply: (T, U) -> T): T {
    return if (obj != null) apply(this, obj) else this
}


fun <T> List<T>.appendIfNotNull(value: T?) = if (value == null) this else this + value
fun <T> List<T>.prependIfNotNull(value: T?) = value?.prependTo(this) ?: this
fun <T> T.singletonList() = listOf(this)

fun <T : Any?> T.prependTo(list: List<T>): List<T> {
    val appendedList = ArrayList<T>(list.size + 1)
    appendedList.add(this)
    appendedList.addAll(list)
    return appendedList
}

fun <T, R> Iterable<T>.flatMapNotNull(mapping: (T) -> Iterable<R?>): List<R> {
    val list = mutableListOf<R>()
    for (element in this) {
        mapping(element).forEach { if (it != null) list.add(it) }
    }
    return list
}

val <K, V> List<Pair<K, V>>.keys get() = map { it.first }
val <K, V> List<Pair<K, V>>.values get() = map { it.second }
fun <K, V> List<Pair<K, V>>.mapValues(mapper: (V) -> V) = map { (k, v) -> k to mapper(v) }

inline fun <T> recursiveList(seed: T?, getter: (T) -> T?): List<T> {
    val list = mutableListOf<T>()
    var current: T? = seed
    while (current != null) {
        list.add(current)
        current = getter(current)
    }

    return list
}

fun downloadUtfStringFromUrl(url: String): String {
    return URL(url).openStream().use { String(it.readBytes(), StandardCharsets.UTF_8) }
}

fun downloadJarFromUrl(url: String, to: Path) {
    return URL(url).openStream().use { to.writeBytes(it.readBytes()) }
}

suspend fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(Dispatchers.IO) { f(it) } }.awaitAll()
}

private fun <T> Appendable.appendElement(element: T, transform: ((T) -> CharSequence)?) {
    when {
        transform != null -> append(transform(element))
        element is CharSequence? -> append(element)
        element is Char -> append(element)
        else -> append(element.toString())
    }
}


private fun <T, A : Appendable> Iterable<T>.joinTo(
    buffer: A,
    separator: Char,
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): A {
    buffer.append(prefix)
    var count = 0
    for (element in this) {
        if (++count > 1) buffer.append(separator)
        if (limit < 0 || count <= limit) {
            buffer.appendElement(element, transform)
        } else break
    }
    if (limit in 0 until count) buffer.append(truncated)
    buffer.append(postfix)
    return buffer
}


// Normal joinToString() but with a Char
fun <T> Iterable<T>.joinToString(
    separator: Char,
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): String {
    return joinTo(StringBuilder(), separator, prefix, postfix, limit, truncated, transform).toString()
}

fun <T1 : T, T2 : T, T3 : T, T4 : T, T5 : T, T> combineLists(
    list1: Collection<T1>?,
    list2: Collection<T2>?,
    list3: Collection<T3>?,
    el1: T4?,
    el2: T5?
) = mutableListOf<T>().apply {
    addAllNullable(list1)
    addAllNullable(list2)
    addAllNullable(list3)
    addNullable(el1)
    addNullable(el2)
}

private fun <T> MutableList<T>.addAllNullable(collection: Collection<T>?) {
    if (collection != null) addAll(collection)
}

private fun <T> MutableList<T>.addNullable(el: T?) {
    if (el != null) add(el)
}

fun <T> Iterable<T>.fastToSet() = if (this is Set<T>) this else toSet()