package onion.gh0st.iMoodle.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [VersionComparator].
 *
 * Fijan las reglas con las que la app decide si una publicación de GitHub
 * es una actualización: comparación numérica por componentes, prefijo
 * `v`, pre-versiones y versiones ilegibles.
 */
class VersionComparatorTest {

    @Test
    fun `higher minor version is newer`() {
        assertTrue(VersionComparator.isNewer(current = "2.4", candidate = "2.5"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = "2.4"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = "2.3"))
    }

    @Test
    fun `components are compared as numbers and not as text`() {
        assertTrue(VersionComparator.isNewer(current = "2.9", candidate = "2.10"))
        assertFalse(VersionComparator.isNewer(current = "2.10", candidate = "2.9"))
    }

    @Test
    fun `missing components count as zero`() {
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = "2.4.0"))
        assertTrue(VersionComparator.isNewer(current = "2.4", candidate = "2.4.1"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertTrue(VersionComparator.isNewer(current = "2.4", candidate = "v2.5"))
        assertFalse(VersionComparator.isNewer(current = "v2.4", candidate = "2.4"))
    }

    @Test
    fun `a pre-release is older than its final version`() {
        assertTrue(VersionComparator.isNewer(current = "2.5-beta", candidate = "2.5"))
        assertFalse(VersionComparator.isNewer(current = "2.5", candidate = "2.5-beta"))
    }

    @Test
    fun `a newer pre-release is newer than an older final version`() {
        assertTrue(VersionComparator.isNewer(current = "2.4", candidate = "2.5-beta"))
    }

    @Test
    fun `build metadata is ignored`() {
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = "2.4+abc123"))
    }

    @Test
    fun `unreadable versions never count as an update`() {
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = "latest"))
        assertFalse(VersionComparator.isNewer(current = "2.4", candidate = ""))
        assertFalse(VersionComparator.isNewer(current = "dev", candidate = "2.5"))
    }
}
