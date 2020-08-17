package metautils.codegeneration.asm

import metautils.api.AnyJavaType
import metautils.api.JavaReturnType
import metautils.api.JavaType
import codegeneration.*
import metautils.codegeneration.*
import metautils.signature.*
import metautils.util.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import metautils.signature.JavaLangObjectGenericType
import metautils.signature.VoidJavaType
import metautils.signature.getContainedClassesRecursively
import metautils.signature.toJvmType
import metautils.types.JvmReturnType
import metautils.types.MethodDescriptor
import metautils.types.ObjectType
import java.nio.file.Path


private fun GenericReturnType.hasGenericsInvolved() = this is TypeVariable
        || getContainedClassesRecursively().any { type -> type.classNameSegments.any { it.typeArguments != null } }

internal fun JavaReturnType.asmType(): Type = toJvmType().asmType()
internal fun JvmReturnType.asmType(): Type = Type.getType(classFileName)


//private

private fun writeClassImpl(
    info: ClassInfo, className: QualifiedName, srcRoot: Path, index: ClasspathIndex
): Unit = with(info) {
    val genericsInvolved = typeArguments.isNotEmpty() || superClass?.type?.hasGenericsInvolved() == true
            || superInterfaces.any { it.type.hasGenericsInvolved() }
    val signature = if (genericsInvolved) ClassSignature(typeArguments = typeArguments.let { if (it.isEmpty()) null else it },
            superClass = superClass?.type ?: JavaLangObjectGenericType,
            superInterfaces = superInterfaces.map { it.type }
        ) else null


    val classWriter = AsmClassWriter(index)

    classWriter.writeClass(
        access, visibility, className,
        sourceFile = className.shortName.outerMostClass() + ".java",
        signature = signature,
        superClass = superClass?.toJvmType() ?: ObjectType.Object,
        superInterfaces = superInterfaces.map { it.toJvmType() },
        annotations = info.annotations
    ) {
        AsmGeneratedClass(
            this,
            className,
            srcRoot,
            info.access.variant.isInterface,
            index
        ).apply {
            body()
//            if (capturedOuterClass != null) {
//                // In a double nested class it will probably break
//                addField(name = "this\$0")
//            }
        }.finish()
    }


    val path = srcRoot.resolve(className.toPath(".class"))
    path.createParentDirectories()
    classWriter.writeBytesTo(path)
}


class AsmCodeGenerator(private val index: ClasspathIndex) : CodeGenerator {
    override fun writeClass(info: ClassInfo, packageName: PackageName, srcRoot: Path) {
        writeClassImpl(
            info,
            QualifiedName.fromPackageAndShortName(packageName,
                ShortClassName.fromComponents(listOf(info.shortName))),
            srcRoot,
            index
        )
    }

}


private fun <T : GenericReturnType> Iterable<JavaType<T>>.generics() = map { it.type }


