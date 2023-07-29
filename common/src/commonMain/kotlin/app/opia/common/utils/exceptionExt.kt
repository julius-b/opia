package app.opia.common.utils

import kotlin.reflect.KClass

// source: https://youtrack.jetbrains.com/issue/KT-7128 "Multi catch block"
inline fun <R, reified T : Throwable> Result<R>.catching(
    transform: (exception: T) -> R
) = recoverCatching { exception ->
    if (exception is T) transform(exception) else throw exception
}

inline fun <R> Result<R>.catching(
    vararg exceptionClasses: KClass<out Throwable>,
    transform: (exception: Throwable) -> R
) = recoverCatching { exception ->
    if (exceptionClasses.any { it.isInstance(exception) }) {
        transform(exception)
    } else throw exception
}
