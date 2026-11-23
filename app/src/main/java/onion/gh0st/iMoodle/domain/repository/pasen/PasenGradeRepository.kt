package onion.gh0st.iMoodle.domain.repository.pasen

import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria

/**
 * Contrato de acceso a las calificaciones de iPasen.
 *
 * ## Qué ofrece
 *
 * Las convocatorias de evaluación del alumno, cada una con las
 * asignaturas y sus notas.
 *
 * ## Por qué no hay caché
 *
 * Las notas cambian cuando el centro las publica. Cachearlas mostraría
 * una nota antigua justo en el momento en que el alumno más quiere
 * verla actualizada. La consulta es ligera y se hace cada vez que se
 * abre la pantalla.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `PasenGradeRepository` (API de iPasen).
 * - **Tests**: `FakePasenGradeRepository`.
 *
 * ## Quién la consume
 *
 * - `GetPasenGrades`.
 * - `PasenGradesViewModel`.
 */
interface PasenGradeRepository {

    /**
     * Devuelve las convocatorias de evaluación del alumno autenticado.
     *
     * @param forceRefresh Si `true`, ignora la caché y va a iPasen.
     * @return Lista de convocatorias.
     */
    suspend fun getGrades(forceRefresh: Boolean = false): List<PasenConvocatoria>
}
