package metautils.internal

import metautils.signature.*
import metautils.signature.baseTypesGenericsMap
import metautils.types.JvmType
import metautils.types.MethodDescriptor
import metautils.util.PackageName
import metautils.util.QualifiedName
import metautils.util.applyIf
import metautils.util.mapElementsOld
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

//internal fun ClassGenericType.remapTopLevel(mapper: (className: QualifiedName) -> QualifiedName?): ClassGenericType {
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

//internal fun ClassGenericType.remapTypeArguments(mapper: (className: QualifiedName) -> QualifiedName) =
//        withSegments(classNameSegments.map { it.withArguments(it.typeArguments.mapElementsOld(mapper)) })

//internal fun ClassGenericType.remap(mapper: (className: QualifiedName) -> QualifiedName) = remapTopLevel(mapper)
//        .remapTypeArguments(mapper)


internal fun classSignatureFromAsmClassNode(classNode: ClassNode, outerClassTypeArgs: Iterable<TypeArgumentDeclaration>?): ClassSignature = if (classNode.signature != null) {
    ClassSignature.fromSignatureString(classNode.signature,
        outerClassTypeArgs = outerClassTypeArgs
    )
} else {
    ClassSignature(
        superClass = ClassGenericType.withNoTypeArgs(classNode.superName),
        superInterfaces = classNode.interfaces.map {
            ClassGenericType.withNoTypeArgs(it)
        },
        typeArguments = listOf()
    )
}

