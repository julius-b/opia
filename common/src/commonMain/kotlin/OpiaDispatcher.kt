import kotlinx.coroutines.CoroutineDispatcher

// Desktop has no Main dispatcher, need Swing lib
// Android doesn't allow dispatch (UI operation) from non-Main (not Default)
interface OpiaDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
