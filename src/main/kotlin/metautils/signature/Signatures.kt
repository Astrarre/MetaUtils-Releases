package metautils.signature

import metautils.internal.fromPackageNameAndClassSegments
import metautils.types.jvm.JvmPrimitiveType
import metautils.util.*


interface Signature : Tree

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
) : Signature, Tree by branches(typeArguments, superInterfaces, superClass) {
    init {
        check(typeArguments == null || typeArguments.isNotEmpty())
    }

    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

//    override fun visitNames(visitor: NameVisitor, filter: VisitorFilter) {
//        typeArguments?.forEach { it.visitNames(visitor) }
//        superClass.visitNames(visitor)
//        superInterfaces.forEach { it.visitNames(visitor) }
//    }
//
//    override val children: Collection<Tree>
//        get() = TODO("Not yet implemented")

    override fun toString(): String = "<${typeArguments?.joinToString(", ")}> ".includeIf(typeArguments != null) +
            "(extends $superClass" + ", implements ".includeIf(superInterfaces.isNotEmpty()) +
            superInterfaces.joinToString(", ") + ")"
}

data class MethodSignature(
        val typeArguments: List<TypeArgumentDeclaration>?,
        val parameterTypes: List<GenericTypeOrPrimitive>,
        val returnType: GenericReturnType,
        val throwsSignatures: List<ThrowableType>
) : Signature, Tree by branches(typeArguments, parameterTypes, throwsSignatures, returnType) {
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

sealed class TypeArgument : Tree {
    companion object;
     data class SpecificType(val type: GenericType, val wildcardType: WildcardType?) : TypeArgument(), Tree by branch(type) {
//        override fun visitNames(visitor: NameVisitor) {
//            type.visitNames(visitor)
//        }

         override fun equals(other: Any?): Boolean = super.equals(other)
         override fun hashCode(): Int = super.hashCode()
        override fun toString(): String = "? $wildcardType ".includeIf(wildcardType != null) + type
    }

    object AnyType : TypeArgument(), Leaf {
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

sealed class GenericReturnType : Tree {
    object Void : GenericReturnType(), Leaf {
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
        JvmPrimitiveType.Byte.classFileName to GenericsPrimitiveType.Byte,
        JvmPrimitiveType.Char.classFileName to GenericsPrimitiveType.Char,
        JvmPrimitiveType.Double.classFileName to GenericsPrimitiveType.Double,
        JvmPrimitiveType.Float.classFileName to GenericsPrimitiveType.Float,
        JvmPrimitiveType.Int.classFileName to GenericsPrimitiveType.Int,
        JvmPrimitiveType.Long.classFileName to GenericsPrimitiveType.Long,
        JvmPrimitiveType.Short.classFileName to GenericsPrimitiveType.Short,
        JvmPrimitiveType.Boolean.classFileName to GenericsPrimitiveType.Boolean
).mapKeys { it.key[0] }

class GenericsPrimitiveType private constructor(val primitive: JvmPrimitiveType) : GenericTypeOrPrimitive(), Leaf {
//    override fun visitNames(visitor: NameVisitor) {
//
//    }

    override fun toString(): String = primitive.toString()

    companion object {
        val Byte = GenericsPrimitiveType(JvmPrimitiveType.Byte)
        val Char = GenericsPrimitiveType(JvmPrimitiveType.Char)
        val Double = GenericsPrimitiveType(JvmPrimitiveType.Double)
        val Float = GenericsPrimitiveType(JvmPrimitiveType.Float)
        val Int = GenericsPrimitiveType(JvmPrimitiveType.Int)
        val Long = GenericsPrimitiveType(JvmPrimitiveType.Long)
        val Short = GenericsPrimitiveType(JvmPrimitiveType.Short)
        val Boolean = GenericsPrimitiveType(JvmPrimitiveType.Boolean)
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
) : Tree by branches(interfaceBounds, classBound) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

//    override fun visitNames(visitor: NameVisitor) {
//        classBound?.visitNames(visitor)
//        interfaceBounds.forEach { it.visitNames(visitor) }
//    }

    override fun toString(): String = name + " extends $classBound".includeIf(classBound != null) +
            " implements ".includeIf(interfaceBounds.isNotEmpty()) + interfaceBounds.joinToString(", ")
}

//fun GenericType.visitNames(visitor: (QualifiedName) -> Unit) = when (this) {
//    is ClassGenericType -> visitNames(visitor)
//    is TypeVariable -> {
////        this.declaration
//    }
//    is ArrayGenericType -> componentType.visitNames(visitor)
//}


data class ClassGenericType(
    val packageName: PackageName?,
    /**
         * Outer class and then inner classes
         */
        val classNameSegments: List<SimpleClassGenericType>
) : ThrowableType(), Tree by  branches(classNameSegments,
    // TODO:  IMPORTANT: change parsing method to make packageName be empty instead of null when missing.
    QualifiedName.fromPackageNameAndClassSegments(packageName ?: PackageName.Empty, classNameSegments)) {
    init {
        check(classNameSegments.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    companion object;

//    override fun visitNames(visitor: NameVisitor) {
//        visitor(toJvmQualifiedName())
//        classNameSegments.forEach { segment -> segment.typeArguments?.forEach { it.visitNames(visitor) } }
//    }

    override fun toString(): String = /*"${packageName?.toSlashQualified()}/".includeIf(packageName != null) +*/
            classNameSegments.joinToString("$")
}


data class SimpleClassGenericType(val name: String, val typeArguments: List<TypeArgument>?) : Tree by branches(typeArguments) {
    init {
        if (typeArguments != null) require(typeArguments.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String =
            if (typeArguments != null) "$name<${typeArguments.joinToString(", ")}>" else name
}


data class ArrayGenericType(val componentType: GenericTypeOrPrimitive) : GenericType(), Tree by branch(componentType) {
//    override fun visitNames(visitor: NameVisitor) {
//        componentType.visitNames(visitor)
//    }

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = "$componentType[]"
}

/**
 * i.e. T, U
 */
data class TypeVariable(val name: String, val declaration: TypeArgumentDeclaration) : ThrowableType(), Tree by branch(declaration) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

//    override fun visitNames(visitor: NameVisitor) {
//        TODO("Not yet implemented")
//    }

    override fun toString(): String = name
}

