@file:Suppress("UNCHECKED_CAST")

package metautils.signature

import metautils.api.*
import metautils.internal.fromPackageNameAndClassSegments
import metautils.types.*
import metautils.util.*

val JavaLangObjectGenericType = QualifiedName.Object.toRawGenericType()
val JavaLangObjectJavaType = AnyJavaType(JavaLangObjectGenericType, annotations = listOf())
val VoidJavaType = VoidJvmReturnType.toRawGenericType().noAnnotations()

//fun TypeArgumentDeclaration.remap(mapper: (className: QualifiedName) -> QualifiedName?): TypeArgumentDeclaration =
//        copy(classBound = classBound?.remap(mapper), interfaceBounds = interfaceBounds.map { it.remap(mapper) })

//fun <T : GenericReturnType> T.remap(mapper: (className: QualifiedName) -> QualifiedName?): T = when (this) {
//    is GenericsPrimitiveType -> this
//    is ClassGenericType -> remap(mapper)
//    is TypeVariable -> copy(declaration = declaration.remap(mapper))
//    is ArrayGenericType -> copy(componentType.remap(mapper))
//    GenericReturnType.Void -> GenericReturnType.Void
//    else -> error("impossible")
//} as T


@OptIn(ExperimentalStdlibApi::class)
fun GenericReturnType.getContainedClassesRecursively(): List<ClassGenericType> =
        buildList { visitContainedClasses { add(it) } }

fun GenericReturnType.visitContainedClasses(visitor: (ClassGenericType) -> Unit): Unit = when (this) {
    is GenericsPrimitiveType, is TypeVariable, VoidGenericReturnType -> {
    }
    is ClassGenericType -> {
        visitor(this)
        for (className in classNameSegments) {
            className.typeArguments.forEach {
                if (it is TypeArgument.SpecificType) it.type.visitContainedClasses(visitor)
            }
        }
    }
    is ArrayGenericType -> componentType.visitContainedClasses(visitor)
}


fun ClassGenericType.Companion.fromRawClassString(string: String, slashQualified: Boolean = true): ClassGenericType {
    return string.toQualifiedName(slashQualified).toRawGenericType()
}

/**
 * Will only put the type args at the INNERMOST class!
 */
fun ClassGenericType.Companion.fromNameAndTypeArgs(
    name: QualifiedName,
    typeArgs: List<TypeArgument>
): ClassGenericType {
    val outerClassesArgs: List<List<TypeArgument>> = (0 until (name.shortName.components.size - 1)).map { listOf() }
    return name.toClassGenericType(outerClassesArgs + listOf(typeArgs))
}


fun ClassGenericType.toJvmQualifiedName(): QualifiedName =
    QualifiedName.fromPackageNameAndClassSegments(packageName, classNameSegments)

fun ClassGenericType.toJvmString() = toJvmQualifiedName().toSlashString()

fun <T : GenericReturnType> T.noAnnotations(): JavaType<T> = JavaType(this, listOf())
fun <T : GenericReturnType> T.annotated(annotation: JavaAnnotation): JavaType<T> = JavaType(this, listOf(annotation))

fun GenericTypeOrPrimitive.toJvmType(): JvmType = when (this) {
    is GenericsPrimitiveType -> primitive
    is ClassGenericType -> toJvmType()
    is TypeVariable -> resolveJvmType()
    is ArrayGenericType -> ArrayType(componentType.toJvmType())
}

private fun TypeVariable.resolveJvmType(): JvmType = with(declaration) {
    classBound?.toJvmType()
            ?: if (interfaceBounds.isNotEmpty()) interfaceBounds[0].toJvmType() else ObjectType.Object
}

fun ClassGenericType.toJvmType(): ObjectType = ObjectType.fromClassName(toJvmQualifiedName())

fun GenericReturnType.toJvmType(): JvmReturnType = when (this) {
    is GenericTypeOrPrimitive -> toJvmType()
    VoidGenericReturnType -> VoidJvmReturnType
}

fun MethodSignature.toJvmDescriptor() = MethodDescriptor(
        parameterDescriptors = parameterTypes.map { it.toJvmType() },
        returnDescriptor = returnType.toJvmType()
)

fun ClassSignature.addSuperInterface(superInterface: ClassGenericType) =
    ClassSignature(typeArguments, superClass, superInterfaces + superInterface)


//fun JavaType<*>.toJvmType() = type.toJvmType()
fun JavaType<*>.toJvmType() : JvmReturnType = type.toJvmType()
fun AnyJavaType.toJvmType(): JvmType = type.toJvmType()
fun JavaClassType.toJvmType(): ObjectType = type.toJvmType()
//fun JavaThrowableType.toJvmType() = type.toJvmType()

fun ClassGenericType.withSegments(segments: List<SimpleClassGenericType>) =
    ClassGenericType(packageName,segments)

fun ClassGenericType.outerClass(): ClassGenericType {
    check(classNameSegments.size >= 2)
    return withSegments(classNameSegments.dropLast(1))
}

fun JavaClassType.outerClass(): JavaClassType = copy(type = type.outerClass())

fun QualifiedName.toRawGenericType(): ClassGenericType = toClassGenericType(shortName.components.map { listOf() })

/**
 *  Each element in typeArgsChain is for an element in the inner class name chain.
 *  Each element contains the type args for each class name in the chain.
 */
fun QualifiedName.toClassGenericType(typeArgsChain: List<List<TypeArgument>>): ClassGenericType =
        ClassGenericType(packageName,
                shortName.components.zip(typeArgsChain).map { (name, args) -> SimpleClassGenericType(name, args) }
        )

