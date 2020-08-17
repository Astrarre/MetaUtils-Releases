package metautils.signature

import metautils.internal.visiting
import metautils.internal.fromPackageNameAndClassSegments
import metautils.types.JvmPrimitiveType
import metautils.types.JvmPrimitiveTypes
import metautils.util.*


interface Signature : Visitable

//
//fun GenericReturnType.visitNames(visitor: (QualifiedName) -> Unit): Unit = when (this) {
//    is GenericsPrimitiveType, GenericReturnType.Void -> {
//    }
//    is GenericType -> visitNames(visitor)
//}

data class ClassSignature(
        val typeArguments: List<TypeArgumentDeclaration>?,
        val superClass: ClassGenericType,
        val superInterfaces: List<ClassGenericType>
) : Signature, Visitable by visiting(typeArguments, superInterfaces, superClass) {
    init {
        check(typeArguments == null || typeArguments.isNotEmpty())
    }

    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()


    override fun toString(): String = "<${typeArguments?.joinToString(", ")}> ".includeIf(typeArguments != null) +
            "(extends $superClass" + ", implements ".includeIf(superInterfaces.isNotEmpty()) +
            superInterfaces.joinToString(", ") + ")"
}

data class MethodSignature(
        val typeArguments: List<TypeArgumentDeclaration>?,
        val parameterTypes: List<GenericTypeOrPrimitive>,
        val returnType: GenericReturnType,
        val throwsSignatures: List<ThrowableType>
) : Signature, Visitable by visiting(typeArguments, parameterTypes, throwsSignatures, returnType) {
    init {
        check(typeArguments == null || typeArguments.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    companion object;

//    override fun visitNames(visitor: NameVisitor) {
//        typeArguments?.forEach { it.visitNames(visitor) }
//        parameterTypes.forEach { it.visitNames(visitor) }
//        returnType.visitNames(visitor)
//        throwsSignatures.forEach { it.visitNames(visitor) }
//    }

    override fun toString(): String = "<${typeArguments?.joinToString(", ")}> ".includeIf(typeArguments != null) +
            "(${parameterTypes.joinToString(", ")}): $returnType" +
            " throws ".includeIf(throwsSignatures.isNotEmpty()) + throwsSignatures.joinToString(", ")
}

typealias FieldSignature = GenericTypeOrPrimitive

sealed class TypeArgument : Visitable {
    companion object;
     data class SpecificType(val type: GenericType, val wildcardType: WildcardType?) : TypeArgument(), Visitable by visiting(type) {
//        override fun visitNames(visitor: NameVisitor) {
//            type.visitNames(visitor)
//        }

         override fun equals(other: Any?): Boolean = super.equals(other)
         override fun hashCode(): Int = super.hashCode()
        override fun toString(): String = "? $wildcardType ".includeIf(wildcardType != null) + type
    }

    object AnyType : TypeArgument(), VisitLeaf {
//        override fun visitNames(visitor: NameVisitor) {
//
//        }

        override fun toString(): String = "*"
    }
}

enum class WildcardType {
    Extends, Super;

    override fun toString(): String = when (this) {
        Extends -> "extends"
        Super -> "super"
    }
}

sealed class GenericReturnType : Visitable {
    object Void : GenericReturnType(), VisitLeaf {
//        override fun visitNames(visitor: NameVisitor) {
//
//        }

        override fun toString(): String = "void"
    }
}

sealed class GenericTypeOrPrimitive : GenericReturnType(), Signature {
    companion object
}

internal val baseTypesGenericsMap = mapOf(
        JvmPrimitiveTypes.Byte.classFileName to GenericsPrimitiveType.Byte,
        JvmPrimitiveTypes.Char.classFileName to GenericsPrimitiveType.Char,
        JvmPrimitiveTypes.Double.classFileName to GenericsPrimitiveType.Double,
        JvmPrimitiveTypes.Float.classFileName to GenericsPrimitiveType.Float,
        JvmPrimitiveTypes.Int.classFileName to GenericsPrimitiveType.Int,
        JvmPrimitiveTypes.Long.classFileName to GenericsPrimitiveType.Long,
        JvmPrimitiveTypes.Short.classFileName to GenericsPrimitiveType.Short,
        JvmPrimitiveTypes.Boolean.classFileName to GenericsPrimitiveType.Boolean
).mapKeys { it.key[0] }

class GenericsPrimitiveType private constructor(val primitive: JvmPrimitiveType) : GenericTypeOrPrimitive(), VisitLeaf {
//    override fun visitNames(visitor: NameVisitor) {
//
//    }

    override fun toString(): String = primitive.toString()

    companion object {
        val Byte = GenericsPrimitiveType(JvmPrimitiveTypes.Byte)
        val Char = GenericsPrimitiveType(JvmPrimitiveTypes.Char)
        val Double = GenericsPrimitiveType(JvmPrimitiveTypes.Double)
        val Float = GenericsPrimitiveType(JvmPrimitiveTypes.Float)
        val Int = GenericsPrimitiveType(JvmPrimitiveTypes.Int)
        val Long = GenericsPrimitiveType(JvmPrimitiveTypes.Long)
        val Short = GenericsPrimitiveType(JvmPrimitiveTypes.Short)
        val Boolean = GenericsPrimitiveType(JvmPrimitiveTypes.Boolean)
    }
}

sealed class GenericType : GenericTypeOrPrimitive() {
    companion object
}

sealed class ThrowableType : GenericType()


// Just for copy()
data class TypeArgumentDeclaration(
        val name: String,
        val classBound: GenericType?,
        val interfaceBounds: List<GenericType>
) : Visitable by visiting(interfaceBounds, classBound) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name + " extends $classBound".includeIf(classBound != null) +
            " implements ".includeIf(interfaceBounds.isNotEmpty()) + interfaceBounds.joinToString(", ")
}


data class ClassGenericType(
    val packageName: PackageName,
    /**
         * Outer class and then inner classes
         */
        val classNameSegments: List<SimpleClassGenericType>
) : ThrowableType(), Visitable by visiting(classNameSegments,
    QualifiedName.fromPackageNameAndClassSegments(packageName, classNameSegments)) {

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    companion object;


    override fun toString(): String = classNameSegments.joinToString("$")
}


data class SimpleClassGenericType(val name: String, val typeArguments: List<TypeArgument>?) : Visitable by visiting(typeArguments) {
    init {
        if (typeArguments != null) require(typeArguments.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String =
            if (typeArguments != null) "$name<${typeArguments.joinToString(", ")}>" else name
}


data class ArrayGenericType(val componentType: GenericTypeOrPrimitive) : GenericType(), Visitable by visiting(componentType) {

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = "$componentType[]"
}

/**
 * i.e. T, U
 */
data class TypeVariable(val name: String, val declaration: TypeArgumentDeclaration) : ThrowableType(), Visitable by visiting(declaration) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name
}

