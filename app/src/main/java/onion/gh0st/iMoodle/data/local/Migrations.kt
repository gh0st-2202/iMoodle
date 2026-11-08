package onion.gh0st.iMoodle.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de esquema de [AppDatabase].
 *
 * ## Qué es una migración
 *
 * Una migración es el conjunto de instrucciones SQL que transforman
 * una base de datos de una versión antigua a una nueva. Cada vez que
 * el esquema cambia (se añade una columna, se renombra una tabla, se
 * modifica un tipo), hay que escribir una migración que explique a
 * Room cómo transformar los datos existentes para que encajen en el
 * nuevo esquema.
 *
 * Sin migraciones, Room no sabe qué hacer cuando encuentra una base
 * de datos antigua y, por defecto, la borra. Eso significa que el
 * usuario pierde todos sus datos cacheados: cursos, tareas, agenda…
 * (y tiene que volver a sincronizarlos todos, con el coste de red y
 * de tiempo que eso supone).
 *
 * ## Histórico de versiones
 *
 * - **v1 → v2**: sin documentar (cambios previos a la política
 *   actual de migraciones).
 * - **v2 → v3**: las URLs de imagen de los cursos dejan de guardarse
 *   con el token incrustado. Ahora se guardan sin token y se le
 *   añade al vuelo cuando se lee. Motivo: no persistir tokens en
 *   claro en la base de datos.
 * - **v3 → v4**: eliminación del campo `teacherName` de
 *   `CourseEntity`. Motivo: el profesor del curso no se muestra en
 *   ninguna pantalla, así que no merecía la pena guardarlo.
 * - **v4 → v5**: migración no-op (no cambia nada). Sirve para
 *   establecer el patrón de migraciones reales. A partir de aquí,
 *   todas las migraciones son reales.
 *
 * ## Política de migraciones
 *
 * - **v1, v2, v3**: migración **destructiva**. Son versiones de
 *   desarrollo, sin usuarios reales, así que se permite que Room
 *   borre y recree la base de datos. Se configura en
 *   `DatabaseModule` con `fallbackToDestructiveMigrationFrom(1, 2, 3)`.
 * - **v4 en adelante**: migración **real**. Cada versión nueva debe
 *   tener su `MIGRATION_N_N+1` registrada en [ALL]. Nada de
 *   `fallbackToDestructiveMigration` genérico.
 *
 * ## Cómo añadir una migración nueva
 *
 * 1. **Subir la versión**: cambiar `version = 5` a `version = 6` en
 *    `AppDatabase`.
 * 2. **Escribir la migración**: añadir un
 *    `val MIGRATION_5_6 = object : Migration(5, 6) { ... }` con el
 *    SQL necesario.
 * 3. **Registrarla**: añadirla al array [ALL].
 * 4. **Añadir un test**: escribir un test instrumentado con
 *    `MigrationTestHelper` que verifique que la migración funciona.
 * 5. **Compilar**: Room generará el JSON del nuevo esquema en
 *    `app/schemas/`.
 */
internal object Migrations {

    /**
     * Migración **v4 → v5**: no-op.
     *
     * No cambia nada en el esquema. Existe para establecer el patrón
     * de migraciones reales: a partir de aquí, cada vez que se suba
     * la versión, habrá que escribir una migración con el SQL real.
     *
     * ## Por qué una migración no-op es útil
     *
     * Room exige que cada transición de versión esté cubierta por una
     * migración registrada. Si subimos la versión pero no
     * registramos nada, Room no sabe qué hacer. Una migración no-op
     * le dice "no hay cambios en el esquema, adelante".
     *
     * ## Cuándo se cambiará
     *
     * En cuanto se añada, quite o modifique cualquier campo de
     * cualquier entidad. En ese momento, esta migración se
     * reemplazará por una real (con `ALTER TABLE` o similar) o se
     * añadirá una nueva (`MIGRATION_5_6`).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
        }
    }

    /**
     * Lista de todas las migraciones reales.
     *
     * Se registra en `DatabaseModule` con
     * `.addMigrations(*Migrations.ALL)`. Room irá aplicando las
     * migraciones una a una hasta llegar a la versión actual.
     *
     * ## Cómo se aplica
     *
     * Si un usuario tiene la versión 4 y la app está en la 5, Room
     * busca una migración de 4 a 5, la aplica y listo. Si tuviera la
     * 3 y la app está en la 5, buscaría 3→4 y 4→5, y las aplicaría
     * en orden.
     */
    val ALL = arrayOf(MIGRATION_4_5)
}