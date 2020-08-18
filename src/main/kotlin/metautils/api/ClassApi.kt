package metautils.api



import metautils.codegeneration.ClassAccess
import metautils.codegeneration.MethodAccess
import metautils.codegeneration.Visibility
import metautils.internal.visiting
import metautils.signature.TypeArgumentDeclaration
import metautils.signature.toJvmType
import metautils.types.classFileName
import metautils.util.*


interface Visible {
    val visibility: Visibility
}


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
    private var classInFieldForDataClassCopying: ClassApi? = null
) : Visible, Visitable by visiting(typeArguments, superInterfaces, methods, fields, superClass/*, outerClass*/) {

    override fun equals(other: Any?): Boolean = other is ClassApi && other.name == name
    override fun hashCode(): Int = name.hashCode()

    val outerClass : ClassApi? get() = classInFieldForDataClassCopying

    internal fun init(classIn: ClassApi?) {
        this.classInFieldForDataClassCopying = classIn
    }


    override fun toString(): String {
        return visibility.toString() + "static ".includeIf(isStatic) + "final ".includeIf(isFinal) + name.shortName
    }

    companion object;


    abstract class Member : Visible {
        abstract val name: String
        val classIn: ClassApi get() = classInField!!

        protected abstract var classInField: ClassApi?

        // No other way of doing this because it's a recursive definition
        internal fun init(classIn: ClassApi) {
            classInField = classIn
        }

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
    ) : Member(), Visitable by visiting(parameters.values, typeArguments, throws, returnType) {
        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()

        override fun toString() = "static ".includeIf(isStatic) +
                "$name(${parameters.joinToString { (name, type) -> "$name: $type" }}): $returnType"

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
    ) : Member(), Visitable by visiting(type) {
        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()

        override fun toString() = "static ".includeIf(isStatic) + "$name: $type"
        val locallyUniqueIdentifier by lazy { name + type.toJvmType().classFileName }
    }
}


