package onion.gh0st.iMoodle.inyectores

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import onion.gh0st.iMoodle.data.local.AppDatabase
import onion.gh0st.iMoodle.data.local.LocalDataCleaner
import onion.gh0st.iMoodle.data.local.Migrations
import onion.gh0st.iMoodle.data.local.RoomLocalDataCleaner
import onion.gh0st.iMoodle.data.local.RoomTransactionRunner
import onion.gh0st.iMoodle.data.local.TransactionRunner
import onion.gh0st.iMoodle.data.local.dao.AssignmentDao
import onion.gh0st.iMoodle.data.local.dao.CalendarDao
import onion.gh0st.iMoodle.data.local.dao.CourseDao
import javax.inject.Singleton

/**
 * Módulo de Hilt con las dependencias de la base de datos local
 * (Room).
 *
 * ## Qué es Hilt y qué es un "módulo"
 *
 * Hilt es la librería de inyección de dependencias que usa la app.
 * Su trabajo es construir automáticamente los objetos que las clases
 * necesitan, en lugar de que cada clase los construya por su cuenta.
 *
 * Para que Hilt sepa cómo construir un objeto, hay que
 * "enseñárselo". Eso se hace con **módulos**: clases anotadas con
 * `@Module` que contienen métodos anotados con `@Provides`. Cada
 * método `@Provides` es una "receta" que le dice a Hilt: "cuando
 * alguien te pida un objeto de este tipo, constrúyelo así".
 *
 * Este módulo, en concreto, enseña a Hilt a construir:
 *
 * - La base de datos `AppDatabase`.
 * - Los DAOs (`CourseDao`, `AssignmentDao`, `CalendarDao`).
 * - El `TransactionRunner` (para transacciones).
 * - El `LocalDataCleaner` (para borrar datos).
 *
 * ## Por qué es un `object` y no una clase
 *
 * Porque los métodos `@Provides` no necesitan estado. Al ser
 * `object`, Hilt puede llamarlos como si fueran estáticos, sin
 * instanciar nada.
 *
 * ## Por qué todos los `@Provides` son `@Singleton`
 *
 * Porque la app solo debe tener una instancia de cada uno. Si hubiera
 * varias instancias de la base de datos, cada una tendría su propio
 * "estado" y se producirían incoherencias. Con `@Singleton`, Hilt
 * garantiza que todas las clases que pidan `AppDatabase` reciben la
 * misma instancia.
 *
 * ## Sobre la política de migraciones
 *
 * La base de datos sigue esta política:
 *
 * - **Migraciones reales** desde la v4 en adelante: cada cambio de
 *   esquema tiene su `MIGRATION_N_N+1` registrada en
 *   [Migrations.ALL]. Los datos de los usuarios se preservan.
 * - **Migración destructiva** solo para v1, v2, v3: son versiones de
 *   desarrollo sin usuarios reales, así que se permite que Room
 *   borre y recree la base de datos. Se configura con
 *   `fallbackToDestructiveMigrationFrom(1, 2, 3)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Crea la base de datos local de la app.
     *
     * ## Qué hace
     *
     * 1. Construye la base de datos SQLite con el nombre
     *    `imoodle.db`.
     * 2. Registra todas las migraciones reales.
     * 3. Configura la migración destructiva solo para versiones
     *    antiguas.
     *
     * ## Sobre el parámetro `dropAllTables`
     *
     * La versión moderna de `fallbackToDestructiveMigrationFrom` exige
     * especificar explícitamente si se deben borrar todas las tablas
     * al migrar. Aquí se pasa `true` porque solo se aplica a
     * versiones antiguas (v1-v3) sin datos que preservar.
     *
     * @param context Contexto de aplicación inyectado por Hilt.
     * @return La instancia de `AppDatabase` (singleton).
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "imoodle.db",
        )
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3)
            .build()

    /**
     * Provee el DAO de cursos.
     *
     * @param database Base de datos.
     * @return El DAO listo para consultar y modificar la tabla
     *   `courses`.
     */
    @Provides
    fun provideCourseDao(database: AppDatabase): CourseDao = database.courseDao()

    /**
     * Provee el DAO de tareas.
     *
     * @param database Base de datos.
     * @return El DAO listo para consultar y modificar la tabla
     *   `assignments`.
     */
    @Provides
    fun provideAssignmentDao(database: AppDatabase): AssignmentDao =
        database.assignmentDao()

    /**
     * Provee el DAO de eventos de calendario.
     *
     * @param database Base de datos.
     * @return El DAO listo para consultar y modificar la tabla
     *   `calendar_events`.
     */
    @Provides
    fun provideCalendarDao(database: AppDatabase): CalendarDao = database.calendarDao()

    /**
     * Provee el ejecutor de transacciones.
     *
     * ## Qué es un `TransactionRunner`
     *
     * Es una abstracción que permite ejecutar un bloque de
     * operaciones como una sola transacción atómica. Se usa en los
     * repositorios cuando necesitan hacer "borrar + insertar" sin
     * que la UI vea un estado intermedio.
     *
     * ## Por qué es una interfaz
     *
     * Porque en tests se sustituye por un fake
     * (`InlineTransactionRunner` o `RecordingTransactionRunner`) que
     * no necesita una base de datos real.
     *
     * @param database Base de datos sobre la que se ejecutan las
     *   transacciones.
     * @return La implementación real sobre Room.
     */
    @Provides
    @Singleton
    fun provideTransactionRunner(database: AppDatabase): TransactionRunner =
        RoomTransactionRunner(database)

    /**
     * Provee el limpiador de datos locales.
     *
     * ## Qué es un `LocalDataCleaner`
     *
     * Es una abstracción que permite borrar todos los datos locales
     * (cursos, tareas, eventos) de golpe. Se usa al hacer logout para
     * que el siguiente usuario del dispositivo no vea los datos del
     * anterior.
     *
     * ## Por qué es una interfaz
     *
     * Porque en tests se sustituye por un fake
     * (`FakeLocalDataCleaner`) que solo cuenta las llamadas.
     *
     * @param database Base de datos que se va a limpiar.
     * @return La implementación real sobre Room.
     */
    @Provides
    @Singleton
    fun provideLocalDataCleaner(database: AppDatabase): LocalDataCleaner =
        RoomLocalDataCleaner(database)
}