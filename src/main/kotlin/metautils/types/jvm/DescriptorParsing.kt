package metautils.types.jvm

import metautils.types.internal.jvmPrimitiveStringToTypeChar
import metautils.types.internal.jvmPrimitiveStringToType

//TODO: keep going for other classes if we split this up into a seperate lib

internal fun jvmTypeFromDescriptorString(descriptor: String): FieldDescriptor {
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
    val parameterDescriptors = mutableListOf<FieldType>()
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
            parameterDescriptors.add(FieldDescriptor.fromDescriptorString(descriptor.substring(parameterStartPos, i + 1)))
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
    val returnDescriptor = if (returnDescriptorString == ReturnDescriptor.Void.classFileName) ReturnDescriptor.Void
    else FieldDescriptor.fromDescriptorString(returnDescriptorString)

    return MethodDescriptor(parameterDescriptors, returnDescriptor)

}