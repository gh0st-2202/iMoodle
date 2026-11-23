package onion.gh0st.iMoodle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.HiltAndroidApp
import onion.gh0st.iMoodle.core.AppIconManager

/**
 * Punto de entrada del proceso y raíz del grafo de dependencias de
 * Hilt.
 *
 * ## Qué es una `Application`
 *
 * En Android, la `Application` es una clase que representa el
 * proceso completo de la app. Se crea **antes** que cualquier
 * Activity, Service o Receiver, y vive mientras viva el proceso. Es
 * el sitio adecuado para inicializaciones globales.
 *
 * ## Qué hace esta `Application`
 *
 * 1. **Anotación `@HiltAndroidApp`**: le dice a Hilt que genere el
 *    código necesario para la inyección de dependencias. Sin esta
 *    anotación, Hilt no funcionaría.
 * 2. **Punto de entrada del grafo**: todos los objetos inyectables
 *    (ViewModels, repositorios, `TokenStore`, etc.) se resuelven a
 *    partir de esta instancia. Hilt crea un "grafo" de dependencias
 *    que conecta todos los módulos.
 * 3. **Sincronización del icono**: al arrancar, activa el alias del
 *    launcher que corresponde a la paleta guardada (§7.104) y limpia
 *    cualquier cambio pendiente que ya se haya aplicado.
 * 4. **Cambio de icono en segundo plano**: observa el ciclo de vida
 *    de las Activities y aplica el icono pendiente solo cuando no
 *    queda ninguna resumida, para no interrumpir al usuario en Perfil.
 *
 * ## Por qué el cambio de icono no se hace en el Fragment
 *
 * Cambiar el alias activo obliga a Android a reconstruir la caché del
 * launcher y, en algunos fabricantes, cierra el proceso. Si se hiciera
 * justo al elegir el color, la app se cerraría «en la cara» del usuario.
 * Aquí se retrasa con un pequeño margen y se comprueba que realmente
 * se ha ido al escritorio (§7.106).
 *
 * ## Cómo se conecta con el resto
 *
 * - **Módulos**: `DatabaseModule`, `DataModule`, `NetworkModule`,
 *   `ProvidesModule` (todos en `inyectores/`).
 * - **Consumidores**: ViewModels, Fragments, Activities,
 *   BroadcastReceivers (todos anotados con `@AndroidEntryPoint`).
 *
 * Hilt conecta automáticamente los módulos con los consumidores.
 */
@HiltAndroidApp
class IMoodleApp : Application() {

    /** Contador de Activities resumidas; `0` significa «app en segundo plano». */
    private var resumedActivities = 0

    /** Ejecuta el cambio de icono pendiente en el hilo principal. */
    private val applyPendingIcon = Runnable {
        if (resumedActivities == 0) AppIconManager.applyPendingIcon(this)
    }

    /** Handler del hilo principal usado para el retardo del icono. */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Inicializa Hilt, sincroniza el icono y registra el observador de
     * segundo plano.
     */
    override fun onCreate() {
        super.onCreate()
        AppIconManager.syncSavedIcon(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            /**
             * Cancela un cambio pendiente si la app vuelve a primer plano.
             *
             * @param activity Activity que se reanuda.
             */
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                handler.removeCallbacks(applyPendingIcon)
            }

            /**
             * Programa el cambio de icono si ya no queda ninguna Activity
             * resumida.
             *
             * @param activity Activity que se pausa.
             */
            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                if (resumedActivities == 0) {
                    handler.postDelayed(applyPendingIcon, BACKGROUND_DELAY_MS)
                }
            }

            /** No hace falta hacer nada al crear una Activity. */
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            /** No hace falta hacer nada al iniciar una Activity. */
            override fun onActivityStarted(activity: Activity) = Unit

            /** No hace falta hacer nada al detener una Activity. */
            override fun onActivityStopped(activity: Activity) = Unit

            /** No hace falta hacer nada al guardar el estado de una Activity. */
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            /** No hace falta hacer nada al destruir una Activity. */
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private companion object {
        /** Margen para no confundir una transición entre pantallas con un salto al escritorio. */
        const val BACKGROUND_DELAY_MS = 300L
    }
}
