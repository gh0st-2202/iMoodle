package onion.gh0st.iMoodle.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.usecase.update.CheckForUpdates
import onion.gh0st.iMoodle.domain.usecase.update.DownloadUpdate
import onion.gh0st.iMoodle.domain.usecase.update.InstallApk
import javax.inject.Inject

/**
 * ViewModel que gestiona el ciclo completo de una actualización:
 * comprobar, avisar al usuario, descargar e instalar.
 *
 * ## Qué cambia respecto a la versión anterior
 *
 * - **El aviso sobrevive a las rotaciones.** [updateAvailable] conserva la
 *   actualización hasta que el usuario decide ([dismissUpdate] o
 *   [startDownload]). Antes, el estado de "ya mostrado" vivía en la
 *   Activity y se perdía al rotar, de modo que el diálogo reaparecía una y
 *   otra vez.
 * - **Una sola consulta a GitHub por proceso.** [check] se puede llamar
 *   todas las veces que haga falta (por ejemplo, desde `onCreate`) y solo
 *   consulta la primera. La API de GitHub limita las peticiones sin
 *   autenticar a 60 por hora.
 * - **La descarga no revienta la app.** Corre en `viewModelScope` y
 *   cualquier fallo (sin red, GitHub caído…) se convierte en un
 *   [UpdateEvent.DownloadFailed] en lugar de una excepción sin capturar.
 *
 * @param checkForUpdates Caso de uso que busca una versión más nueva.
 * @param downloadUpdate Caso de uso que descarga el instalable.
 * @param installApk Caso de uso que abre el instalador del sistema.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdates: CheckForUpdates,
    private val downloadUpdate: DownloadUpdate,
    private val installApk: InstallApk,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)

    /**
     * Actualización pendiente de que el usuario decida, o `null`.
     *
     * Permanece asignada hasta que se llama a [dismissUpdate] o a
     * [startDownload], de modo que una Activity recreada puede volver a
     * mostrar el diálogo.
     */
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso del proceso de descarga (aviso, error, instalador listo). */
    val events: Flow<UpdateEvent> = _events.receiveAsFlow()

    private var hasChecked = false

    /**
     * Busca una versión más nueva. Solo consulta la primera vez que se
     * llama; las siguientes llamadas no hacen nada.
     */
    fun check() {
        if (hasChecked) return
        hasChecked = true

        viewModelScope.launch {
            checkForUpdates()
                .onSuccess { update -> if (update != null) _updateAvailable.value = update }
                .onFailure { exception ->
                    Logging.logError(TAG, "checkForUpdates", exception)
                }
        }
    }

    /** Descarta el aviso actual (el usuario ha pulsado "Más tarde"). */
    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    /**
     * Descarga la actualización indicada y, si termina bien, abre el
     * instalador del sistema.
     *
     * Retira el aviso, emite [UpdateEvent.DownloadStarted] y, al
     * terminar, lanza [InstallApk]. Si la descarga falla emite
     * [UpdateEvent.DownloadFailed]; si el sistema no puede abrir el
     * instalador, [UpdateEvent.InstallFailed].
     *
     * @param update Actualización que el usuario ha aceptado.
     */
    fun startDownload(update: AppUpdate) {
        _updateAvailable.value = null

        viewModelScope.launch {
            _events.send(UpdateEvent.DownloadStarted)
            downloadUpdate(update)
                .onSuccess { apkFile ->
                    if (!installApk(apkFile)) _events.send(UpdateEvent.InstallFailed)
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "downloadUpdate", exception)
                    _events.send(UpdateEvent.DownloadFailed)
                }
        }
    }

    private companion object {
        const val TAG = "UpdateViewModel"
    }
}

/** Eventos de un solo uso emitidos por [UpdateViewModel] durante la descarga. */
sealed interface UpdateEvent {

    /** La descarga ha empezado (la UI muestra un aviso breve). */
    data object DownloadStarted : UpdateEvent

    /**
     * El sistema no pudo abrir el instalador de paquetes (o no hay
     * ninguno disponible).
     *
     * La descarga sí terminó bien, así que la UI solo debe explicar
     * que la instalación no se pudo lanzar.
     */
    data object InstallFailed : UpdateEvent

    /** La descarga ha fallado (la UI muestra un mensaje de error). */
    data object DownloadFailed : UpdateEvent
}
