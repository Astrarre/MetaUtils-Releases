@file:Suppress("UNCHECKED_CAST")

package metautils.api

import metautils.internal.visiting
import metautils.types.JvmType
import metautils.types.ObjectType
import metautils.signature.*
import metautils.util.*
import org.objectweb.asm.tree.AnnotationNode


// This is actually not a complete representation of java type since it doesn't have annotations in type arguments,
// but those are rarely used, only exist in newer versions of jetbrains annotations, and even modern decompilers
// don't know how to decompile them, so it's fine to omit them here
data class JavaType<out T : GenericReturnType>(val type: T, val annotations: List<JavaAnnotation>) :
    NameTree<JavaType<T>> , Visitable by visiting(annotations, type) {
    companion object {
        fun fromRawClassName(name: String) = ClassGenericType.fromRawClassString(name).noAnnotations()
    }
    override fun toString(): String = annotations.joinToString("") { "$it " } + type
    override fun map(mapper: (QualifiedName) -> QualifiedName): JavaType<T> = copy(type = type.map(mapper) as T)
}
typealias JavaClassType = JavaType<ClassGenericType>
//typealias JavaSuperType = JavaType<ClassGenericType>
typealias AnyJavaType = JavaType<GenericTypeOrPrimitive>
typealias JavaReturnType = JavaType<GenericReturnType>
typealias JavaThrowableType = JavaType<ThrowableType>

data class JavaAnnotation private constructor(val type: ObjectType, val parameters: Map<String, AnnotationValue>) :
    Visitable by visiting(parameters.values, type) {
    override fun toString(): String = "@$type"
    companion object {
        //TODO: cache @Nullable and @Nonull annotations
        fun fromAsmNode(node: AnnotationNode) =
            JavaAnnotation(JvmType.fromDescriptorString(node.desc) as ObjectType, parseRawAnnotationValues(node.values))
        fun fromRawJvmClassName(name: String) =
            JavaAnnotation(ObjectType.fromClassName(name, slashQualified = true), parameters = mapOf())

    }

//    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(type = type.map(mapper))
}

sealed class AnnotationValue : Visitable {
    class Array(val components: List<AnnotationValue>) : AnnotationValue(), Visitable by visiting(components)
    class Annotation(val annotation: JavaAnnotation) : AnnotationValue(), Visitable by visiting(annotation)
    sealed class Primitive : AnnotationValue(), VisitLeaf {
        abstract val primitive: Any

        class Num(override val primitive: Number) : Primitive()
        class Bool(override val primitive: Boolean) : Primitive()
        class Cha(override val primitive: Char) : Primitive()
        class Str(override val primitive: String) : Primitive()
    }

    class Enum(val type: ObjectType, val constant: String) : AnnotationValue(), Visitable by visiting(type)
    class ClassType(val type: JvmType) : AnnotationValue(), Visitable by visiting(type)
}

//fun <T : GenericReturnType> JavaType<T>.remap(mapper: (className: QualifiedName) -> QualifiedName) =
//    copy(type = type.map(mapper))





private fun parseRawAnnotationValues(keyValues: List<Any>?): Map<String, AnnotationValue> {
    if (keyValues == null) return mapOf()
    val map = mutableMapOf<String, Any>()
    var key: String? = null
    keyValues.forEachIndexed { index, kv ->
        if (index % 2 == 0) {
            // Key
            key = kv as String
        } else {
            // Value
            map[key!!] = kv
        }
    }
    return map.mapValues { (_, v) -> parseAnnotationValue(v) }
}

private fun parseAnnotationValue(value: Any): AnnotationValue = when (value) {
    is Number -> AnnotationValue.Primitive.Num(value)
    is Boolean -> AnnotationValue.Primitive.Bool(value)
    is Char -> AnnotationValue.Primitive.Cha(value)
    is String -> AnnotationValue.Primitive.Str(value)
    is org.objectweb.asm.Type -> AnnotationValue.ClassType(JvmType.fromDescriptorString(value.descriptor))
    is Array<*> -> {
        assert(value.size == 2)
        assert(value[0] is String)
        @Suppress("UNCHECKED_CAST")
        value as Array<String>

        val type = JvmType.fromDescriptorString(value[0]) as ObjectType
        AnnotationValue.Enum(type = type, constant = value[1])
    }
    is AnnotationNode -> AnnotationValue.Annotation(JavaAnnotation.fromAsmNode(value))
    is List<*> -> AnnotationValue.Array(value.map { parseAnnotationValue(it!!) })
    else -> error("Unexpected annotation value '$value' of type '${value::class.qualifiedName}'")
}
