package metautils.types.internal

import metautils.types.jvm.JvmPrimitiveType

internal val jvmPrimitiveStringToType = mapOf(
    JvmPrimitiveType.Byte.classFileName to JvmPrimitiveType.Byte,
    JvmPrimitiveType.Char.classFileName to JvmPrimitiveType.Char,
    JvmPrimitiveType.Double.classFileName to JvmPrimitiveType.Double,
    JvmPrimitiveType.Float.classFileName to JvmPrimitiveType.Float,
    JvmPrimitiveType.Int.classFileName to JvmPrimitiveType.Int,
    JvmPrimitiveType.Long.classFileName to JvmPrimitiveType.Long,
    JvmPrimitiveType.Short.classFileName to JvmPrimitiveType.Short,
    JvmPrimitiveType.Boolean.classFileName to JvmPrimitiveType.Boolean
)

internal val jvmPrimitiveStringToTypeChar = jvmPrimitiveStringToType.mapKeys { it.key[0] }