private class AsmGeneratedClass(
    private val classWriter: AsmClassWriter.ClassBody,
    private val className: QualifiedName,
    private val srcRoot: Path,
    private val isInterface: Boolean,
    private val index: ClasspathIndex
) : GeneratedClass {

    private val instanceFieldInitializers: MutableMap<FieldExpression, Expression> = mutableMapOf()
    private val staticFieldInitializers: MutableMap<FieldExpression, Expression> = mutableMapOf()
    private val constructors: MutableList<MethodInfo> = mutableListOf()

//    fun addAsmInnerClasses(innerClasses: List<InnerClassNode>) {
//        innerClasses.forEach { classWriter.visitInnerClass(it.name, it.outerName, it.innerName, it.access) }
//    }

    fun finish() {
        assert(instanceFieldInitializers.isEmpty() || constructors.isNotEmpty())
        for (constructor in constructors) {
            constructor.addMethodImpl(
                returnType = VoidJavaType,
                name = ConstructorsName,
                typeArguments = listOf(),
                access = MethodAccess(
                    isStatic = false, visibility = constructor.visibility, isFinal = false, isAbstract = false
                )
            ) {
                for ((targetField, fieldValue) in this@AsmGeneratedClass.instanceFieldInitializers) {
                    addStatement(
                        AssignmentStatement(
                            target = targetField,
                            assignedValue = fieldValue
                        )
                    )
                }
            }
        }

        if (staticFieldInitializers.isNotEmpty()) {
            val staticInitializer = MethodInfo(
                visibility = Visibility.Package,
                parameters = listOf(),
                throws = listOf()
            ) {
                for ((targetField, fieldValue) in this@AsmGeneratedClass.staticFieldInitializers) {
                    addStatement(
                        AssignmentStatement(
                            target = targetField,
                            assignedValue = fieldValue
                        )
                    )
                }
            }
            staticInitializer.addMethodImpl(
                returnType = VoidJavaType,
                name = "<clinit>",
                typeArguments = listOf(),
                access = MethodAccess(
                    isStatic = true,
                    visibility = Visibility.Package,
                    isFinal = false,
                    isAbstract = false
                )
            )
        }
    }

    override fun addInnerClass(info: ClassInfo, isStatic: Boolean) {
        if (!isStatic) error("Non-static inner classes are not supported by the ASM generator yet")
        val innerClassName = className.innerClass(info.shortName)
        classWriter.trackInnerClass(innerClassName)
        writeClassImpl(info, innerClassName, srcRoot, index)
//        classWriter.visitNestMember(innerClassName.toSlashQualifiedString())
    }

    override fun addMethod(
        methodInfo: MethodInfo,
        isStatic: Boolean,
        isFinal: Boolean,
        isAbstract: Boolean,
        typeArguments: List<TypeArgumentDeclaration>,
        name: String,
        returnType: JavaReturnType
    ) {
        methodInfo.addMethodImpl(
            returnType, typeArguments,
            MethodAccess(isStatic, isFinal, isAbstract, methodInfo.visibility), name
        )
    }


    private fun MethodInfo.addMethodImpl(
            returnType: JavaReturnType,
            typeArguments: List<TypeArgumentDeclaration>,
            access: MethodAccess,
            name: String,
            bodyPrefix: GeneratedMethod.() -> Unit = {}
    ) {
        val descriptor = MethodDescriptor(parameters.map { it.type.toJvmType() }, returnType.toJvmType())
        val genericsInvolved = typeArguments.isNotEmpty() || parameters.any { it.type.type.hasGenericsInvolved() }
                || returnType.type.hasGenericsInvolved()
        val signature = if (genericsInvolved) {
            MethodSignature(typeArguments.let { if(it.isEmpty()) null else it },
                parameters.map { it.type.type }, returnType.type, throws.generics()
            )
        } else null

        classWriter.writeMethod(
            name, access, descriptor,parameterNames = parameters.map { it.name }, signature =  signature,
            annotations = returnType.annotations,
            parameterAnnotations = parameters
                .mapIndexed { i, (_,paramType,_) -> i to paramType.annotations }.toMap(),
            throws = throws.map { it.toJvmType() }
        ) {
            if (access.isAbstract) {
                AbstractGeneratedMethod.apply(bodyPrefix).apply(body)
            } else {
                val builder = AsmGeneratedMethod(this,returnType.toJvmType(), name, access.isStatic,
                    parameters.map { (name, type) -> name to type.toJvmType() }).apply(bodyPrefix).apply(body)

                // This assumes extremely simplistic method calls that just call one method and that's it
                writeZeroOperandInstruction(returnType.asmType().getOpcode(Opcodes.IRETURN))
                val localVarSize = parameters.sumBy { it.type.toJvmType().byteWidth() }.applyIf(!access.isStatic) { it + 1 }
                val stackSize = builder.maxStackSize()
                setMaxStackAndVariablesSize(stackSize, localVarSize)
            }
        }
    }


    override fun addConstructor(info: MethodInfo) {
        require(!isInterface) { "Interfaces cannot have constructors" }
        // Need field initializer information before we write the constructors
        constructors.add(info)
    }


    override fun addField(
            name: String,
            type: AnyJavaType,
            visibility: Visibility,
            isStatic: Boolean,
            isFinal: Boolean,
            initializer: Expression?
    ) {
        val genericsInvolved = type.type.hasGenericsInvolved()
        val signature = if (genericsInvolved) type.type else null
        classWriter.writeField(
            name, type.toJvmType(), signature, visibility, isStatic, isFinal,
            annotations = type.annotations
        )

        if (initializer != null) {
            val targetField = FieldExpression(
                receiver = if (isStatic) ClassReceiver(ObjectType.fromClassName(className)) else ThisExpression,
                name = name,
                owner = ObjectType.fromClassName(className),
                type = type.toJvmType()
            )
            if (isStatic) {
                staticFieldInitializers[targetField] = initializer
            } else {
                instanceFieldInitializers[targetField] = initializer
            }
        }
    }

    override fun addJavadoc(comment: String) {

    }

}


object AbstractGeneratedMethod : GeneratedMethod {
    override fun addStatement(statement: Statement) {
        error("Method is abstract")
    }

    override fun addJavadoc(comment: String) {
    }
}

