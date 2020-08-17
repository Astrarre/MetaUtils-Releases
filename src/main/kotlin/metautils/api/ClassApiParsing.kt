@file:Suppress("IfThenToElvis")

package metautils.api

import metautils.codegeneration.ClassAccess
import metautils.codegeneration.ClassVariant
import metautils.codegeneration.MethodAccess
import metautils.codegeneration.Visibility
import kotlinx.coroutines.runBlocking
import metautils.asm.*
import metautils.signature.*
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import metautils.signature.fromRawClassString
import metautils.signature.noAnnotations
import metautils.signature.toRawGenericType
import metautils.types.JvmType
import metautils.types.MethodDescriptor
import metautils.util.*
import java.nio.file.Path

fun ClassApi.Companion.readFromJar(jarPath: Path, filter: (Path) -> Boolean): Collection<ClassApi> {
    require(jarPath.hasExtension(".jar")) { "Specified path $jarPath does not point to a jar" }

    // Only pass top-level classes into readSingularClass()

//    val start = System.currentTimeMillis()
    val result = jarPath.openJar { jar ->
        val root = jar.getPath("/")
        runBlocking {
            root.walk().filter(filter).readFromSequence(root)
        }
    }

//    println("Parsing took ${System.currentTimeMillis() - start} millis")

    return result
}


//fun ClassApi.Companion.readFromDirectory(dirPath: Path): Collection<ClassApi> {
//    require(dirPath.isDirectory()) { "Specified path $dirPath is not a directory" }
//
//    return dirPath.recursiveChildren().readFromSequence(dirPath)
//}

//fun ClassApi.Companion.readFromList(
//    list: List<Path>,
//    rootPath: Path
//): Collection<ClassApi> =
//    list.asSequence().readFromSequence(rootPath)

private suspend fun Sequence<Path>.readFromSequence(rootPath: Path): Collection<ClassApi> = filter {
    // Only pass top-level classes into readSingularClass()
    it.isClassfile() && '$' !in it.toString()
}.toList()
//            .filter { "Concrete" in it.toString() }
    .parallelMap {
//        println("Processing class $it")
        readSingularClass(rootPath, it, nonStaticOuterClassTypeArgs = listOf(), isStatic = false, isProtected = false)
    }