fun ObjectType.toRawGenericType(): ClassGenericType = fullClassName.toRawGenericType()
fun ObjectType.toRawJavaType(): JavaClassType = JavaClassType(fullClassName.toRawGenericType(), annotations = listOf())
fun JvmType.toRawGenericType(): GenericTypeOrPrimitive = when (this) {
    is JvmPrimitiveTypes -> JvmPrimitiveToGenericsPrimitive.getValue(this)
    is ObjectType -> toRawGenericType()
    is ArrayType -> ArrayGenericType(componentType.toRawGenericType())
}

private val JvmPrimitiveToGenericsPrimitive = mapOf(
        JvmPrimitiveTypes.Byte to GenericsPrimitiveType.Byte,
        JvmPrimitiveTypes.Char to GenericsPrimitiveType.Char,
        JvmPrimitiveTypes.Double to GenericsPrimitiveType.Double,
        JvmPrimitiveTypes.Float to GenericsPrimitiveType.Float,
        JvmPrimitiveTypes.Int to GenericsPrimitiveType.Int,
        JvmPrimitiveTypes.Long to GenericsPrimitiveType.Long,
        JvmPrimitiveTypes.Short to GenericsPrimitiveType.Short,
        JvmPrimitiveTypes.Boolean to GenericsPrimitiveType.Boolean
)


fun JvmReturnType.toRawGenericType(): GenericReturnType = when (this) {
    is JvmType -> toRawGenericType()
    VoidJvmReturnType -> VoidGenericReturnType
}

//fun ClassGenericType.remapTopLevel(mapper: (className: QualifiedName) -> QualifiedName?): ClassGenericType {
//    val asQualifiedName = toJvmQualifiedName()
//    val asMappedQualifiedName = mapper(asQualifiedName) ?: asQualifiedName
//    val mappedPackage = asMappedQualifiedName.packageName
//
//    val mappedClasses = classNameSegments.zip(asMappedQualifiedName.shortName.components).map { (oldName, mappedName) ->
//        SimpleClassGenericType(name = mappedName, typeArguments = oldName.typeArguments)
//    }
//
//    return ClassGenericType(mappedPackage, mappedClasses)
//}

//fun ClassGenericType.remapTypeArguments(mapper: (className: QualifiedName) -> QualifiedName?) =
//        copy(classNameSegments = classNameSegments.map { it.copy(typeArguments = it.typeArguments?.remap(mapper)) })

//fun ClassGenericType.remap(mapper: (className: QualifiedName) -> QualifiedName?) = remapTopLevel(mapper)
//        .remapTypeArguments(mapper)


//private fun TypeArgument.remap(mapper: (className: QualifiedName) -> QualifiedName?): TypeArgument = when (this) {
//    is TypeArgument.SpecificType -> copy(type = type.remap(mapper))
//    TypeArgument.AnyType -> TypeArgument.AnyType
//}

//fun List<TypeArgument>.remap(mapper: (className: QualifiedName) -> QualifiedName?) = map { it.remap(mapper) }

fun List<TypeArgumentDeclaration>.toTypeArgumentsOfNames(): List<TypeArgument> = map {
    TypeArgument.SpecificType(TypeVariable(it.name, it), wildcardType = null)
}


fun <T : GenericReturnType> JavaType<T>.mapTypeVariables(mapper: (TypeVariable) -> GenericType): JavaType<T> =
        copy(type = type.mapTypeVariables(mapper))

fun <T : GenericReturnType> T.mapTypeVariables(mapper: (TypeVariable) -> GenericType): T = when (this) {
    is GenericTypeOrPrimitive -> mapTypeVariables(mapper)
    VoidGenericReturnType -> this
    else -> error("impossible")
} as T // There is one case where this might fail, but the restrictions java places on throwable types should prevent that

fun GenericTypeOrPrimitive.mapTypeVariables(mapper: (TypeVariable) -> GenericType): GenericTypeOrPrimitive =
        when (this) {
            is GenericsPrimitiveType -> this
            is GenericType -> mapTypeVariables(mapper)
        }

fun GenericType.mapTypeVariables(mapper: (TypeVariable) -> GenericType): GenericType = when (this) {
    is ClassGenericType -> mapTypeVariables(mapper)
    is ArrayGenericType -> copy(componentType = componentType.mapTypeVariables(mapper))
    is TypeVariable -> mapper(this)
}

//inline fun ThrowableType.mapTypeVariables(mapper: (TypeVariable) -> GenericType): ThrowableType = when (this) {
//    is ClassGenericType -> mapTypeVariables(mapper)
//
//}

fun ClassGenericType.mapTypeVariables(mapper: (TypeVariable) -> GenericType): ClassGenericType =
        withSegments(classNameSegments.map { it.withArguments(it.typeArguments.mapTypeVariables(mapper)) })

fun List<TypeArgument>.mapTypeVariables(mapper: (TypeVariable) -> GenericType): List<TypeArgument> =
        this.map {
            when (it) {
                is TypeArgument.SpecificType -> it.withType(it.type.mapTypeVariables(mapper))
                TypeArgument.AnyType -> it
            }
        }

fun TypeArgumentDeclaration.mapTypeVariables(mapper: (TypeVariable) -> GenericType) = copy(
        classBound = classBound?.mapTypeVariables(mapper),
        interfaceBounds = interfaceBounds.map { it.mapTypeVariables(mapper) }
)

fun List<TypeArgumentDeclaration>.mapTypeVariablesDecl(mapper: (TypeVariable) -> GenericType): List<TypeArgumentDeclaration> =
        this.map {
            it.mapTypeVariables(mapper)
        }