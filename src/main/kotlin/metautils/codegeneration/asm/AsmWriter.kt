package metautils.codegeneration.asm

import metautils.api.JavaAnnotation
import metautils.api.AnnotationValue
import metautils.asm.AnnotateableVisitor
import metautils.asm.annotateable
import metautils.codegeneration.*
import metautils.signature.*
import metautils.types.jvm.JvmType
import metautils.types.jvm.MethodDescriptor
import metautils.types.jvm.ObjectType
import metautils.types.jvm.toJvmString
import metautils.util.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.AnnotationVisitor
import java.nio.file.Path



fun ClassAccess.toAsmAccess(visibility: Visibility, isStatic: Boolean = false): Int {
    var access = 0
    if (isFinal) access = access or Opcodes.ACC_FINAL
    if (isStatic) access = access or Opcodes.ACC_STATIC
    access = access or when (variant) {
        ClassVariant.Interface -> Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        ClassVariant.ConcreteClass -> 0
        ClassVariant.AbstractClass -> Opcodes.ACC_ABSTRACT
        ClassVariant.Enum -> Opcodes.ACC_ENUM
        ClassVariant.Annotation -> Opcodes.ACC_ANNOTATION
    }

    return access or visibility.asmOpcode()
}


private fun MethodAccess.toAsmAccess(): Int {
    var access = 0
    if (isStatic) access = access or Opcodes.ACC_STATIC
    if (isFinal) access = access or Opcodes.ACC_FINAL
    if (isAbstract) access = access or Opcodes.ACC_ABSTRACT
    access = access or visibility.asmOpcode()
    return access
}

private fun fieldAsmAccess(visibility: Visibility, isStatic: Boolean, isFinal: Boolean): Int {
    var access = 0
    if (isStatic) access = access or Opcodes.ACC_STATIC
    if (isFinal) access = access or Opcodes.ACC_FINAL
    access = access or visibility.asmOpcode()
    return access
}

private fun Visibility.asmOpcode() = when (this) {
    ClassVisibility.Public -> Opcodes.ACC_PUBLIC
    ClassVisibility.Private -> Opcodes.ACC_PRIVATE
    ClassVisibility.Package -> 0
    Visibility.Protected -> Opcodes.ACC_PROTECTED
}


// Tracks which class names are passed to asm, so we can now which inner class names were used in each class
internal class AsmClassWriter(private val index: ClasspathIndex) {
    private val classWriter = ClassWriter(0)
    private val referencedNames = mutableSetOf<QualifiedName>()

//    fun getReferencedNames(): Collection<QualifiedName> = referencedNames

    private fun QualifiedName.track() {
        referencedNames.add(this)
    }

    private fun ObjectType.track() = fullClassName.track()
    private fun JvmType.track() = visitNames { it.track() }
    private fun Collection<ObjectType>.track() = forEach { it.track() }
    private fun Collection<JavaAnnotation>.trackAno() = forEach { it.track() }
    private fun JavaAnnotation.track() = visitNames { it.track() }

    private fun AnnotateableVisitor.visitJavaAnnotation(annotation: JavaAnnotation) {
        visitAnnotation(annotation.type.classFileName, true).visitAnnotationParameters(annotation.parameters)
    }

    private fun AnnotationVisitor.visitAnnotationParameters(values: Map<String, AnnotationValue>) {
        for ((name, value) in values) {
            visitAnnotationValue(name, value)
        }
        visitEnd()
    }

    private fun AnnotationVisitor.visitAnnotationValue(name: String?, value: AnnotationValue) {
        when (value) {
            is AnnotationValue.Array -> {
                val arrayVisitor = visitArray(name)
                value.components.forEach { arrayVisitor.visitAnnotationValue(null, it) }
                arrayVisitor.visitEnd()
            }
            is AnnotationValue.Annotation -> visitAnnotation(name, value.annotation.type.classFileName)
                .visitAnnotationParameters(value.annotation.parameters)
            is AnnotationValue.Primitive -> visit(name, value.primitive)
            is AnnotationValue.Enum -> visitEnum(name, value.type.classFileName, value.constant)
            is AnnotationValue.ClassType -> visit(name, value.type.asmType())
        }
    }

