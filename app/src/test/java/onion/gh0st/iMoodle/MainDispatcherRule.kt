package onion.gh0st.iMoodle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Regla de JUnit que reemplaza `Dispatchers.Main` por un
 * [TestDispatcher] durante cada test.
 *
 * ## Qué problema resuelve
 *
 * En tests unitarios puros (JVM, sin emulador), `Dispatchers.Main`
 * no está inicializado. Cualquier `viewModelScope.launch` lanzaría
 * `IllegalStateException: Module with the Main dispatcher had failed
 * to initialize`.
 *
 * Esta regla se ejecuta antes y después de cada test para sustituir
 * `Dispatchers.Main` por un dispatcher de test y luego restaurarlo.
 * Así los ViewModels pueden usar `viewModelScope` normalmente.
 *
 * ## Sobre el dispatcher por defecto
 *
 * Se usa `UnconfinedTestDispatcher`, que ejecuta las corrutinas de
 * forma "eager" (inmediatamente, sin suspender). Esto simplifica
 * mucho los tests de carga inicial: no hay que llamar a
 * `advanceUntilIdle()` para que se ejecuten las corrutinas del
 * `init`.
 *
 * ## Uso
 *
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * @param testDispatcher Dispatcher a usar como `Main`. Por defecto,
 *   `UnconfinedTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    /**
     * Antes de cada test: sustituye `Dispatchers.Main` por el
     * dispatcher de test.
     *
     * @param description Descripción del test en ejecución.
     */
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    /**
     * Después de cada test: restaura `Dispatchers.Main` al original.
     *
     * @param description Descripción del test terminado.
     */
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}