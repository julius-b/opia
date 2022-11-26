import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun mainDispatcher(): CoroutineDispatcher {
    return Dispatchers.Main
}
