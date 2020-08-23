package metautils.signature

import metautils.internal.*
import metautils.types.JvmPrimitiveType
import metautils.types.JvmPrimitiveTypes
import metautils.types.classFileName
import metautils.util.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

typealias GenericReturnType = GenericReturnTypeGen<*>
typealias ThrowableType = ThrowableTypeGen<*>


sealed class GenericReturnTypeGen<This : GenericReturnTypeGen<This>> : NameTree<This>

object VoidGenericReturnType : GenericReturnTypeGen<VoidGenericReturnType>(), NameLeaf<VoidGenericReturnType> {
    override fun toString(): String = "void"
}

interface Signature : Visitable

/*data*/ class ClassSignature(
    val typeArguments: List<TypeArgumentDeclaration>,
    val superClass: ClassGenericType,
    val superInterfaces: List<ClassGenericType>
) : Signature, Visitable by visiting(typeArguments, superInterfaces, superClass) {

    companion object {
        //// If the type args are null it won't try to resolve type argument declarations when it can't
        fun fromAsmClassNode(node: ClassNode, outerClassTypeArgs: Iterable<TypeArgumentDeclaration>?) =
            classSignatureFromAsmClassNode(node, outerClassTypeArgs)

        fun fromSignatureString(signature: String, outerClassTypeArgs: Iterable<TypeArgumentDeclaration>?) =
            SignatureReader(signature, outerClassTypeArgs).readClass()
    }

//    override fun equals(other: Any?): Boolean = super.equals(other)
//    override fun hashCode(): Int = super.hashCode()


    override fun toString(): String = "<${typeArguments.joinToString(", ")}> ".includeIf(typeArguments.isNotEmpty()) +
            "(extends $superClass" + ", implements ".includeIf(superInterfaces.isNotEmpty()) +
            superInterfaces.joinToString(", ") + ")"
}


/*data*/ class MethodSignature(
    val typeArguments: List<TypeArgumentDeclaration>,
    val parameterTypes: List<GenericTypeOrPrimitive>,
    val returnType: GenericReturnType,
    val throwsSignatures: List<ThrowableType>
) : Signature, Visitable by visiting(typeArguments, parameterTypes, throwsSignatures, returnType) {

//    override fun equals(other: Any?): Boolean = super.equals(other)
//    override fun hashCode(): Int = super.hashCode()

    companion object {
        fun fromAsmMethodNode(node: MethodNode, classTypeArgs: Iterable<TypeArgumentDeclaration>?) : MethodSignature =
            methodSignatureFromAsmMethodNode(node,classTypeArgs)

        fun fromSignatureString(signature: String, classTypeArgs: Iterable<TypeArgumentDeclaration>?): MethodSignature =
            SignatureReader(signature, classTypeArgs).readMethod()
    }

    override fun toString(): String = "<${typeArguments.joinToString(", ")}> ".includeIf(typeArguments.isNotEmpty()) +
            "(${parameterTypes.joinToString(", ")}): $returnType" +
            " throws ".includeIf(throwsSignatures.isNotEmpty()) + throwsSignatures.joinToString(", ")
}

typealias FieldSignature = GenericTypeOrPrimitive

fun TypeArgument.SpecificType.withType(type: GenericType) =
    TypeArgument.SpecificType(type = type, wildcardType)

sealed class TypeArgument : NameTree<TypeArgument> {
    companion object;
    /*data*/ class SpecificType(val type: GenericType, val wildcardType: WildcardType?) : TypeArgument(),
        Visitable by visiting(type) {
        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()
        override fun toString(): String = "? $wildcardType ".includeIf(wildcardType != null) + type
        override fun map(mapper: (QualifiedName) -> QualifiedName): TypeArgument {
            return withType(type = type.map(mapper))
        }
    }

    object AnyType : TypeArgument(), NameLeaf<TypeArgument> {

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

typealias GenericTypeOrPrimitive = GenericTypeOrPrimitiveGen<*>

//TODO: I think change the name of this class
sealed class GenericTypeOrPrimitiveGen<This : GenericTypeOrPrimitiveGen<This>> : GenericReturnTypeGen<This>(),
    Signature {
    companion object {
        fun fromFieldSignature(signature: String, classTypeArgs: Iterable<TypeArgumentDeclaration>?) =
            SignatureReader(signature, classTypeArgs).readField()
    }
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

//TODO: change the name of this class
class GenericsPrimitiveType private constructor(val primitive: JvmPrimitiveType) :
    GenericTypeOrPrimitiveGen<GenericsPrimitiveType>(), NameLeaf<GenericsPrimitiveType> {
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
typealias GenericType = GenericTypeGen<*>


sealed class GenericTypeGen<This : GenericTypeGen<This>> : GenericTypeOrPrimitiveGen<This>() {
    companion object
}

sealed class ThrowableTypeGen<This : ThrowableTypeGen<This>> : GenericTypeGen<This>()


// Just for copy()
data class TypeArgumentDeclaration(
    val name: String,
    val classBound: GenericType?,
    val interfaceBounds: List<GenericType>
) : NameMappable<TypeArgumentDeclaration>, Visitable by visiting(interfaceBounds, classBound) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name + " extends $classBound".includeIf(classBound != null) +
            " implements ".includeIf(interfaceBounds.isNotEmpty()) + interfaceBounds.joinToString(", ")

    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(
        classBound = classBound?.map(mapper),
        interfaceBounds = interfaceBounds.mapElementsOld(mapper)
    )
}


/*data*/ class ClassGenericType(
    val packageName: PackageName,
    /**
     * Outer class and then inner classes
     */
    val classNameSegments: List<SimpleClassGenericType>
) : ThrowableTypeGen<ClassGenericType>(), Visitable by visiting(
    classNameSegments,
    QualifiedName.fromPackageNameAndClassSegments(packageName, classNameSegments)
) {

    companion object;

    override fun toString(): String = classNameSegments.joinToString("$")
    override fun map(mapper: (QualifiedName) -> QualifiedName): ClassGenericType = remap(mapper)
}



data class SimpleClassGenericType(val name: String, val typeArguments: List<TypeArgument>) :
    Visitable by visiting(typeArguments) {


    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String =
        if (typeArguments.isNotEmpty()) "$name<${typeArguments.joinToString(", ")}>" else name
}


data class ArrayGenericType(val componentType: GenericTypeOrPrimitive) : GenericTypeGen<ArrayGenericType>(),
    Visitable by visiting(componentType) {

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = "$componentType[]"
    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(componentType = componentType.map(mapper))
}

/**
 * i.e. T, U
 */
data class TypeVariable(val name: String, val declaration: TypeArgumentDeclaration) : ThrowableTypeGen<TypeVariable>(),
    Visitable by visiting(declaration) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name
    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(declaration = declaration.map(mapper))
}

