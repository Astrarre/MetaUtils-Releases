package metautils.codegeneration


import codegeneration.*
import metautils.api.AnyJavaType
import metautils.api.JavaReturnType
import metautils.api.JavaType
import com.squareup.javapoet.*
import metautils.signature.TypeArgumentDeclaration
import metautils.util.PackageName
import metautils.util.applyIf
import metautils.util.toDotString
import java.nio.file.Path
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier


object JavaCodeGenerator : CodeGenerator {

    override fun writeClass(
        info: ClassInfo,
        packageName: PackageName,
        srcRoot: Path
    ) {
        val generatedClass = generateClass(info)
        JavaFile.builder(
            packageName.toDotString(),
            generatedClass.build()
        ).skipJavaLangImports(true).build().writeTo(srcRoot)
    }


}

private fun generateClass(info: ClassInfo): TypeSpec.Builder = with(info) {
    val builder =
        if (access.variant.isInterface) TypeSpec.interfaceBuilder(shortName) else TypeSpec.classBuilder(shortName)
    builder.apply {
        visibility.toModifier()?.let { addModifiers(it) }
        if (access.variant.isAbstract) addModifiers(Modifier.ABSTRACT)
        if (access.isFinal) addModifiers(Modifier.FINAL)
        if (superClass != null) superclass(superClass.toTypeName())
        for (superInterface in superInterfaces) addSuperinterface(superInterface.toTypeName())
        addTypeVariables(typeArguments.map { it.toTypeName() })
        addAnnotations(this@with.annotations.map { it.toAnnotationSpec() })
    }
    JavaGeneratedClass(builder, access.variant.isInterface).body()
    return builder
}

private fun generateMethod(info: MethodInfo, methodName: String?): MethodSpec.Builder = with(info) {
    val builder = if (methodName != null) MethodSpec.methodBuilder(methodName) else MethodSpec.constructorBuilder()
    builder.apply {
        JavaGeneratedMethod(this).apply(body)
        addParameters(info.parameters.map { (name, type, javadoc) ->
            ParameterSpec.builder(type.toTypeName(), name.applyIf(!SourceVersion.isName(name)) { it + "_" }).apply {
//                addAnnotations(type.annotations.map { it.toAnnotationSpec() })
                if (javadoc != null) addJavadoc(javadoc)
            }.build()
        })
        addExceptions(throws.map { it.toTypeName() })
        visibility.toModifier()?.let { addModifiers(it) }
    }

}

class JavaGeneratedClass(
    private val typeSpec: TypeSpec.Builder,
    private val isInterface: Boolean
) : GeneratedClass {

    override fun addMethod(
        methodInfo: MethodInfo,
        isStatic: Boolean,
        isFinal: Boolean,
        isAbstract: Boolean,
        typeArguments: List<TypeArgumentDeclaration>,
        name: String,
        returnType: JavaReturnType
    ) {
        val method = generateMethod(methodInfo, name).apply {
            addTypeVariables(typeArguments.map { it.toTypeName() })
            //soft to do: the fact the annotations are attached to the return type is a bit wrong,
            // but in java if you put the same annotation on the method and return type
            // it counts as duplicating the annotation...
            returns(returnType.type.toTypeName())
            addAnnotations(returnType.annotations.map { it.toAnnotationSpec() })

            when {
                isAbstract -> addModifiers(Modifier.ABSTRACT)
                isStatic -> addModifiers(Modifier.STATIC)
                isInterface -> addModifiers(Modifier.DEFAULT)
            }

            if (isFinal) addModifiers(Modifier.FINAL)

        }.build()

        typeSpec.addMethod(method)
    }


    override fun addConstructor(info: MethodInfo) {
        require(!isInterface) { "Interfaces don't have constructors" }
        typeSpec.addMethod(generateMethod(info, methodName = null).build())
    }

    override fun addInnerClass(info: ClassInfo, isStatic: Boolean) {
        val generatedClass = generateClass(info)
        typeSpec.addType(generatedClass.apply {
            if (isStatic) addModifiers(Modifier.STATIC)
        }.build())
    }

    override fun addField(
            name: String,
            type: AnyJavaType,
            visibility: Visibility,
            isStatic: Boolean,
            isFinal: Boolean,
            initializer: Expression?
    ) {
        typeSpec.addField(FieldSpec.builder(type.toTypeName(), name)
            .apply {
                visibility.toModifier()?.let { addModifiers(it) }
                if (isStatic) addModifiers(Modifier.STATIC)
                if (isFinal) addModifiers(Modifier.FINAL)
                if (initializer != null) {
                    val (format, arguments) = JavaCodeWriter().writeCode(initializer.code)
                    initializer(format, *arguments.toTypeNames())
                }
                addAnnotations(type.annotations.map { it.toAnnotationSpec() })
            }
            .build()
        )
    }

    override fun addJavadoc(comment: String) {
        typeSpec.addJavadoc(comment)
    }


}

private fun List<JavaType<*>>.toTypeNames() = map { it.toTypeName(annotate = false) }.toTypedArray()


class JavaGeneratedMethod(private val methodSpec: MethodSpec.Builder) : GeneratedMethod {

    override fun addStatement(statement: Statement) {
        val (format, arguments) = JavaCodeWriter().writeCode(statement.code)
        methodSpec.addStatement(format, *arguments.toTypeNames())
    }

    override fun addJavadoc(comment: String) {
        methodSpec.addJavadoc(comment)
    }

}


