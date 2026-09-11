package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.trie.TrieEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is the integration point for a new backend, so its lookup rules are
 * worth pinning down.
 */
class EnginesRegistryTest {

    @Test fun `at least one engine is registered`() {
        assertTrue(Engines.factories.isNotEmpty())
    }

    @Test fun `the trie engine is the default`() {
        assertEquals(TrieEngine.ID, Engines.default.descriptor.id)
    }

    @Test fun `engine ids are unique`() {
        val ids = Engines.factories.map { it.descriptor.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `byId finds a registered engine`() {
        assertEquals(TrieEngine.ID, Engines.byId(TrieEngine.ID).descriptor.id)
    }

    @Test fun `byId falls back to the default for unknown or null ids`() {
        assertEquals(Engines.default.descriptor.id, Engines.byId("no-such-engine").descriptor.id)
        assertEquals(Engines.default.descriptor.id, Engines.byId(null).descriptor.id)
    }

    @Test fun `every registered engine has a usable descriptor`() {
        Engines.factories.forEach { factory ->
            val d = factory.descriptor
            assertTrue("id must not be blank", d.id.isNotBlank())
            assertTrue("displayName must not be blank", d.displayName.isNotBlank())
            assertTrue("version must not be blank", d.version.isNotBlank())
            assertTrue("must declare a language", d.supportedLanguages.isNotEmpty())
            assertNotNull(d)
        }
    }
}
