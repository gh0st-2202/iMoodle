package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository

/**
 * Implementación en memoria de [ForumRepository] para tests.
 *
 * Devuelve los datos configurados, sin parsing de JSON ni red.
 */
class FakeForumRepository : ForumRepository {

    /** Discusiones a devolver por [getDiscussions]. */
    var discussions: List<ForumDiscussion> = emptyList()

    /** Posts a devolver por [getPosts]. */
    var posts: List<ForumPost> = emptyList()

    /** Si es no-null, se lanza al invocar [getDiscussions]. */
    var discussionsError: Throwable? = null

    /** Si es no-null, se lanza al invocar [getPosts]. */
    var postsError: Throwable? = null

    /**
     * Devuelve [discussions] o lanza [discussionsError].
     *
     * @param forumId ID del foro (ignorado por el fake).
     * @return Lista configurada.
     * @throws Throwable si [discussionsError] no es `null`.
     */
    override suspend fun getDiscussions(forumId: Int): List<ForumDiscussion> {
        discussionsError?.let { throw it }
        return discussions
    }

    /**
     * Devuelve [posts] o lanza [postsError].
     *
     * @param discussionId ID de la discusión (ignorado por el fake).
     * @return Lista configurada.
     * @throws Throwable si [postsError] no es `null`.
     */
    override suspend fun getPosts(discussionId: Int): List<ForumPost> {
        postsError?.let { throw it }
        return posts
    }
}