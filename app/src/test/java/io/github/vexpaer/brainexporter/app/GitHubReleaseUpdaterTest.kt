package io.github.vexpaer.brainexporter.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {
    @Test
    fun `semantic version comparison ignores tag prefix and build suffix`() {
        assertTrue(isNewerVersion("0.2.0", "0.1.9"))
        assertTrue(isNewerVersion("v1.0.0", "0.9.9-debug"))
        assertFalse(isNewerVersion("0.2.0", "0.2.0-debug"))
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
    }
}