    inline fun writeClass(
        access: ClassAccess, visibility: Visibility, className: QualifiedName, sourceFile: String,
        signature: ClassSignature?, superClass: ObjectType, superInterfaces: List<ObjectType>,
        annotations: List<JavaAnnotation>,
        init: ClassBody.() -> Unit
    ) {
        className.track()
        signature?.visitNames { it.track() }
        superClass.track()
        superInterfaces.track()
        annotations.trackAno()

        classWriter.visit(
            Opcodes.V1_8,
            access.toAsmAccess(visibility),
            className.toSlashString(),
            signature?.toClassfileName(),
            superClass.fullClassName.toSlashString(),
            superInterfaces.map { it.fullClassName.toSlashString() }.toTypedArray()
        )

        annotations.forEach { classWriter.annotateable.visitJavaAnnotation(it) }

        classWriter.visitSource(sourceFile, null)
        init(ClassBody())
        classWriter.visitEnd()
    }

    fun writeBytesTo(path: Path) {
        // All inner classes referenced must be added as INNERCLASS fields
        for (name in referencedNames) {
            if (name.shortName.components.size >= 2) {
                classWriter.visitInnerClass(
                    name.toSlashString(), name.outerClass().toSlashString(),
                    name.shortName.innermostClass(), index.accessOf(name)
                )
            }
        }
        path.writeBytes(classWriter.toByteArray())
    }

    inner class ClassBody {
        fun trackInnerClass(name: QualifiedName) = name.track()
        fun writeMethod(
            name: String,
            access: MethodAccess,
            descriptor: MethodDescriptor,
            parameterNames: List<String>,
            signature: MethodSignature?,
            annotations: List<JavaAnnotation>,
            parameterAnnotations: Map<Int, List<JavaAnnotation>>,
            throws: List<JvmType>,
            init: MethodBody.() -> Unit
        ) {

            descriptor.visitNames { it.track() }
            signature?.visitNames { it.track() }
            annotations.trackAno()
            parameterAnnotations.values.forEach { it.trackAno() }
            throws.forEach { it.track() }

            val methodWriter = classWriter.visitMethod(
                access.toAsmAccess(),
                name, descriptor.classFileName, signature?.toClassfileName(),
                throws.map { it.toJvmString() }.toTypedArray()
            )

            parameterNames.forEach { methodWriter.visitParameter(it, 0) }

            for (annotation in annotations) {
                methodWriter.annotateable.visitJavaAnnotation(annotation)
            }

            for ((index, paramAnnotations) in parameterAnnotations) {
                paramAnnotations.forEach {
                    methodWriter.visitParameterAnnotation(index, it.type.classFileName, false).visitEnd()
                }
            }


            if (!access.isAbstract) {
                methodWriter.visitCode()
            }
            init(MethodBody(methodWriter))

            methodWriter.visitEnd()
        }

        fun writeField(
            name: String, type: JvmType, signature: FieldSignature?, visibility: Visibility, isStatic: Boolean,
            isFinal: Boolean, annotations: List<JavaAnnotation>
        ) {
            type.track()
            signature?.visitNames { it.track() }
            annotations.trackAno()

            val fieldVisitor = classWriter.visitField(
                fieldAsmAccess(visibility, isStatic, isFinal),
                name, type.classFileName, signature?.toClassfileName(), null
            )

            for (annotation in annotations) {
                fieldVisitor.annotateable.visitJavaAnnotation(annotation)
            }

            fieldVisitor.visitEnd()
        }
    }

    inner class MethodBody(private val methodWriter: MethodVisitor) {
        fun writeZeroOperandInstruction(instruction: Int) {
            methodWriter.visitInsn(instruction)
        }

        fun setMaxStackAndVariablesSize(stack: Int, variables: Int) {
            methodWriter.visitMaxs(stack, variables)
        }

        fun writeLvArgInstruction(instruction: Int, lvIndex: Int) {
            methodWriter.visitVarInsn(instruction, lvIndex)
        }

        fun writeTypeArgInstruction(instruction: Int, type: JvmType) {
            type.track()
            methodWriter.visitTypeInsn(instruction, type.toJvmString())
        }

        fun writeFieldArgInstruction(instruction: Int, fieldOwner: ObjectType, fieldName: String, fieldType: JvmType) {
            fieldOwner.track()
            fieldType.track()
            methodWriter.visitFieldInsn(instruction, fieldOwner.toJvmString(), fieldName, fieldType.classFileName)
        }

        fun writeMethodCall(
            instruction: Int,
            methodOwner: ObjectType,
            methodName: String,
            methodDescriptor: MethodDescriptor,
            isInterface: Boolean
        ) {
            methodOwner.track()
            methodDescriptor.visitNames { it.track() }
            methodWriter.visitMethodInsn(
                instruction,
                methodOwner.toJvmString(),
                methodName,
                methodDescriptor.classFileName,
                isInterface
            )
        }

    }
}