// isStatic information is passed by the parent class for inner classes
private fun readSingularClass(
    rootPath: Path,
    path: Path,
    // Non-static inner classes can reference the type arguments of their parent class so we need that info
    nonStaticOuterClassTypeArgs: Collection<TypeArgumentDeclaration>,
//    outerClass: ClassApi?,
    isStatic: Boolean,
    isProtected: Boolean
): ClassApi {


    val classNode = readToClassNode(path)

    // Need to pass the type args of outer classes to be able to resolve type variables in this class
//    val nonStaticOuterClassTypeArgs = if (outerClass == null) mapOf()
//    else outerClass.outerClassesToThis()
//        .filter { !it.isStatic }
//        .flatMap { classApi -> classApi.typeArguments.map { it.name to it } }
//        .toMap()

    val outerClassTypeArgsMap = nonStaticOuterClassTypeArgs.map { it.name to it }.toMap()

    val signature = if (classNode.signature != null) {
        ClassSignature.readFrom(classNode.signature,
            outerClassTypeArgs = outerClassTypeArgsMap
        )
    } else {
        ClassSignature(
            superClass = ClassGenericType.fromRawClassString(classNode.superName),
            superInterfaces = classNode.interfaces.map {
                ClassGenericType.fromRawClassString(it)
            },
            typeArguments = null
        )
    }

    val classTypeArgMap = (signature.typeArguments?.map { it.name to it }?.toMap() ?: mapOf()) +
            outerClassTypeArgsMap

    val methods = classNode.methods.filter { !it.isSynthetic }
        .map { readMethod(it, /*classNode, */classTypeArgs = classTypeArgMap) }
    val fields = classNode.fields.map { readField(it, classTypeArgs = classTypeArgMap) }

    val fullClassName = classNode.name.toQualifiedName(slashQualified = true)


//    val innerClasses = classNode.innerClasses.map { it.name to it }.toMap()
    val innerClassShortName = with(fullClassName.shortName.components) { if (size == 1) null else last() }

    val typeArguments = signature.typeArguments ?: listOf()
    val nonStaticTypeArgs = nonStaticOuterClassTypeArgs + if(isStatic) listOf() else typeArguments
    val innerClasses = classNode.innerClasses
        .filter { innerClassShortName != it.innerName && it.outerName == classNode.name }
        .map {
            readSingularClass(
                rootPath,
                rootPath.resolve("${it.name}.class"),
                nonStaticOuterClassTypeArgs = nonStaticTypeArgs,
                isStatic = it.isStatic,
                isProtected = it.isProtected
            )
        }

    val classApi = ClassApi(
        name = fullClassName,
        superClass = if (classNode.superName == ClassNames.Object) null else {
            JavaClassType(signature.superClass, annotations = listOf())
        },
        superInterfaces = signature.superInterfaces.map { JavaClassType(it, annotations = listOf()) },
        methods = methods.toSet(), fields = fields.toSet(),
        innerClasses = innerClasses,
//        outerClass = outerClass,
        visibility = if (isProtected) Visibility.Protected else classNode.visibility,
        access = ClassAccess(
            variant = with(classNode) {
                when {
                    isInterface -> ClassVariant.Interface
                    isAnnotation -> ClassVariant.Annotation
                    isEnum -> ClassVariant.Enum
                    isAbstract -> ClassVariant.AbstractClass
                    else -> ClassVariant.ConcreteClass
                }
            },
            isFinal = classNode.isFinal
        ),
        isStatic = isStatic,
        typeArguments = signature.typeArguments ?: listOf(),
        annotations = parseAnnotations(classNode.visibleAnnotations, classNode.invisibleAnnotations)
    )

    classApi.methods.forEach { it.init(classApi) }
    classApi.fields.forEach { it.init(classApi) }
    classApi.innerClasses.forEach { it.init(classApi) }
    return classApi

    // We need to create the inner classes after creating the classapi, so we can specify what is their outer class.
//    return classApi.copy(innerClasses = classNode.innerClasses
//        .filter { innerClassShortName != it.innerName && it.outerName == classNode.name }
//        .map {
//            readSingularClass(
//                rootPath,
//                rootPath.resolve("${it.name}.class"),
//                classApi,
//                isStatic = it.isStatic,
//                isProtected = it.isProtected
//            )
//        }
//    )
}

private fun parseAnnotations(visible: List<AnnotationNode>?, invisible: List<AnnotationNode>?): List<JavaAnnotation> {
    val combined = when {
        visible == null -> invisible
        invisible == null -> visible
        else -> visible + invisible
    }
    combined ?: return listOf()
    return combined.map { JavaAnnotation.fromAsmNode(it) }
}

//private fun parseAnnotation(node: AnnotationNode) =
//    JavaAnnotation(FieldType.read(node.desc) as ObjectType, parseRawAnnotationValues(node.values))
//
//private fun parseRawAnnotationValues(keyValues: List<Any>?): Map<String, AnnotationValue> {
//    if (keyValues == null) return mapOf()
//    val map = mutableMapOf<String, Any>()
//    var key: String? = null
//    keyValues.forEachIndexed { index, kv ->
//        if (index % 2 == 0) {
//            // Key
//            key = kv as String
//        } else {
//            // Value
//            map[key!!] = kv
//        }
//    }
//    return map.mapValues { (_, v) -> parseAnnotationValue(v) }
//}
//
//private fun parseAnnotationValue(value: Any): AnnotationValue = when (value) {
//    is Number -> AnnotationValue.Primitive.Num(value)
//    is Boolean -> AnnotationValue.Primitive.Bool(value)
//    is Char -> AnnotationValue.Primitive.Cha(value)
//    is String -> AnnotationValue.Primitive.Str(value)
//    is org.objectweb.asm.Type -> AnnotationValue.ClassType(JvmType.read(value.descriptor))
//    is Array<*> -> {
//        assert(value.size == 2)
//        assert(value[0] is String)
//        @Suppress("UNCHECKED_CAST")
//        value as Array<String>
//
//        val type = JvmType.read(value[0]) as ObjectType
//        AnnotationValue.Enum(type = type, constant = value[1])
//    }
//    is AnnotationNode -> AnnotationValue.Annotation(parseAnnotation(value))
//    is List<*> -> AnnotationValue.Array(value.map { parseAnnotationValue(it!!) })
//    else -> error("Unexpected annotation value '$value' of type '${value::class.qualifiedName}'")
//}


