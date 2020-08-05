package metautils.codegeneration

import com.squareup.javapoet.*
import metautils.api.AnnotationValue
import metautils.api.JavaAnnotation
import metautils.api.JavaType
import metautils.types.jvm.ArrayType
import metautils.types.jvm.JvmPrimitiveType
import metautils.types.jvm.JvmType
import metautils.types.jvm.ObjectType
import metautils.signature.*
import metautils.util.*

internal sealed class CodeWriter {
    /**
     * Note: This is a pure operation, I just thing "CodeWriter" is the most fitting name.
     */
    abstract fun writeCode(code: Code): FormattedString
    abstract fun writeAnnotationValue(value: AnnotationValue): FormattedString
}


//internal object KotlinCodeWriter : JavaCodeWriter() {
//    override fun write(code: Code): FormattedString  = when(code){
//        is CastExpression -> "".format
//        else -> super.write(code)
//    }
//}

internal open class JavaCodeWriter : CodeWriter() {
    private fun write(expression: Expression) = writeCode(expression.code)
    private fun write(rec: Receiver) = writeCode(rec.code)
    override fun writeCode(code: Code): FormattedString = when (code) {
        is VariableExpression -> code.name.format
        is CastExpression -> write(code.target).mapString { "($TYPE_FORMAT)$it" }
                .prependArg(code.castTo)
        is FieldExpression -> write(code.receiver).mapString { "${it.withParentheses()}.${code.name}" }
        ThisExpression -> "this".format
        is MethodCall -> {
            val (prefixStr, prefixArgs) = code.prefix()
            code.parameters.toParameterList().mapString { "$prefixStr$it" }.prependArgs(prefixArgs)
        }
        is ReturnStatement -> write(code.target).mapString { "return $it" }
        is ClassReceiver -> TYPE_FORMAT.formatType(code.type.toRawJavaType())
        SuperReceiver -> "super".format
        is AssignmentStatement -> writeCode(code.target as Code).mapString { "$it = " } + write(code.assignedValue)
        is ArrayConstructor -> write(code.size).mapString { "new $TYPE_FORMAT[$it]" }
                .prependArg(code.componentClass)
//        is ConstructorCall.This -> code.parameters.toParameterList().mapString { "this$it" }
        is ConstructorCall.Super -> code.parameters.toParameterList().mapString { "super$it" }
    }

    override fun writeAnnotationValue(value: AnnotationValue): FormattedString = when (value) {
        is AnnotationValue.Array -> {
            val elements = value.components.map { writeAnnotationValue(it) }
            val string = "[" + elements.joinToString(", ") { it.string } + "]"
            string.formatType(elements.flatMap { it.formatArguments })
        }
        is AnnotationValue.Annotation -> {
            val elements = value.annotation.parameters.map { (name, value) -> name to writeAnnotationValue(value) }
            val arguments = elements.joinToString(", ") { (name, format) -> "$name = ${format.string}" }
            val string = "@$TYPE_FORMAT($arguments)"
            string.formatType(value.annotation.type.toRawJavaType())
                    .appendArgs(elements.flatMap { (_, format) -> format.formatArguments })
        }
        is AnnotationValue.Primitive.Num -> value.primitive.toString().format
        is AnnotationValue.Primitive.Bool -> (if (value.primitive) "true" else "false").format
        is AnnotationValue.Primitive.Cha -> "'${value.primitive}'".format
        is AnnotationValue.Primitive.Str -> "\"${value.primitive}\"".format
        is AnnotationValue.Enum -> "$TYPE_FORMAT.${value.constant}".formatType(value.type.toRawJavaType())
        is AnnotationValue.ClassType -> value.type.toFormattedString().mapString { "$it.class" }
    }

    private fun JvmPrimitiveType.toFormat() = when (this) {
        JvmPrimitiveType.Byte -> "byte"
        JvmPrimitiveType.Char -> "char"
        JvmPrimitiveType.Double -> "double"
        JvmPrimitiveType.Float -> "float"
        JvmPrimitiveType.Int -> "int"
        JvmPrimitiveType.Long -> "long"
        JvmPrimitiveType.Short -> "short"
        JvmPrimitiveType.Boolean -> "boolean"
    }

    private fun JvmType.toFormattedString(): FormattedString = when (this) {
        is JvmPrimitiveType -> toFormat().format
        is ObjectType -> TYPE_FORMAT.formatType(toRawJavaType())
        is ArrayType -> componentType.toFormattedString().mapString { "$it[]" }
    }

    private fun List<Pair<JvmType, Expression>>.toParameterList(): FormattedString {
        val parametersCode = map { write(it.second) }
        val totalArgs = parametersCode.flatMap { it.formatArguments }
        return ("(" + parametersCode.joinToString(", ") { it.string } + ")").formatType(totalArgs)
    }

    // Add parentheses to casts and constructor calls
    private fun String.withParentheses() =
            if ((startsWith("(") || startsWith("new")) && !startsWith(")")) "($this)" else this
//    private fun String.removeParentheses() = if (startsWith("(")) substring(1, length - 1) else this

    private fun MethodCall.prefix(): FormattedString = when (this) {
        is MethodCall.Method -> if (receiver == null) name.format else writeCode(receiver as Code)
                .mapString { "${it.withParentheses()}.$name" }
        is MethodCall.Constructor -> {
            val rightSide = "new $TYPE_FORMAT".formatType(constructing)
            if (receiver == null) rightSide
            else write(receiver).mapString { "${it.withParentheses()}." } + rightSide
        }
    }

}


