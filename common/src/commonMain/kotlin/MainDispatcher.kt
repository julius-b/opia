import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Desktop has no Main dispatcher, need Swing lib
// Android doesn't allow dispatch (UI operation) from non-Main (not Default)
expect fun mainDispatcher(): CoroutineDispatcher
