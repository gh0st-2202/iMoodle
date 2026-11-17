package onion.gh0st.iMoodle.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import javax.inject.Inject

/**
 * Receiver que reprograma las alarmas cuando el sistema invalida su
 * programación.
 *
 * ## Por qué existe
 *
 * `AlarmManager` pierde todas las alarmas cuando el móvil se apaga o
 * se reinicia. Es una decisión de diseño de Android: las alarmas se
 * consideran "volátiles". Si la app no hiciera nada, el usuario
 * dejaría de recibir recordatorios después de reiniciar el móvil,
 * sin previo aviso.
 *
 * Para evitarlo, Android envía un `broadcast`
 * (`ACTION_BOOT_COMPLETED`) a las apps que lo solicitan, avisándolas
 * de que el sistema ha terminado de arrancar. Este receiver lo
 * escucha y reprograma todas las alarmas que había guardadas en
 * `AlarmStorage`.
 *
 * ## Cambios de hora y de zona horaria
 *
 * Las alarmas se programan como instantes absolutos (`RTC_WAKEUP`)
 * calculados a partir de la hora local. Si el usuario cambia de zona
 * horaria o ajusta la hora a mano, ese instante ya no corresponde a
 * las 08:00 locales. Por eso también se atienden
 * `ACTION_TIMEZONE_CHANGED` y `ACTION_TIME_CHANGED`: en ambos casos se
 * recalculan las alarmas desde los eventos guardados.
 *
 * ## Cómo funciona
 *
 * 1. Comprueba que el `Intent` recibido es uno de los que interesan
 *    (arranque, cambio de hora o de zona). Por seguridad: un receiver
 *    puede recibir otros `Intent`, y no queremos reaccionar a ellos.
 * 2. Llama a `goAsync()` para indicar a Android que el receiver va a
 *    hacer trabajo asíncrono (cargar alarmas de disco,
 *    reprogramarlas). Sin esto, Android podría matar el proceso antes
 *    de que terminara.
 * 3. En una corrutina de `Dispatchers.IO`:
 *    - Carga las alarmas guardadas.
 *    - Si hay alguna, pide al scheduler que las reprograme.
 *    - Marca el `PendingResult` como terminado con `finish()`.
 *
 * ## Por qué `goAsync()` es necesario
 *
 * El método `onReceive` de un `BroadcastReceiver` se ejecuta en el
 * hilo principal y **debe terminar en menos de 10 segundos**. Si
 * tarda más, el sistema mata el proceso y muestra un ANR
 * ("Application Not Responding"). La reprogramación de alarmas implica
 * acceso a disco, lo que podría tardar. Con `goAsync()`, se le dice a
 * Android que el trabajo continúa en background y que no mate el
 * proceso hasta que se llame a `finish()`.
 *
 * ## Anotación `@AndroidEntryPoint`
 *
 * Es necesaria para que Hilt pueda inyectar `scheduler` en este
 * receiver. Sin ella, `scheduler` sería `null` y el receiver
 * fallaría.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    /**
     * Scheduler inyectado por Hilt. Se usa para reprogramar las
     * alarmas.
     */
    @Inject lateinit var scheduler: NotificationScheduler

    /**
     * Punto de entrada del receiver. Se llama cuando se recibe un
     * `broadcast`.
     *
     * ## Qué hace
     *
     * 1. Comprueba que la acción es una de [RESCHEDULE_ACTIONS]. Si
     *    no, no hace nada.
     * 2. Pide a Android permiso para hacer trabajo asíncrono.
     * 3. Lanza una corrutina que:
     *    - Carga las alarmas guardadas.
     *    - Si hay alguna, las reprograma.
     *    - Marca el `PendingResult` como terminado.
     *
     * @param context Contexto del receiver.
     * @param intent `Intent` recibido.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storedEvents = AlarmStorage.load(context)
                if (storedEvents.isNotEmpty()) {
                    scheduler.schedule(storedEvents)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        /**
         * Acciones que invalidan las alarmas programadas.
         *
         * `TIME_SET` es el valor real de `ACTION_TIME_CHANGED` (así se
         * llama la constante en el SDK).
         */
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