internal fun methodSignatureFromAsmMethodNode(method: MethodNode, classTypeArgs: Iterable<TypeArgumentDeclaration>?)
        = if (method.signature != null)  MethodSignature.fromSignatureString(method.signature, classTypeArgs) else {
    val descriptor = MethodDescriptor.fromDescriptorString(method.desc)
    val parameters = getNonGeneratedParameterDescriptors(descriptor, method)
    MethodSignature(
        typeArguments = listOf(), parameterTypes = parameters.map { GenericTypeOrPrimitiveGen.fromRawJvmType(it)},
        returnType = GenericReturnType.fromRawJvmType(descriptor.returnDescriptor),
        throwsSignatures = method.exceptions.map { ClassGenericType.withNoTypeArgs(it) }
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

typealias TypeArgDecls = Map<String, TypeArgumentDeclaration>





private const val doChecks = false

private val StubTypeArgDecl = TypeArgumentDeclaration("", null, listOf())

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalStdlibApi::class)
internal class SignatureReader(private val signature: String, typeVariableDeclarations: Iterable<TypeArgumentDeclaration>?) {
    var progressPointer = 0

    private val typeArgDeclarations: MutableMap<String,TypeArgumentDeclaration> =
        typeVariableDeclarations?.map { it.name to it }?.toMap()?.toMutableMap() ?: mutableMapOf()
    private var couldNotResolveSomeTypeArgumentDecls = false

    private val shouldReResolve = couldNotResolveSomeTypeArgumentDecls && typeVariableDeclarations != null

    // In cases where there are recursive type argument bounds, we can't resolve the declarations of the bounds by reading
    // left to right. So after we finish reading we go over the TypeVariables and replace the stub declarations with the
    // real, resolved declarations.

    private fun ClassSignature.reResolveTypeArgumentDeclarations() = ClassSignature(
        typeArguments = typeArguments.mapTypeVariablesDecl { it.reResolve() },
        superClass = superClass.reResolve(),
        superInterfaces = superInterfaces.map { it.reResolve() }
    )

    private fun MethodSignature.reResolveTypeArgumentDeclarations() = MethodSignature(
        typeArguments = typeArguments.mapTypeVariablesDecl { it.reResolve() },
        parameterTypes = parameterTypes.map { it.reResolve() },
        returnType = returnType.reResolve(),
        throwsSignatures = throwsSignatures.map { it.reResolve() }
    )

    private fun TypeVariable.reResolve(): TypeVariable = copy(
            declaration = typeArgDeclarations[name]
                ?: error("Can't find type argument declaration of type variable '$name'")
    )

    private fun <T : GenericReturnType> T.reResolve() = mapTypeVariables { it.reResolve() }

    fun readClass(): ClassSignature {
        check { signature.isNotEmpty() }

        val typeParamsMarker = signature[0]
        val formalTypeParameters = if (typeParamsMarker == '<') {
            readFormalTypeParameters()
        } else listOf()
        val superClassSignature = readClassTypeSignature()
        val superInterfaceSignatures = readRepeatedly(
            until = { progressPointer == signature.length },
            reader = { readClassTypeSignature() },
            skip = false
        )
        return ClassSignature(formalTypeParameters, superClassSignature, superInterfaceSignatures)
            .applyIf(shouldReResolve) { it.reResolveTypeArgumentDeclarations() }
    }

    fun readMethod(): MethodSignature {
        val formalTypeParameters = if (current() == '<') readFormalTypeParameters() else listOf()
        advance('(')
        val parameterTypes = readRepeatedly(until = { current() == ')' }, reader = { readTypeSignature() }, skip = true)
        val returnType = if (current() == 'V') VoidGenericReturnType.also { advance() } else readTypeSignature()
        val throws = readRepeatedly(
            until = { progressPointer == signature.length },
            reader = { readThrowsSignature() },
            skip = false
        )
        return MethodSignature(formalTypeParameters, parameterTypes, returnType, throws)
            .applyIf(shouldReResolve) { it.reResolveTypeArgumentDeclarations() }
    }

    fun readField(): FieldSignature = readFieldTypeSignature().applyIf(shouldReResolve) { it.reResolve() }

    private fun readThrowsSignature(): ThrowableType {
        advance('^')
        return when (current()) {
            'L' -> readClassTypeSignature()
            'T' -> readTypeVariableSignature()
            else -> error("Unrecognized throwable type prefix: ${current()}")
        }
    }

    private fun readFormalTypeParameters(): List<TypeArgumentDeclaration> {
        advance('<')
        val args = readRepeatedly(until = { current() == '>' }, reader = { readFormalTypeParameter() }, skip = true)
        check { args.isNotEmpty() }
        return args
    }

    private fun readFormalTypeParameter(): TypeArgumentDeclaration {
        val identifier = readUntil(':', skip = true)
        val current = current()
        val classBound = if (current.let { it == 'L' || it == '[' || it == 'T' }) {
            readFieldTypeSignature()
        } else null
        val interfaceBounds = readRepeatedly(
            until = { current() != ':' },
            reader = {
                advance(':')
                readFieldTypeSignature()
            }, skip = false
        )
        return TypeArgumentDeclaration(identifier, classBound, interfaceBounds)
            .also { typeArgDeclarations[identifier] = it }
    }


    private fun readClassTypeSignature(): ClassGenericType {
        advance('L')
        val packageSpecifier = readPackageSpecifier()
        val classNameChain =
            readRepeatedly(until = { current() == ';' }, reader = { readSimpleClassTypeSignature() }, skip = true)

        check { classNameChain.isNotEmpty() }
        return ClassGenericType(packageSpecifier, classNameChain)
    }

    private fun readSimpleClassTypeSignature(): SimpleClassGenericType {
        val identifier = readUntil(until = { it == '<' || it == '.' || it == '$' || it == ';' }, skip = false)
        val typeArguments = if (current() == '<') readTypeArguments()
        else {
            listOf()
        }
        val current = current()
        if (current == '.' || current == '$') advance()
        return SimpleClassGenericType(identifier, typeArguments)
    }

    private fun readTypeArguments(): List<TypeArgument> {
        advance('<')
        val args = readRepeatedly(until = { current() == '>' }, reader = { readTypeArgument() }, skip = true)
        check { args.isNotEmpty() }
        return args
    }

    private fun readTypeArgument(): TypeArgument {
        if (current() == '*') {
            advance()
            return TypeArgument.AnyType
        }
        val wildcardIndicator = readWildcardIndicator()
        val fieldTypeSignature = readFieldTypeSignature()
        return TypeArgument.SpecificType(fieldTypeSignature, wildcardIndicator)
    }

    private fun readWildcardIndicator(): WildcardType? = when (current()) {
        '+' -> WildcardType.Extends
        '-' -> WildcardType.Super
        else -> null
    }.also { if (it != null) advance() }

    private fun readFieldTypeSignature(): GenericType = when (current()) {
        'L' -> readClassTypeSignature()
        '[' -> readArrayTypeSignature()
        'T' -> readTypeVariableSignature()
        else -> error("Unrecognized field type signature prefix: ${current()}")
    }

    private fun readArrayTypeSignature(): ArrayGenericType {
        advance('[')
        return ArrayGenericType(readTypeSignature())
    }

    private fun readTypeSignature(): GenericTypeOrPrimitive {
        readPrimitiveSignature()?.let { return it }
        return readFieldTypeSignature()
    }

    private fun readPrimitiveSignature(): GenericsPrimitiveType? = baseTypesGenericsMap[current()]?.also { advance() }

    private fun readTypeVariableSignature(): TypeVariable {
        advance('T')
        val identifier = readUntil(';', skip = true)
        val declaration = typeArgDeclarations[identifier] ?: run {
            // If we can't find it we assume it's defined later on, which means it's a recursive definition
            // If indeed it is never defined it will throw in reResolveTypeArgumentDeclarations
            couldNotResolveSomeTypeArgumentDecls = true
            StubTypeArgDecl
        }
        return TypeVariable(identifier, declaration)
    }

    private fun readPackageSpecifier(): PackageName {
        val packageEnd = findLastPackageSeparator()
        if (packageEnd == -1) return PackageName.Empty
        return PackageName.fromComponents(
            buildList {
                readRepeatedly(
                    until = { progressPointer > packageEnd },
                    reader = { add(readUntil('/', skip = true)) },
                    skip = false
                )
            }
        )
    }

    //    // signature <T:LInnerClassGenericTest.InnerClass<Ljava/util/ArrayList<Ljava/lang/String;>;>;>Ljava/lang/Object;
//// declaration: InnerClassGenericTest<T extends InnerClassGenericTest.InnerClass<java.util.ArrayList<java.lang.String>>>
    private fun findLastPackageSeparator(): Int {
        var currentIndex = progressPointer
        var lastSeparator = -1
        do {
            val current = signature[currentIndex]
            if (current == '/') lastSeparator = currentIndex
            currentIndex++
        } while (
        // Got to start of some generic parameter, means package specifier is over, e.g. foo/bar/List<String>
            current != '<' &&
            // Got to some inner class, e.g. foo/bar/Baz$Inner
            current != '$' &&
            // Got to the end of a class, e.g. foo/bar/Baz;
            current != ';'
        )
        return lastSeparator
    }

    private inline fun current() = signature[progressPointer]
    private inline fun advance() = progressPointer++
    private inline fun advance(checkChar: Char) {
        check(message = {
            "Expected $checkChar at position $progressPointer, but was ${current()} instead"
        }) { current() == checkChar }
        advance()
    }

    private inline fun check(message: () -> String = { "" }, check: () -> Boolean) {
        if (doChecks) {
            if (message().isNotEmpty()) check(check(), message)
            else check(check())
        }
    }

    private inline fun readUntil(until: (Char) -> Boolean, skip: Boolean): String {
        val start = progressPointer
        while (!until(current())) {
            advance()
        }
        val result = signature.substring(start, progressPointer)
        if (skip) advance()
        return result
    }

    private fun readUntil(symbol: Char, skip: Boolean): String = readUntil({ it == symbol }, skip)

    private inline fun <T> readRepeatedly(until: () -> Boolean, reader: () -> T, skip: Boolean): List<T> = buildList {
        while (!until()) {
            add(reader())
        }
        if (skip) advance()
    }
}



