package metautils.api



import metautils.codegeneration.ClassAccess
import metautils.codegeneration.MethodAccess
import metautils.codegeneration.Visibility
import metautils.signature.TypeArgumentDeclaration
import metautils.signature.toJvmType
import metautils.util.*

interface GraphNode : Tree {
    val presentableName: String
    val globallyUniqueIdentifier: String
}

interface Visible {
    val visibility: Visibility
}

//TODO: get rid of all things that accept both classapi and member, that's not needed anymore


/**
 * [ClassApi]es use dot.separated.format for the packageName always!
 */
data class ClassApi(
    val annotations: List<JavaAnnotation>,
    override val visibility: Visibility,
    val access: ClassAccess,
    val isStatic: Boolean,
    val name: QualifiedName,
    val typeArguments: List<TypeArgumentDeclaration>,
    val superClass: JavaClassType?,
    val superInterfaces: List<JavaClassType>,
    val methods: Collection<Method>,
    val fields: Collection<Field>,
    val innerClasses: List<ClassApi>,
//    val outerClass: ClassApi?,
    private var classInFieldForDataClassCopying: ClassApi? = null
) : Visible, Tree by branches(typeArguments, superInterfaces, methods, fields, superClass/*, outerClass*/),
    GraphNode {

    override fun equals(other: Any?): Boolean = other is ClassApi && other.name == name
    override fun hashCode(): Int = name.hashCode()

    val outerClass : ClassApi? get() = classInFieldForDataClassCopying

    internal fun init(classIn: ClassApi?) {
        this.classInFieldForDataClassCopying = classIn
    }

    override val presentableName: String
        get() = name.presentableName
    override val globallyUniqueIdentifier: String
        get() = name.toSlashString()

    override fun toString(): String {
        return visibility.toString() + "static ".includeIf(isStatic) + "final ".includeIf(isFinal) + name.shortName
    }

    companion object;


    abstract class Member : Visible, GraphNode {
        abstract val name: String
        val classIn: ClassApi get() = classInField!!

        protected abstract var classInField: ClassApi?

        // No other way of doing this because it's a recursive definition
        internal fun init(classIn: ClassApi) {
            classInField = classIn
        }

        //        abstract val signature: Signature
        abstract val isStatic: Boolean
    }


    data class Method(
            override val name: String,
            val returnType: JavaReturnType,
            val parameters: List<Pair<String, AnyJavaType>>,
            val typeArguments: List<TypeArgumentDeclaration>,
            val throws: List<JavaThrowableType>,
            val access: MethodAccess,
        // For data class copying
            override var classInField: ClassApi? = null
    ) : Member(), Tree by branches(parameters.values, typeArguments, throws, returnType) {
        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()

        override fun toString() = "static ".includeIf(isStatic) +
                "$name(${parameters.joinToString { (name, type) -> "$name: $type" }}): $returnType"

        override val presentableName: String
            get() = "$name(" +
                    parameters.values.joinToString { it.toJvmType().toString() }
                        .includeIf(classIn.methods.any { it != this && it.name == name }) + ")"

        override val globallyUniqueIdentifier: String by lazy { classIn.globallyUniqueIdentifier + "#" + locallyUniqueIdentifier }
        val locallyUniqueIdentifier: String by lazy { name + getJvmDescriptor().classFileName }

        override val isStatic = access.isStatic
        override val visibility = access.visibility
    }

    data class Field(
            override val name: String,
            val type: AnyJavaType,
            override val isStatic: Boolean,
            override val visibility: Visibility,
            val isFinal: Boolean,
        // For data class copying
            override var classInField: ClassApi? = null
    ) : Member(), Tree by branch(type) {
        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()

        override fun toString() = "static ".includeIf(isStatic) + "$name: $type"
        override val presentableName: String
            get() = name
        override val globallyUniqueIdentifier by lazy { classIn.globallyUniqueIdentifier + "." + locallyUniqueIdentifier }
        val locallyUniqueIdentifier by lazy { name + type.toJvmType().classFileName }
    }
}