private fun JvmPrimitiveType.toTypeName(): TypeName = when (this) {
    JvmPrimitiveType.Byte -> TypeName.BYTE
    JvmPrimitiveType.Char -> TypeName.CHAR
    JvmPrimitiveType.Double -> TypeName.DOUBLE
    JvmPrimitiveType.Float -> TypeName.FLOAT
    JvmPrimitiveType.Int -> TypeName.INT
    JvmPrimitiveType.Long -> TypeName.LONG
    JvmPrimitiveType.Short -> TypeName.SHORT
    JvmPrimitiveType.Boolean -> TypeName.BOOLEAN
}

fun TypeArgumentDeclaration.toTypeName(): TypeVariableName = TypeVariableName.get(
        name,
        *interfaceBounds.prependIfNotNull(classBound).map { it.toTypeName() }.toTypedArray()
)

fun JavaType<*>.toTypeName(annotate: Boolean = true): TypeName {
    return type.toTypeName().applyIf(annotate) { it.annotated(annotations.map { it.toAnnotationSpec() }) }
}

fun JavaAnnotation.toAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(type.toTypeName()).apply {
        for ((name, value) in parameters) {
            val (string, format) = JavaCodeWriter().writeAnnotationValue(value)
            addMember(name, string, *format.map { it.toTypeName() }.toTypedArray())
        }
    }.build()
}

fun GenericReturnType.toTypeName(): TypeName = when (this) {
    is GenericsPrimitiveType -> primitive.toTypeName()
    is ClassGenericType -> toTypeName()
    is TypeVariable -> TypeVariableName.get(name)
    is ArrayGenericType -> ArrayTypeName.of(componentType.toTypeName())
    GenericReturnType.Void -> TypeName.VOID
}

private fun ClassGenericType.toTypeName(): TypeName {
    val outerClass = classNameSegments[0]
    val outerClassArgs = outerClass.typeArguments
    val innerClasses = classNameSegments.drop(1)
//    require() {
//        "Inner class type arguments cannot be translated to a JavaPoet representation"
//    }

    val isGenericType = classNameSegments.any { it.typeArguments != null }

    val outerRawType = ClassName.get(
            packageName?.toDotString() ?: "",
            classNameSegments[0].name,
            *(if (isGenericType) arrayOf() else innerClasses.map { it.name }.toTypedArray())
    )

    return if (!isGenericType) outerRawType else {
        if (outerClassArgs == null) {
            when (innerClasses.size) {
                0 -> outerRawType
                1 -> ParameterizedTypeName.get(
                        outerRawType.nestedClass(innerClasses[0].name),
                        *innerClasses[0].typeArguments.toTypeName().toTypedArray()
                )
                else -> {
                    if (innerClasses[0].typeArguments == null) {
                        assert(innerClasses[1].typeArguments != null)
                        ParameterizedTypeName.get(
                                outerRawType.nestedClass(innerClasses[0].name).nestedClass(innerClasses[1].name),
                                *innerClasses[1].typeArguments.toTypeName().toTypedArray()
                        )
                    } else {
                        // This would require pretty complicated handling in the general case, thanks jake wharton
                        error("2-deep inner classes with a type argument in the middle class is not expected")
                    }
                }
            }

        } else {
            innerClasses.fold(
                    ParameterizedTypeName.get(
                            outerRawType,
                            *outerClassArgs.toTypeName().toTypedArray()
                    )
            ) { acc, classSegment ->
                acc.nestedClass(classSegment.name, classSegment.typeArguments.toTypeName())
            }
        }
    }
}

private fun List<TypeArgument>?.toTypeName() = this?.map { it.toTypeName() } ?: listOf()

private fun TypeArgument.toTypeName(): TypeName = when (this) {
    is TypeArgument.SpecificType -> {
        val bound = type.toTypeName()
        when (wildcardType) {
            WildcardType.Extends -> WildcardTypeName.subtypeOf(bound)
            WildcardType.Super -> WildcardTypeName.supertypeOf(bound)
            null -> bound
        }
    }
    TypeArgument.AnyType -> WildcardTypeName.subtypeOf(TypeName.OBJECT)
}

//fun JavaAnnotation.toAnnotationSpec(): AnnotationSpec = AnnotationSpec.builder(type.toTypeName()).build()

private fun ObjectType.toTypeName(): ClassName {
    val shortName = fullClassName.shortName
    return ClassName.get(
            fullClassName.packageName.toDotString(), shortName.outerMostClass(),
            *shortName.innerClasses().toTypedArray()
    )
}

internal data class FormattedString(val string: String, val formatArguments: List<JavaType<*>>) {
    fun mapString(map: (String) -> String) = copy(string = map(string))
    fun appendArg(arg: JavaType<*>) = copy(formatArguments = formatArguments + arg)
    fun appendArgs(args: List<JavaType<*>>) = copy(formatArguments = formatArguments + args)
    fun prependArg(arg: JavaType<*>) = copy(formatArguments = listOf(arg) + formatArguments)
    fun prependArgs(args: List<JavaType<*>>) = copy(formatArguments = args + formatArguments)

    operator fun plus(other: FormattedString) =
            FormattedString(this.string + other.string, formatArguments + other.formatArguments)
}


private val String.format get() = FormattedString(this, listOf())
private fun String.formatType(args: List<JavaType<*>>) = FormattedString(this, args)
private fun String.formatType(arg: JavaType<*>) = FormattedString(this, listOf(arg))

private const val TYPE_FORMAT = "\$T"