private fun readField(field: FieldNode, classTypeArgs: TypeArgDecls): ClassApi.Field {
    val signature = if (field.signature != null) FieldSignature.readFrom(field.signature, classTypeArgs)
    else JvmType.fromDescriptorString(field.desc).toRawGenericType()

    return ClassApi.Field(
        name = field.name,
        type = AnyJavaType(
            signature,
            annotations = parseAnnotations(field.visibleAnnotations, field.invisibleAnnotations)
        ),
        isStatic = field.isStatic,
        isFinal = field.isFinal,
        visibility = field.visibility
    )
}


// Generated parameters are generated $this garbage that come from for example inner classes
private fun getNonGeneratedParameterDescriptors(
    descriptor: MethodDescriptor,
    method: MethodNode
): List<JvmType> {
    if (method.parameters == null) return descriptor.parameterDescriptors
    val generatedIndices = method.parameters.mapIndexed { i, node -> i to node }.filter { '$' in it.second.name }
        .map { it.first }

    return descriptor.parameterDescriptors.filterIndexed { i, _ -> i !in generatedIndices }
}

private fun readMethod(
    method: MethodNode,
//    classNode: ClassNode,
    classTypeArgs: TypeArgDecls
): ClassApi.Method {
    val signature = if (method.signature != null) MethodSignature.readFrom(method.signature, classTypeArgs) else {
        val descriptor = MethodDescriptor.fromDescriptorString(method.desc)
        val parameters = getNonGeneratedParameterDescriptors(descriptor, method)
        MethodSignature(
            typeArguments = null, parameterTypes = parameters.map { it.toRawGenericType() },
            returnType = descriptor.returnDescriptor.toRawGenericType(),
            throwsSignatures = method.exceptions.map { ClassGenericType.fromRawClassString(it) }
        )
    }
    val parameterNames = inferParameterNames(method, /*classNode,*/ signature.parameterTypes.size)

    val visibility = method.visibility

    return ClassApi.Method(
        name = method.name,
        typeArguments = signature.typeArguments ?: listOf(),
        returnType = JavaReturnType(
            signature.returnType,
            annotations = parseAnnotations(method.visibleAnnotations, method.invisibleAnnotations)
        ),
        parameters = parameterNames.zip(signature.parameterTypes)
            .mapIndexed { index, (name, type) ->
                name to AnyJavaType(
                    type, annotations = parseAnnotations(
                        method.visibleParameterAnnotations?.get(index),
                        method.invisibleParameterAnnotations?.get(index)
                    )
                )
            },
        throws = signature.throwsSignatures.map { it.noAnnotations() },
        access = MethodAccess(
            isStatic = method.isStatic,
            isFinal = method.isFinal,
            isAbstract = method.isAbstract,
            visibility = visibility
        )
    )
}

private fun inferParameterNames(
    method: MethodNode,
//    classNode: ClassNode,
    parameterCount: Int
): List<String> {
    val locals = method.localVariables
    return when {
        method.parameters != null -> {
            // Some methods use a parameter names field instead of local variables
            method.parameters.filter { '$' !in it.name }.map { it.name }
        }
        locals != null -> {
            val nonThisLocalNames = locals.filter { it.name != "this" }.map { it.name }
//            // Enums pass the name and ordinal into the constructor as well
//            val namesWithEnum = nonThisLocalNames.applyIf(classNode.isEnum && method.isConstructor) {
//                listOf("\$enum\$name", "\$enum\$ordinal") + it
//            }

            check(nonThisLocalNames.size >= parameterCount) {
                "There was not enough (${nonThisLocalNames.size}) local variable debug information for all parameters" +
                        " ($parameterCount} of them) in method ${method.name}"
            }
            nonThisLocalNames.take(parameterCount).map { it }
        }
        else -> listOf()
    }
}

//private fun String.innerClassShortName(): String? = if ('$' in this) this.substringAfterLast('$') else null