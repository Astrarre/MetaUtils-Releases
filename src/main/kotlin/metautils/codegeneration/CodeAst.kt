package metautils.codegeneration

import metautils.api.AnyJavaType
import metautils.api.JavaClassType
import metautils.types.jvm.JvmType
import metautils.types.jvm.ObjectType
import metautils.types.jvm.ReturnDescriptor

sealed class Code {
    override fun toString(): String {
        val (string, format) = JavaCodeWriter().writeCode(this)
        var index = 0
        return string.replace(Regex("\\\$T")) { format[index++].toString() }
    }
}


// all implementors of Receiver, Statement and Expression MUST inherit Code
val Receiver.code get() = this as Code
val Statement.code get() = this as Code

interface Receiver

class ClassReceiver(val type: ObjectType) : Receiver, Code()
object SuperReceiver : Receiver, Code()

interface Statement

interface Assignable

class ReturnStatement(val target: Expression) : Statement, Code()
class AssignmentStatement(val target: Assignable, val assignedValue: Expression) : Statement, Code()
sealed class ConstructorCall(val parameters: List<Pair<JvmType, Expression>>) : Statement, Code() {
    //        class This(parameters: List<Expression>, asmAccess: Int) : ConstructorCall(parameters, asmAccess)
    class Super(parameters: List<Pair<JvmType, Expression>>, val superType: ObjectType) : ConstructorCall(parameters)
}


interface Expression : Receiver

class VariableExpression(val name: String) : Expression, Assignable, Code()
class CastExpression(val target: Expression, val castTo: AnyJavaType) : Expression, Code()
class FieldExpression(val receiver: Receiver, val name: String, val owner: ObjectType, val type: JvmType) : Expression,
        Assignable, Code()

sealed class MethodCall(val parameters: List<Pair<JvmType, Expression>>) : Expression,
        Statement, Code() {

    abstract val receiver: Receiver?

    class Method(
        override val receiver: Receiver?,
        val name: String,
        parameters: List<Pair<JvmType, Expression>>,
        val methodAccess: MethodAccess,
        val receiverAccess: ClassAccess,
        val returnType: ReturnDescriptor,
        val owner: ObjectType
//        , val dontDoVirtualDispatch: Boolean
    ) : MethodCall(parameters)

    class Constructor(
            override val receiver: Expression?,
            val constructing: JavaClassType,
            parameters: List<Pair<JvmType, Expression>>
    ) : MethodCall(parameters)
}

class ArrayConstructor(val componentClass: JavaClassType, val size: Expression) : Expression, Code()

object ThisExpression : Expression, Code()

//sealed class Assignable, Code{
//    class Field : Assignable()
//}


enum class ClassVariant {
    Interface,
    ConcreteClass,
    AbstractClass,
    Enum,
    Annotation
}

data class ClassAccess(val isFinal: Boolean, val variant: ClassVariant)
data class MethodAccess(
    val isStatic: Boolean,
    val isFinal: Boolean,
    val isAbstract: Boolean,
    val visibility: Visibility
)

sealed class Visibility {
    companion object

    object Protected : Visibility() {
        override fun toString(): String = "protected "
    }
}

sealed class ClassVisibility : Visibility() {
    object Public : ClassVisibility() {
        override fun toString(): String = "public "
    }

    object Private : ClassVisibility() {
        override fun toString(): String = "private "
    }

    object Package : ClassVisibility() {
        override fun toString(): String = ""
    }
}

val Visibility.isPrivate get() = this == ClassVisibility.Private
