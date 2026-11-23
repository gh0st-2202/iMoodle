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

    /** Permiso devuelto por [canAddDiscussion]. */
    var canCreateDiscussion: Boolean = true

    /** Si es no-null, se lanza al invocar [addDiscussion]. */
    var addDiscussionError: Throwable? = null

    /** Si es no-null, se lanza al invocar [addReply]. */
    var addReplyError: Throwable? = null

    /** Última discusión publicada: `(forumId, subject, message)`. */
    var lastDiscussion: Triple<Int, String, String>? = null
        private set

    /** Última respuesta publicada: `(parentPostId, subject, message)`. */
    var lastReply: Triple<Int, String, String>? = null
        private set

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

    /**
     * Devuelve [canCreateDiscussion].
     *
     * @param forumId ID del foro (ignorado por el fake).
     * @return El permiso configurado.
     */
    override suspend fun canAddDiscussion(forumId: Int): Boolean = canCreateDiscussion

    /**
     * Registra la discusión y devuelve un ID fijo.
     *
     * @param forumId ID del foro.
     * @param subject Asunto.
     * @param message Mensaje.
     * @return ID ficticio de la discusión.
     * @throws Throwable si [addDiscussionError] no es `null`.
     */
    override suspend fun addDiscussion(forumId: Int, subject: String, message: String): Int {
        addDiscussionError?.let { throw it }
        lastDiscussion = Triple(forumId, subject, message)
        return 1
    }

    /**
     * Registra la respuesta y devuelve un ID fijo.
     *
     * @param parentPostId ID del post padre.
     * @param subject Asunto.
     * @param message Mensaje.
     * @return ID ficticio del post.
     * @throws Throwable si [addReplyError] no es `null`.
     */
    override suspend fun addReply(parentPostId: Int, subject: String, message: String): Int {
        addReplyError?.let { throw it }
        lastReply = Triple(parentPostId, subject, message)
        return 2
    }
}
