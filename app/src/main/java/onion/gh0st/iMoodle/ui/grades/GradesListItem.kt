package onion.gh0st.iMoodle.ui.grades

import onion.gh0st.iMoodle.domain.model.grade.GradeItem

/**
 * Item de la lista de notas.
 *
 * ## Qué tipos hay
 *
 * - **SectionHeader**: cabecera de una sección (Unidad 1, Unidad 2…)
 *   en verde, igual que en el detalle del curso.
 * - **Grade**: una actividad calificable con su nota.
 */
sealed interface GradesListItem {
    data class SectionHeader(val title: String) : GradesListItem
    data class Grade(val item: GradeItem) : GradesListItem
}
