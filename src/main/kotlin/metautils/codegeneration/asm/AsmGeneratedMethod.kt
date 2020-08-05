package metautils.codegeneration.asm

import codegeneration.*
import metautils.codegeneration.*
import metautils.util.*
import org.objectweb.asm.Opcodes
import metautils.signature.outerClass
import metautils.signature.toJvmType
import metautils.types.jvm.*

private const val printStackOps = false

private class JvmState(
    private val methodName: String,
    private val methodStatic: Boolean,
    parameters: List<Pair<String, JvmType>>
) {
    fun maxStackSize(): Int {
        if (currentStack != 0) error("$currentStack element(s) on the stack were not used yet")
        if (printStackOps) {
            println("Method complete")
        }
        return maxStack
    }

    fun getVariable(variable: VariableExpression): JvmLocalVariable {
        val type = lvTable[variable.name]
        requireNotNull(type) { "Attempt to reference variable ${variable.name} when no such variable is declared" }
        return type
    }

    private val lvTable: MutableMap<String, JvmLocalVariable> = buildLvTable(parameters)

    private fun buildLvTable(parameters: List<Pair<String, JvmType>>): MutableMap<String, JvmLocalVariable> {
        val map = mutableMapOf<String, JvmLocalVariable>()
        // The this variable is passed as the first argument in non-static methods
        var currentIndex = if (methodStatic) 0 else 1
        for ((name, type) in parameters) {
            map[name] = JvmLocalVariable(currentIndex, type)
            currentIndex += type.byteWidth()
        }
        return map
    }


    // Max local variables is not tracked yet, assuming no local variables are created
    private var maxStack = 0
    private var currentStack = 0

    inline fun trackPush(type: ReturnDescriptor, message: () -> String = { "" }) {
        trackPush(type.byteWidth()) { message() + " of type $type" }
    }

    inline fun trackReferencePush(message: () -> String = { "" }) {
        trackPush(1) { message() + " of reference type" }
    }

    inline fun trackPop(type: ReturnDescriptor, message: () -> String = { "" }) {
        trackPop(type.byteWidth()) { message() + " of type $type" }
    }

    inline fun trackReferencePop(message: () -> String = { "" }) {
        trackPop(1) { message() + " of reference type" }
    }

    private inline fun trackPush(/*type: ReturnDescriptor,*/ width: Int, message: () -> String) {
        val oldCurrent = currentStack
        currentStack += width

        if (maxStack < currentStack) maxStack = currentStack

        if (printStackOps) {
            val str = message().let { if (it.isEmpty()) "operation" else it }
            println("$str: $oldCurrent -> $currentStack")
        }

    }

    private inline fun trackPop(/*type: ReturnDescriptor,*/ width: Int, message: () -> String) {
        if (printStackOps) {
            val str = message().let { if (it.isEmpty()) "operation" else it }
            print("$str: ")
        }
//        val width = type.byteWidth()
        if (currentStack < width) {
            error("Attempt to pop $width byte(s) from stack when it only contains $currentStack byte(s) in method $methodName")
        }
        val oldCurrent = currentStack
        currentStack -= width
        if (printStackOps) {
            println("$oldCurrent -> $currentStack")
        }
    }
}

private data class JvmLocalVariable(val index: Int, val type: JvmType)


const val ConstructorsName = "<init>"

