package onion.gh0st.iMoodle.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import onion.gh0st.iMoodle.data.local.dao.AssignmentDao
import onion.gh0st.iMoodle.data.local.dao.CalendarDao
import onion.gh0st.iMoodle.data.local.dao.CourseDao
import onion.gh0st.iMoodle.data.local.entity.AssignmentEntity
import onion.gh0st.iMoodle.data.local.entity.CalendarEventEntity
import onion.gh0st.iMoodle.data.local.entity.CourseEntity

/**
 * Base de datos local de la app.
 *
 * ## Qué es Room
 *
 * Room es la capa de abstracción que Android ofrece sobre SQLite. En
 * lugar de escribir SQL a mano, se declaran entidades (clases que
 * representan tablas) y DAOs (interfaces con los métodos para
 * consultar y modificar esas tablas). Room genera el código SQL por
 * detrás.
 *
 * ## Qué guarda esta base de datos
 *
 * La app guarda en local todo lo que el usuario ya ha visto de
 * Moodle, para poder abrirlo al instante sin conexión y para no
 * martillear los servidores con peticiones repetidas. Concretamente:
 *
 * - **Cursos** (`courses`): la lista de asignaturas en las que está
 *   matriculado el usuario, con su progreso y su portada.
 * - **Tareas** (`assignments`): las tareas de cada curso, con su
 *   fecha de entrega y su nota máxima.
 * - **Eventos de calendario** (`calendar_events`): la agenda de
 *   Moodle (entregas, exámenes, eventos del curso) de los próximos
 *   30 días.
 *
 * No se guarda nada más: ni credenciales, ni tokens, ni horarios de
 * Pasen, ni datos de perfil. Cada cosa tiene su propio almacén.
 *
 * ## Sobre la versión
 *
 * Room exige que la base de datos tenga un número de versión. Cada
 * vez que se cambia el esquema (se añade, se quita o se modifica una
 * columna), hay que subir la versión y proporcionar una "migración"
 * que transforme los datos antiguos al nuevo esquema. Si no se hace,
 * Room borra la base de datos y la recrea vacía, perdiendo los datos
 * del usuario.
 *
 * En este proyecto, la versión actual es **5**. El histórico completo
 * y la política de migraciones están documentados en [Migrations].
 *
 * ## Sobre `exportSchema`
 *
 * `exportSchema = true` le dice a Room que, además de generar el
 * código Java/Kotlin, exporte un fichero JSON con el esquema de cada
 * versión. Ese fichero se guarda en `app/schemas/` y se versiona en
 * git. Sirve para tres cosas:
 *
 * 1. **Tests de migraciones**: los tests instrumentados pueden usar
 *    el JSON para crear la base de datos en la versión antigua y
 *    aplicar la migración.
 * 2. **Historial**: cualquiera puede ver cómo ha evolucionado el
 *    esquema.
 * 3. **Detección de cambios accidentales**: si alguien modifica una
 *    entidad sin querer, el JSON cambia y el revisor lo ve.
 */
@Database(
    entities = [
        CourseEntity::class,
        AssignmentEntity::class,
        CalendarEventEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO de cursos.
     *
     * @return El DAO con las operaciones sobre la tabla `courses`.
     */
    abstract fun courseDao(): CourseDao

    /**
     * DAO de tareas.
     *
     * @return El DAO con las operaciones sobre la tabla `assignments`.
     */
    abstract fun assignmentDao(): AssignmentDao

    /**
     * DAO de eventos de calendario.
     *
     * @return El DAO con las operaciones sobre la tabla
     *   `calendar_events`.
     */
    abstract fun calendarDao(): CalendarDao
}