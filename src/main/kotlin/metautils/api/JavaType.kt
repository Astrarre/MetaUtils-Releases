@file:Suppress("UNCHECKED_CAST", "DataClassPrivateConstructor")

package metautils.api

import metautils.internal.properties
import metautils.internal.property
import metautils.internal.visiting
import metautils.types.JvmType
import metautils.types.ObjectType
import metautils.signature.*
import metautils.util.*
import org.objectweb.asm.tree.AnnotationNode


// This is actually not a complete representation of java type since it doesn't have annotations in type arguments,
// but those are rarely used, only exist in newer versions of jetbrains annotations, and even modern decompilers
// don't know how to decompile them, so it's fine to omit them here
//TODO: cache Void and Object and primitive type creations, use an internal method for that.
data class JavaType<out T : GenericReturnType> private constructor(val type: T, val annotations: List<JavaAnnotation>) :
    Mappable<JavaType<T>>  by properties(type,annotations, {newType, ano -> JavaType(newType as T,ano)}) {
    companion object {
        val Object = withNoAnnotations(ClassGenericType.Object)
        val Void = withNoAnnotations(VoidGenericReturnType)
        val Int = withNoAnnotations(GenericsPrimitiveType.Int)

        //TODO: consider some form of StringConvertible?

        fun <T : GenericReturnType>fromTypeAndAnnotations(type: T, annotations: List<JavaAnnotation>): JavaType<T> = JavaType(type,annotations)
        fun fromRawClassName(name: String): JavaClassType = withNoAnnotations(ClassGenericType.withNoTypeArgs(name))
        fun fromRawJvmType(type: ObjectType): JavaClassType = withNoAnnotations(ClassGenericType.withNoTypeArgs(type))
        fun <T : GenericReturnType > withNoAnnotations(type: T): JavaType<T> = fromTypeAndAnnotations(type, listOf())
        fun  withNoAnnotations(name: QualifiedName, typeArgs: List<List<TypeArgument>>): JavaClassType =
            withNoAnnotations(ClassGenericType.fromNameAndTypeArgs(name,typeArgs))
    }
    override fun toString(): String = annotations.joinToString("") { "$it " } + type
//    override fun map(mapper: (QualifiedName) -> QualifiedName): JavaType<T> = copy(type = type.map(mapper) as T)
}
typealias JavaClassType = JavaType<ClassGenericType>
typealias AnyJavaType = JavaType<GenericTypeOrPrimitive>
typealias JavaReturnType = JavaType<GenericReturnType>
typealias JavaThrowableType = JavaType<ThrowableType>

/*data*/ class JavaAnnotation private constructor(val type: ObjectType,  parameters: List<Pair<String, AnnotationValue>>) :
    Mappable<JavaAnnotation> by properties(type,parameters.values,
        {newType, params -> JavaAnnotation(newType,params.mapIndexed { index, value -> parameters[index].first to value })}) {
    val parameters: Map<String,AnnotationValue> = parameters.toMap()
    override fun toString(): String = "@$type"
    companion object {
        //TODO: cache @Nullable and @Nonull annotations
        fun fromAsmNode(node: AnnotationNode) =
            JavaAnnotation(JvmType.fromDescriptorString(node.desc) as ObjectType, parseRawAnnotationValues(node.values).map { (k,v) -> k to v })
        fun fromRawJvmClassName(name: String) =
            JavaAnnotation(ObjectType.fromClassName(name, slashQualified = true), parameters = listOf())

    }
}

sealed class AnnotationValue : Mappable<AnnotationValue> {
    class Array(val components: List<AnnotationValue>) : AnnotationValue(), Mappable<AnnotationValue> by property(components,::Array)
    class Annotation(val annotation: JavaAnnotation) : AnnotationValue(), Mappable<AnnotationValue> by property(annotation,::Annotation)
    sealed class Primitive : AnnotationValue(), Leaf<AnnotationValue> {
        abstract val primitive: Any

        class Num(override val primitive: Number) : Primitive()
        class Bool(override val primitive: Boolean) : Primitive()
        class Cha(override val primitive: Char) : Primitive()
        class Str(override val primitive: String) : Primitive()
    }

    class Enum(val type: ObjectType, val constant: String) : AnnotationValue(), Mappable<AnnotationValue> by property(type,{Enum(it,constant)})
    class ClassType(val type: JvmType) : AnnotationValue(), Mappable<AnnotationValue> by property(type,::ClassType)
}




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
