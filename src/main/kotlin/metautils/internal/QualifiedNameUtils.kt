@file:Suppress("SimplifyBooleanWithConstants")

package metautils.internal

import metautils.signature.SimpleClassGenericType
import metautils.util.PackageName
import metautils.util.QualifiedName
import metautils.util.ShortClassName

internal object PackageQualifier{
    private const val Dot = '.'
    private const val Slash = '/'

    operator fun invoke(slash: Boolean) = if(slash) Slash else Dot
}

internal object ClassQualifier {
    private const val Dot = '.'
    private const val Dollar = '$'

    operator fun invoke(dollar: Boolean) = if(dollar) Dollar else Dot
}
internal fun parseQualifiedName(
    slashQualified: Boolean,
    dollarQualified: Boolean,
    name: String
): QualifiedName {
    require(slashQualified == true || dollarQualified == true) {
        "Can't parse qualified name string if it's separated by dots " +
                "in both the package and the inner class names - can't know where the package ends."
    }

    val packageSeparator = PackageQualifier(slashQualified)
    val packageName = name.substringBeforeLast(packageSeparator)
    if (packageName.length == name.length) {
        // If the length is the size it means there's no package split,
        // which means it's just a class name with no package.
        return QualifiedName(
            packageName = PackageName.Empty,
            shortName = ShortClassName.fromClassString(name)
        )
    }
    val className = name.substringAfterLast(packageSeparator)
    return QualifiedName(
        PackageName.fromPackageString(packageName, slashQualified),
        ShortClassName.fromClassString(className, dollarQualified)
    )
}

internal fun QualifiedName.stringifyQualifiedName(dollarQualified: Boolean, slashQualified: Boolean): String {
    return if (packageName.isEmpty) shortName.toString(dollarQualified)
    else buildString {
        append(packageName.toString(slashQualified))
        append(PackageQualifier(slashQualified))
        append(shortName.toString(dollarQualified))
    }
}

internal fun QualifiedName.Companion.fromPackageNameAndClassSegments(packageName: PackageName,
                                                                     segments: List<SimpleClassGenericType>) = QualifiedName(
    packageName,
    ShortClassName.fromComponents(segments.map { it.name })
)