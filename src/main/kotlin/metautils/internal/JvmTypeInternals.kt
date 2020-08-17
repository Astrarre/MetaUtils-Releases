package metautils.internal

import metautils.types.*

internal val jvmPrimitiveStringToType = mapOf(
    JvmPrimitiveTypes.Byte.classFileName to JvmPrimitiveTypes.Byte,
    JvmPrimitiveTypes.Char.classFileName to JvmPrimitiveTypes.Char,
    JvmPrimitiveTypes.Double.classFileName to JvmPrimitiveTypes.Double,
    JvmPrimitiveTypes.Float.classFileName to JvmPrimitiveTypes.Float,
    JvmPrimitiveTypes.Int.classFileName to JvmPrimitiveTypes.Int,
    JvmPrimitiveTypes.Long.classFileName to JvmPrimitiveTypes.Long,
    JvmPrimitiveTypes.Short.classFileName to JvmPrimitiveTypes.Short,
    JvmPrimitiveTypes.Boolean.classFileName to JvmPrimitiveTypes.Boolean
)

private val jvmPrimitiveStringToTypeChar = jvmPrimitiveStringToType.mapKeys { it.key[0] }

internal fun jvmTypeFromDescriptorString(descriptor: String): JvmType {
    jvmPrimitiveStringToType[descriptor]?.let { return it }
    require(descriptor.isNotEmpty()) { "A descriptor cannot be an empty string" }
    if (descriptor[0] == '[') return ArrayType(jvmTypeFromDescriptorString(descriptor.substring(1)))
    require(descriptor[0] == 'L' && descriptor.last() == ';')
    { "'$descriptor' is not a descriptor: A field descriptor must be primitive, an array ([), or a class (L)" }

    // I am unhappy substring copies the entire string
    return ObjectType.fromClassName(descriptor.substring(1, descriptor.length - 1), slashQualified = true)
}


internal fun methodDescriptorFromDescriptorString(descriptor: String): MethodDescriptor {
    require(descriptor[0] == '(') { "A method descriptor must begin with a '('" }
    val parameterDescriptors = mutableListOf<JvmType>()
    var parameterStartPos = 1
    var endPos: Int? = null
    var inClassName = false
    for (i in 1 until descriptor.length) {
        val c = descriptor[i]

        var descriptorTerminated = false

        if (inClassName) {
            if (c == ';') {
                descriptorTerminated = true
                inClassName = false
            }
        } else {
            if (jvmPrimitiveStringToTypeChar[c] != null) descriptorTerminated = true
        }

        if (c == 'L') inClassName = true

        if (descriptorTerminated) {
            parameterDescriptors.add(JvmType.fromDescriptorString(descriptor.substring(parameterStartPos, i + 1)))
            parameterStartPos = i + 1
        }

        if (c == ')') {
            require(!inClassName) { "Class name was not terminated" }
            endPos = i
            break
        }

    }

    requireNotNull(endPos) { "The parameter list of a method descriptor must end with a ')'" }
    require(endPos < descriptor.length - 1) { "A method descriptor must have a return type" }
    val returnDescriptorString = descriptor.substring(endPos + 1, descriptor.length)
    val returnDescriptor = if (returnDescriptorString == VoidJvmReturnType.classFileName) VoidJvmReturnType
    else JvmType.fromDescriptorString(returnDescriptorString)

    return MethodDescriptor(parameterDescriptors, returnDescriptor)

}