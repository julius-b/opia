import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing

actual fun mainDispatcher(): CoroutineDispatcher {
    return Dispatchers.Swing
}
