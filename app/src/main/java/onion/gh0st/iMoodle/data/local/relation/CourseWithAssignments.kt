package onion.gh0st.iMoodle.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import onion.gh0st.iMoodle.data.local.entity.AssignmentEntity
import onion.gh0st.iMoodle.data.local.entity.CourseEntity

/**
 * Relación de Room que agrupa un curso con sus tareas.
 *
 * ## Qué es una relación en Room
 *
 * En una base de datos relacional, las tablas se relacionan entre sí
 * mediante claves (por ejemplo, `assignments.courseId` apunta a
 * `courses.id`). Para trabajar con esos datos, a veces se quieren
 * consultar juntos: "dame el curso y todas sus tareas".
 *
 * Room ofrece la anotación `@Relation` para expresar ese tipo de
 * consulta. Al anotar una propiedad con `@Relation`, Room ejecuta
 * dos `SELECT` internos (uno para el curso, otro para sus tareas) y
 * los combina en una sola instancia de esta clase.
 *
 * ## Cómo se resuelve
 *
 * `CourseDao.getCoursesWithAssignments` está anotado con
 * `@Transaction`, lo que garantiza que los dos `SELECT` se ejecutan
 * de forma consistente, sin que una escritura concurrente pueda
 * dejarlos a medias.
 *
 * ## Para qué se usa
 *
 * Para construir el `Course` de dominio con su lista de tareas ya
 * cargada. Así la UI del listado no tiene que hacer N consultas
 * adicionales (una por curso) para saber cuántas tareas tiene cada
 * uno.
 *
 * ## Qué NO incluye
 *
 * - **No incluye las tareas en formato de dominio**: incluye las
 *   entidades de Room (`AssignmentEntity`). La conversión a dominio
 *   la hace el repositorio (`MoodleCourseRepository`) cuando
 *   construye el `Course`.
 * - **No incluye otros datos del curso** (como el nombre del
 *   profesor o la lista de módulos): solo lo que está en la tabla
 *   `courses`.
 *
 * @property course Fila del curso en la tabla `courses`.
 * @property assignments Tareas asociadas al curso, resueltas por
 *   `courseId`. Puede estar vacía si el curso no tiene tareas.
 */
data class CourseWithAssignments(
    @Embedded val course: CourseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "courseId",
    )
    val assignments: List<AssignmentEntity>,
)