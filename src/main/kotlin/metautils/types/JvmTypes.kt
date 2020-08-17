package metautils.types

import metautils.internal.jvmTypeFromDescriptorString
import metautils.internal.methodDescriptorFromDescriptorString
import metautils.internal.visiting
import metautils.util.*

// Comes directly from the spec https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2


sealed class Descriptor(val classFileName: String) : Visitable {
    override fun equals(other: Any?) = other is Descriptor && other.classFileName == classFileName
    override fun hashCode() = classFileName.hashCode()
}

data class MethodDescriptor internal constructor(
    val parameterDescriptors: List<ParameterDescriptor<*>>,
    val returnDescriptor: JvmReturnType
) : Descriptor("(${parameterDescriptors.joinToString("") { it.classFileName }})${returnDescriptor.classFileName}"),
    Visitable by visiting(parameterDescriptors, returnDescriptor) {
    companion object {
        fun fromDescriptorString(descriptor: String) = methodDescriptorFromDescriptorString(descriptor)
    }

    override fun toString() = "(${parameterDescriptors.joinToString(", ")}): $returnDescriptor"
}


sealed class JvmReturnTypeGen<This : JvmReturnTypeGen<This>>(classFileName: String) : Descriptor(classFileName),
    NameMappable<This> {
    companion object {
        val Void = VoidJvmReturnType
    }
}

object VoidJvmReturnType : JvmReturnTypeGen<VoidJvmReturnType>("V"), Leaf<VoidJvmReturnType, QualifiedName> {
    override fun toString() = "void"
}

typealias JvmReturnType = JvmReturnTypeGen<*>

sealed class JvmTypeGen<This : JvmTypeGen<This>>(classFileName: String) : JvmReturnTypeGen<This>(classFileName) {
    companion object {
        fun fromDescriptorString(descriptor: String) = jvmTypeFromDescriptorString(descriptor)
    }
}

typealias JvmType = JvmTypeGen<*>

sealed class JvmPrimitiveTypes<This : JvmPrimitiveTypes<This>>(classFileName: String) : JvmTypeGen<This>(classFileName),
    NameLeaf<This> {
    object Byte : JvmPrimitiveTypes<Byte>("B") {
        override fun toString() = "byte"
    }

    object Char : JvmPrimitiveTypes<Char>("C") {
        override fun toString() = "char"
    }

    object Double : JvmPrimitiveTypes<Double>("D") {
        override fun toString() = "double"
    }

    object Float : JvmPrimitiveTypes<Float>("F") {
        override fun toString() = "float"
    }

    object Int : JvmPrimitiveTypes<Int>("I") {
        override fun toString() = "int"
    }

    object Long : JvmPrimitiveTypes<Long>("J") {
        override fun toString() = "long"
    }

    object Short : JvmPrimitiveTypes<Short>("S") {
        override fun toString() = "short"
    }

    object Boolean : JvmPrimitiveTypes<Boolean>("Z") {
        override fun toString() = "boolean"
    }
}

typealias JvmPrimitiveType = JvmPrimitiveTypes<*>

data class ObjectType internal constructor(val fullClassName: QualifiedName) :
    JvmTypeGen<ObjectType>("L${fullClassName.toSlashString()};"), Visitable by visiting(fullClassName) {
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

    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(fullClassName = mapper(fullClassName))

}

data class ArrayType(val componentType: JvmType) : JvmTypeGen<ArrayType>("[" + componentType.classFileName),
    Visitable by visiting(componentType) {
    override fun toString() = "$componentType[]"
    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(componentType = componentType.map(mapper))
}


typealias FieldType<T> = JvmTypeGen<T>
typealias ParameterDescriptor<T> = FieldType<T>

