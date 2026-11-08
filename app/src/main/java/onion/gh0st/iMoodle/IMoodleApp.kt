package onion.gh0st.iMoodle

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

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
 *
 * ## Por qué no tiene más código
 *
 * Porque no hace falta. Hilt se encarga de todo lo que necesita
 * saber sobre la `Application`. Si en el futuro hay que inicializar
 * algo global (por ejemplo, un logger, un crash reporter, un
 * cliente de analytics), se añade aquí.
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
class IMoodleApp : Application()