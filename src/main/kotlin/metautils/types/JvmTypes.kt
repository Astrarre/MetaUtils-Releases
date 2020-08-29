package metautils.types

import metautils.internal.jvmTypeFromDescriptorString
import metautils.internal.methodDescriptorFromDescriptorString
import metautils.internal.property
import metautils.internal.visiting
import metautils.util.*

typealias JvmReturnType = JvmReturnTypeGen<*>
typealias JvmType = JvmTypeGen<*>
typealias JvmPrimitiveType = JvmPrimitiveTypes<*>


data class ObjectType internal constructor(val fullClassName: QualifiedName) :
    JvmTypeGen<ObjectType>(), Mappable<ObjectType> by property(fullClassName, ::ObjectType) {
    override fun toString() = fullClassName.shortName.toDotString()

    companion object {
        val Object = ObjectType(QualifiedName.Object)

        fun fromClassName(name: String, slashQualified: Boolean = true): ObjectType {
            return fromClassName(QualifiedName.fromClassName(name, slashQualified))
        }

        fun fromClassName(name: QualifiedName): ObjectType {
            return if (name == QualifiedName.Object) Object else ObjectType(name)
        }
    }

}

data class ArrayType(val componentType: JvmType) : JvmTypeGen<ArrayType>(),
    Mappable<ArrayType> by property(componentType, constructor = ::ArrayType) {
    override fun toString() = "$componentType[]"
//    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(componentType = componentType.map(mapper))
}

sealed class JvmPrimitiveTypes<This : JvmPrimitiveTypes<This>> : JvmTypeGen<This>(),
    Leaf<This> {
    object Byte : JvmPrimitiveTypes<Byte>() {
        override fun toString() = "byte"
    }

    object Char : JvmPrimitiveTypes<Char>() {
        override fun toString() = "char"
    }

    object Double : JvmPrimitiveTypes<Double>() {
        override fun toString() = "double"
    }

    object Float : JvmPrimitiveTypes<Float>() {
        override fun toString() = "float"
    }

    object Int : JvmPrimitiveTypes<Int>() {
        override fun toString() = "int"
    }

    object Long : JvmPrimitiveTypes<Long>() {
        override fun toString() = "long"
    }

    object Short : JvmPrimitiveTypes<Short>() {
        override fun toString() = "short"
    }

    object Boolean : JvmPrimitiveTypes<Boolean>() {
        override fun toString() = "boolean"
    }
}

sealed class JvmReturnTypeGen<out This : JvmReturnTypeGen<This>> : /*Descriptor(classFileName),*/
    Mappable<This> {
    companion object {
        val Void = VoidJvmReturnType
    }
}


object VoidJvmReturnType : JvmReturnTypeGen<VoidJvmReturnType>(), Leaf<VoidJvmReturnType> {
    override fun toString() = "void"
}

sealed class JvmTypeGen<This : JvmTypeGen<This>> : JvmReturnTypeGen<This>() {
    companion object {
        fun fromDescriptorString(descriptor: String) = jvmTypeFromDescriptorString(descriptor)
    }
}


data class MethodDescriptor internal constructor(
    val parameterDescriptors: List<JvmType>,
    val returnDescriptor: JvmReturnType
) : Visitable by visiting(parameterDescriptors, returnDescriptor) {
    companion object {
        fun fromDescriptorString(descriptor: String) = methodDescriptorFromDescriptorString(descriptor)
    }

    override fun toString() = "(${parameterDescriptors.joinToString(", ")}): $returnDescriptor"
}

val JvmReturnType.classFileName: String
    get() = when (this) {
        is ObjectType -> "L${fullClassName.toSlashString()};"
        is ArrayType -> "[" + componentType.classFileName
        JvmPrimitiveTypes.Byte -> "B"
        JvmPrimitiveTypes.Char -> "C"
        JvmPrimitiveTypes.Double -> "D"
        JvmPrimitiveTypes.Float -> "F"
        JvmPrimitiveTypes.Int -> "I"
        JvmPrimitiveTypes.Long -> "J"
        JvmPrimitiveTypes.Short -> "S"
        JvmPrimitiveTypes.Boolean -> "Z"
        else -> "V"
    }

val MethodDescriptor.classFileName get() = "(${parameterDescriptors.joinToString("") { it.classFileName }})${returnDescriptor.classFileName}"