internal class AsmGeneratedMethod(
    private val methodWriter: AsmClassWriter.MethodBody,
    private val returnType: ReturnDescriptor,
    name: String,
    isStatic: Boolean,
    parameters: List<Pair<String, JvmType>>
) : GeneratedMethod {
    fun maxStackSize(): Int = state.maxStackSize()

    private val state = JvmState(name, isStatic, parameters)


    private fun Receiver.addOpcodes() = code.addOpcodes()
    private fun Statement.addOpcodes() = code.addOpcodes()


//    private fun Expression.getVariable(): JvmLocalVariable = when(this){
//        is ArrayConstructor
//    }

    // Everything assumes we just pass the parameters in order, very simplistic, a real solution would have a variableIndexTable
    private fun Code.addOpcodes(): Unit = with(state) {
        when (this@addOpcodes) {
            is ClassReceiver -> {
            }
            SuperReceiver -> ThisExpression.code.addOpcodes()
            is ReturnStatement -> {
                // the RETURN is added by addMethod()
                target.addOpcodes()
                trackPop(returnType) { "return" }
            }
            is AssignmentStatement -> {
                if (target is FieldExpression) target.receiver.addOpcodes()
                assignedValue.addOpcodes()
                target.assign()
            }
            is ConstructorCall.Super -> {
                invoke(
                    opcode = Opcodes.INVOKESPECIAL,
                    methodName = ConstructorsName,
                    isInterface = false,
                    returnType = ReturnDescriptor.Void,
                    owner = superType,
                    parameterTypes = parameters.keys,
                    parametersThatWillBePopped = superType.prependTo(parameters.keys),
                    parametersToLoad = ThisExpression.prependTo(parameters.values)
                )
            }
            is VariableExpression -> {
                val (index, type) = state.getVariable(this@addOpcodes)
                methodWriter.writeLvArgInstruction(type.asmType().getOpcode(Opcodes.ILOAD), index)
                trackPush(type) { "variable get" }
            }
            is CastExpression -> {
                target.addOpcodes()
                methodWriter.writeTypeArgInstruction(Opcodes.CHECKCAST, castTo.toJvmType())
            }
            is FieldExpression -> {
                receiver.addOpcodes()
                val opcode = if (receiver is ClassReceiver) Opcodes.GETSTATIC else Opcodes.GETFIELD
                methodWriter.writeFieldArgInstruction(opcode, owner, name, type)
                if (receiver !is ClassReceiver) trackPop(owner) { "field receiver consume" }
                trackPush(type) { "field get" }
            }
            is MethodCall -> addMethodCall()

            is ArrayConstructor -> {
                trackPush(JvmPrimitiveType.Int) { "push array size" }
                methodWriter.writeLvArgInstruction(Opcodes.ILOAD, 0)
                // Assumes reference type array
                trackPop(JvmPrimitiveType.Int) { "pass array size" }
                methodWriter.writeTypeArgInstruction(Opcodes.ANEWARRAY, componentClass.toJvmType())
                trackReferencePush { "push array result" }
            }
            ThisExpression -> {
                trackReferencePush { "push this" }
                methodWriter.writeLvArgInstruction(Opcodes.ALOAD, 0)
            }
        }
    }

    private fun Assignable.assign() = when (this) {
        is VariableExpression -> error("Variable expressions are not supported by the ASM generator yet") // soft to do
        is FieldExpression -> {
//            receiver.addOpcodes()
            val isStatic = receiver is ClassReceiver
            val opcode = if (isStatic) Opcodes.PUTSTATIC else Opcodes.PUTFIELD
            if (!isStatic) state.trackReferencePop { "pass field receiver" }
            state.trackPop(type) { "pass field value" }
            methodWriter.writeFieldArgInstruction(opcode, owner, name, type)
        }
        else -> error("Impossible")
    }

    private fun MethodAccess.invokeOpcode(isInterface: Boolean, isSuperCall: Boolean) = when {
        isStatic -> Opcodes.INVOKESTATIC
        // Final methods sometimes use INVOKESPECIAL but analyzing that is difficult, INVOKEVIRTUAL will work for our purposes.
        !isSuperCall && !visibility.isPrivate -> {
            if (isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        }
        else -> Opcodes.INVOKESPECIAL
    }

    private fun invoke(
        opcode: Int,
        methodName: String,
        parameterTypes: List<JvmType>,
        parametersToLoad: List<Receiver>,
        // This is needed just for checks, we don't actually need this information technically
        parametersThatWillBePopped: List<JvmType>,
        returnType: ReturnDescriptor,
        owner: ObjectType,
        isInterface: Boolean/*,
        parametersAlreadyLoaded: List<JvmType> = listOf()*/
    ) {
        parametersToLoad.forEach { it.addOpcodes() }
        parametersThatWillBePopped.forEach { state.trackPop(it) { "pass parameter" } }

        val descriptor = MethodDescriptor(parameterTypes, returnType)

        state.trackPush(returnType) { "push method result" }
        methodWriter.writeMethodCall(opcode, owner, methodName, descriptor, isInterface)
    }

    private fun MethodCall.addMethodCall() = when (this) {
        is MethodCall.Method -> {
            val isInterface = receiverAccess.variant.isInterface
            val receiver = if (methodAccess.isStatic) null else receiver ?: ThisExpression
            val receiverType = if (methodAccess.isStatic) null else owner
            invoke(
                opcode = methodAccess.invokeOpcode(isInterface, isSuperCall = receiver == SuperReceiver),
                methodName = name,
                returnType = returnType,
                owner = owner,
                isInterface = isInterface,
                parametersToLoad = parameters.values.prependIfNotNull(receiver),
                parametersThatWillBePopped = parameters.keys.prependIfNotNull(receiverType),
                parameterTypes = parameters.keys
            )
        }
        is MethodCall.Constructor -> {
            methodWriter.writeTypeArgInstruction(Opcodes.NEW, constructing.toJvmType())
            state.trackReferencePush { "NEW operation on ${constructing.toJvmType().toJvmString()}" }
            methodWriter.writeZeroOperandInstruction(Opcodes.DUP)
            state.trackReferencePush { "DUP operation on ${constructing.toJvmType().toJvmString()}" }

            // outer class is implicitly the first parameter in java, but explicitly the first parameter in bytecode
            val explicitlyDeclaredMethodParameters = parameters.keys.applyIf(receiver != null) {
                // Add outer class as first param to inner class
                constructing.outerClass().toJvmType().prependTo(it)
            }

            invoke(
                opcode = Opcodes.INVOKESPECIAL,
                methodName = ConstructorsName,
                parameterTypes = explicitlyDeclaredMethodParameters,
                parametersToLoad = parameters.values.prependIfNotNull(receiver), // inner class
                parametersThatWillBePopped = constructing.toJvmType().prependTo(explicitlyDeclaredMethodParameters),
                returnType = ReturnDescriptor.Void,
                owner = constructing.toJvmType(),
                isInterface = false
            )
        }
    }

    override fun addStatement(statement: Statement) {
        if (printStackOps) println("Adding statement $statement")
        statement.addOpcodes()
    }

    override fun addJavadoc(comment: String) {
    }

}