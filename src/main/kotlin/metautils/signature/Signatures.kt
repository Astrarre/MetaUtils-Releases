package metautils.signature

import metautils.internal.SignatureReader
import metautils.internal.classSignatureFromAsmClassNode
import metautils.internal.methodSignatureFromAsmMethodNode
import metautils.internal.visiting
import metautils.util.Visitable
import metautils.util.includeIf
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

interface Signature : Visitable

/*data*/ class ClassSignature(
    val typeArguments: List<TypeArgumentDeclaration>,
    val superClass: ClassGenericType,
    val superInterfaces: List<ClassGenericType>
) : Signature, Visitable by visiting(typeArguments, superInterfaces, superClass) {

    companion object {
        //// If the type args are null it won't try to resolve type argument declarations when it can't
        fun fromAsmClassNode(node: ClassNode, outerClassTypeArgs: Iterable<TypeArgumentDeclaration>?): ClassSignature =
            classSignatureFromAsmClassNode(node, outerClassTypeArgs)

        fun fromSignatureString(signature: String, outerClassTypeArgs: Iterable<TypeArgumentDeclaration>?): ClassSignature =
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