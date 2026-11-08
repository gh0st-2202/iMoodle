package onion.gh0st.iMoodle.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room que representa un curso.
 *
 * ## Qué es una entidad
 *
 * En Room, una entidad es una clase que representa una tabla de la
 * base de datos. Cada propiedad de la clase es una columna de la
 * tabla, y cada instancia es una fila.
 *
 * Esta entidad, en concreto, representa un curso de Moodle (una
 * asignatura en la que el usuario está matriculado).
 *
 * ## Sobre el campo `userId`
 *
 * Cada fila está asociada a un `userId`. Esto permite que varios
 * usuarios compartan el mismo dispositivo sin mezclar sus cursos: el
 * `Flow` que lee la pantalla de listado filtra siempre por el
 * `userId` del usuario logueado.
 *
 * ## Sobre la URL de la imagen
 *
 * `imageUrl` se guarda **sin** el token de Moodle. El motivo es no
 * persistir el token en claro en la base de datos: si el fichero de
 * la base de datos se filtrara, el token quedaría expuesto. El token
 * se añade al vuelo cuando se construye el `Course` de dominio, en
 * el `map` de `MoodleCourseRepository.getCoursesFlow`.
 *
 * ## Sobre las unidades de tiempo
 *
 * Los timestamps (`startDate`, `endDate`) están en **segundos**
 * (epoch). Moodle los devuelve así, y la app los mantiene así por
 * consistencia. La UI multiplica por 1000 al formatear.
 *
 * @property id ID del curso en Moodle. Es la clave primaria: dos
 *   cursos no pueden tener el mismo ID.
 * @property fullname Nombre completo del curso (por ejemplo,
 *   `"Acceso a datos - 2º DAM"`).
 * @property shortname Nombre corto del curso (por ejemplo,
 *   `"ACDAT"`).
 * @property summary Resumen del curso, con formato HTML tal cual lo
 *   envía Moodle.
 * @property progress Progreso del usuario en el curso (0-100), o
 *   `null` si Moodle no lo informa.
 * @property startDate Fecha de inicio del curso en **segundos**, o
 *   `null` si no está definida.
 * @property endDate Fecha de fin del curso en **segundos**, o
 *   `null` si no está definida.
 * @property imageUrl URL de la imagen de portada del curso, o `null`
 *   si no tiene. Se guarda **sin token**.
 * @property userId ID del usuario propietario de la fila. Permite
 *   separar los cursos de distintos usuarios del mismo dispositivo.
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: Int,
    val fullname: String,
    val shortname: String,
    val summary: String,
    val progress: Int?,
    val startDate: Long?,
    val endDate: Long?,
    val imageUrl: String?,
    val userId: Int,
)