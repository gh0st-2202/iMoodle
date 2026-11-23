package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.repository.pasen.PasenGradeRepository

/**
 * Implementación en memoria de [PasenGradeRepository] para tests.
 */
class FakePasenGradeRepository : PasenGradeRepository {

    /** Convocatorias a devolver. */
    var grades: List<PasenConvocatoria> = emptyList()

    /** Si es no-null, se lanza al pedir las calificaciones. */
    var gradeError: Throwable? = null

    /** Último valor de `forceRefresh` pedido. */
    var lastForceRefresh: Boolean? = null
        private set

    override suspend fun getGrades(forceRefresh: Boolean): List<PasenConvocatoria> {
        lastForceRefresh = forceRefresh
        gradeError?.let { throw it }
        return grades
    }
}
