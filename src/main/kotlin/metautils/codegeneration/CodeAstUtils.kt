package codegeneration

import metautils.codegeneration.ClassVariant
import metautils.codegeneration.ClassVisibility
import metautils.codegeneration.Visibility
import javax.lang.model.element.Modifier


//fun Expression.castTo(type: AnyJavaType): CastExpression = CastExpression(this, type)
//fun Expression.castExpressionTo(type: AnyJavaType, doubleCast: Boolean): CastExpression =
//    if (doubleCast) castTo(JavaLangObjectJavaType).castTo(type) else castTo(type)

val Visibility.Companion.Public get() = ClassVisibility.Public
val Visibility.Companion.Private get() = ClassVisibility.Private
val Visibility.Companion.Package get() = ClassVisibility.Package


fun Visibility.toModifier(): Modifier? = when (this) {
    ClassVisibility.Public -> Modifier.PUBLIC
    ClassVisibility.Private -> Modifier.PRIVATE
    Visibility.Protected -> Modifier.PROTECTED
    ClassVisibility.Package -> null
}

val ClassVariant.isInterface get() = this == ClassVariant.Interface
val ClassVariant.isAbstract get() = this == ClassVariant.AbstractClass

