package metautils.signature

import metautils.internal.*
import metautils.types.JvmPrimitiveType
import metautils.types.JvmPrimitiveTypes
import metautils.types.classFileName
import metautils.util.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

typealias GenericReturnType = GenericReturnTypeGen<*>
typealias GenericTypeOrPrimitive = GenericTypeOrPrimitiveGen<*>
typealias ThrowableType = ThrowableTypeGen<*>

//TODO: get rid of all the data modifiers
//TODO: replace all instantations with factories

sealed class GenericReturnTypeGen<This : GenericReturnTypeGen<This>> : Mappable<This>

object VoidGenericReturnType : GenericReturnTypeGen<VoidGenericReturnType>(), Leaf<VoidGenericReturnType> {
    override fun toString(): String = "void"
}


sealed class TypeArgument : Mappable<TypeArgument> {
    companion object;
    /*data*/ class SpecificType(val type: GenericType, val wildcardType: WildcardType?) : TypeArgument(),
        Mappable<TypeArgument> by property(type, { SpecificType(it, wildcardType) }) {
        //        override fun equals(other: Any?): Boolean = super.equals(other)
//        override fun hashCode(): Int = super.hashCode()
        override fun toString(): String = "? $wildcardType ".includeIf(wildcardType != null) + type
//        override fun map(mapper: (QualifiedName) -> QualifiedName): TypeArgument {
//            return withType(type = type.map(mapper))
//        }
    }

    object AnyType : TypeArgument(), Leaf<TypeArgument> {
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
    GenericTypeOrPrimitiveGen<GenericsPrimitiveType>(), Leaf<GenericsPrimitiveType> {
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
) : Mappable<TypeArgumentDeclaration> by nullableProperties(classBound,interfaceBounds,{cls,int -> TypeArgumentDeclaration(name,cls,int)}) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name + " extends $classBound".includeIf(classBound != null) +
            " implements ".includeIf(interfaceBounds.isNotEmpty()) + interfaceBounds.joinToString(", ")

//    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(
//        classBound = classBound?.map(mapper),
//        interfaceBounds = interfaceBounds.mapElementsOld(mapper)
//    )
}


class ClassGenericType(
    val packageName: PackageName,
    /**
     * Outer class and then inner classes
     */
    val classNameSegments: List<SimpleClassGenericType>
) : ThrowableTypeGen<ClassGenericType>(), Mappable<ClassGenericType> by properties(
    QualifiedName.fromPackageNameAndClassSegments(packageName, classNameSegments),
    classNameSegments,
    {fullName, nameSegmentsWithTypeArgs -> fullName.toClassGenericType(nameSegmentsWithTypeArgs.map { it.typeArguments })}
) {

    companion object;

    override fun toString(): String = classNameSegments.joinToString("$")
//    override fun map(mapper: (QualifiedName) -> QualifiedName): ClassGenericType = remap(mapper)
}


/*data*/ class SimpleClassGenericType(val name: String, val typeArguments: List<TypeArgument>) :
    Mappable<SimpleClassGenericType> by property(typeArguments,{SimpleClassGenericType(name,it)}) {


//    override fun equals(other: Any?): Boolean = super.equals(other)
//    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String =
        if (typeArguments.isNotEmpty()) "$name<${typeArguments.joinToString(", ")}>" else name
}


data class ArrayGenericType(val componentType: GenericTypeOrPrimitive) : GenericTypeGen<ArrayGenericType>(),
    Mappable<ArrayGenericType> by property(componentType,::ArrayGenericType) {

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = "$componentType[]"
//    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(componentType = componentType.map(mapper))
}

/**
 * i.e. T, U
 */
data class TypeVariable(val name: String, val declaration: TypeArgumentDeclaration) : ThrowableTypeGen<TypeVariable>(),
    Mappable<TypeVariable> by property(declaration,{TypeVariable(name,it)}) {
    companion object;

    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = name
//    override fun map(mapper: (QualifiedName) -> QualifiedName) = copy(declaration = declaration.map(mapper))
}

fun TypeArgument.SpecificType.withType(type: GenericType) =
    TypeArgument.SpecificType(type = type, wildcardType)

fun SimpleClassGenericType.withArguments(args: List<TypeArgument>) = SimpleClassGenericType(name, args)