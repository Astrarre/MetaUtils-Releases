package metautils.types

//interface JvmReturnType {
//    val classfileName: String
//}
//
//interface JvmType : JvmReturnType, Tree
//interface JvmPrimitiveType : JvmType
//interface JvmObjectType : JvmType {
//    val className: QualifiedName
//}
//
//interface JvmArrayType : JvmType {
//    val componentType: JvmType
//}

//data class MethodDescriptor private constructor(
//    val parameterDescriptors: List<ParameterDescriptor>,
//    val returnDescriptor: ReturnDescriptor
//) : Descriptor("(${parameterDescriptors.joinToString("") { it.classFileName }})${returnDescriptor.classFileName}"),
//    Tree by branches(parameterDescriptors, returnDescriptor) {
//    companion object {
//        fun fromDescriptorString(descriptor: String) = methodDescriptorFromDescriptorString(descriptor)
//    }
//    override fun toString() = "(${parameterDescriptors.joinToString(", ")}): $returnDescriptor"
//}


//sealed class ReturnDescriptor(classFileName: String) : Descriptor(classFileName) {
//    object Void : ReturnDescriptor("V"), Leaf {
//        override fun toString() = "void"
//    }
//}


//sealed class JvmPrimitiveType(classFileName: String) : FieldType(classFileName), Leaf {
//    object Byte : JvmPrimitiveType("B") {
//        override fun toString() = "byte"
//    }
//
//    object Char : JvmPrimitiveType("C") {
//        override fun toString() = "char"
//    }
//
//    object Double : JvmPrimitiveType("D") {
//        override fun toString() = "double"
//    }
//
//    object Float : JvmPrimitiveType("F") {
//        override fun toString() = "float"
//    }
//
//    object Int : JvmPrimitiveType("I") {
//        override fun toString() = "int"
//    }
//
//    object Long : JvmPrimitiveType("J") {
//        override fun toString() = "long"
//    }
//
//    object Short : JvmPrimitiveType("S") {
//        override fun toString() = "short"
//    }
//
//    object Boolean : JvmPrimitiveType("Z") {
//        override fun toString() = "boolean"
//    }
//}


//data class ObjectType(val fullClassName: QualifiedName) :
//    FieldType("L${fullClassName.toSlashQualifiedString()};"), Tree by branch(fullClassName) {
//    override fun toString() = fullClassName.shortName.toDotQualifiedString()
//
//    constructor(qualifiedName: String, dotQualified: Boolean) : this(qualifiedName.toQualifiedName(dotQualified))
//}
//
//data class ArrayType(val componentType: FieldType) : FieldType("[" + componentType.classFileName),
//    Tree by branch(componentType) {
//    override fun toString() = "$componentType[]"
//}
//
//typealias ParameterDescriptor = FieldType

