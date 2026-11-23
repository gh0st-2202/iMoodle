package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.repository.completion.CompletionRepository

/**
 * Implementación en memoria de [CompletionRepository] para tests.
 */
class FakeCompletionRepository : CompletionRepository {

    /** Resultado que devolverá [markModuleViewed]. */
    var viewedResult: Result<Unit> = Result.success(Unit)

    /** Resultado que devolverá [setModuleCompleted]. */
    var completionResult: Result<Unit> = Result.success(Unit)

    /** Último módulo marcado como visto. */
    var lastViewedModule: CourseModule? = null
        private set

    /** Último cambio de finalización: `(cmId, completed)`. */
    var lastCompletionChange: Pair<Int, Boolean>? = null
        private set

    override suspend fun markModuleViewed(module: CourseModule): Result<Unit> {
        lastViewedModule = module
        return viewedResult
    }

    override suspend fun setModuleCompleted(cmId: Int, completed: Boolean): Result<Unit> {
        lastCompletionChange = cmId to completed
        return completionResult
    }
}
