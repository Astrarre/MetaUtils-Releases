package metautils.asm

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

sealed class AsmNode<N> {
    abstract val nodeName: String

    /**
     * Note: these are invisible annotations
     */
    abstract val annotations: MutableList<AnnotationNode>
    abstract val fullyQualifiedName: String
    abstract val uniqueIdentifier: UniqueIdentifier
    abstract val node: N
}

sealed class UniqueIdentifier {
    data class NameDesc(val name: String, val descriptor: String) : UniqueIdentifier()
    data class Name(val name: String) : UniqueIdentifier()

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}


class ClassAsmNode(override val node: ClassNode, private val outerClassName: String?) : AsmNode<ClassNode>() {
    override val annotations: MutableList<AnnotationNode> get() = node.invisibleAnnotations ?: mutableListOf()
    override val fullyQualifiedName get() = outerClassName + "\$" + node.name
    override val nodeName get() = "class"
    override val uniqueIdentifier get() = UniqueIdentifier.Name(node.name)
}

class FieldAsmNode(override val node: FieldNode, private val className: String) : AsmNode<FieldNode>() {
    override val annotations: MutableList<AnnotationNode> get() = node.invisibleAnnotations ?: mutableListOf()
    override val fullyQualifiedName get() = className + "#" + node.name
    override val nodeName get() = "field"
    override val uniqueIdentifier get() = UniqueIdentifier.NameDesc(node.name, node.desc)
}

class MethodAsmNode(override val node: MethodNode, private val className: String) : AsmNode<MethodNode>() {
    override val annotations: MutableList<AnnotationNode> get() = node.invisibleAnnotations ?: mutableListOf()
    override val fullyQualifiedName get() = className + "#" + node.name + node.desc
    override val nodeName get() = "method"
    override val uniqueIdentifier get() = UniqueIdentifier.NameDesc(node.name, node.desc)
}

//soft to do: handle passing of class name information in the case of inner classes
val ClassNode.fieldsWrapped get() = fields.map { FieldAsmNode(it, name) }
val ClassNode.methodsWrapped get() = methods.map { MethodAsmNode(it, name) }


class AnnotateableVisitor(val visitAnnotation: (descriptor: String, visible:  Boolean) -> AnnotationVisitor)

val ClassVisitor.annotateable get() = AnnotateableVisitor { descriptor, visible -> visitAnnotation(descriptor, visible) }
val MethodVisitor.annotateable get() = AnnotateableVisitor { descriptor, visible -> visitAnnotation(descriptor, visible) }
val FieldVisitor.annotateable get() = AnnotateableVisitor { descriptor, visible -> visitAnnotation(descriptor, visible) }
//val AnnotationVisitor.annotateable get() = AnnotateableNode { name, visible -> visitAnnotation(name, visible) }