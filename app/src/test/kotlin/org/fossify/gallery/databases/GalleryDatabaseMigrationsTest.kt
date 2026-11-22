package org.fossify.gallery.databases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryDatabaseMigrationsTest {

    @Test
    fun `migration chain is contiguous from version 4 to current database version`() {
        val dbVersion = GalleryDatabase.DB_VERSION
        val migrations = GalleryDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }

        assertEquals(4, migrations.first().startVersion)
        assertEquals(dbVersion, migrations.last().endVersion)
        assertEquals(dbVersion - 4, migrations.size)

        migrations.forEachIndexed { index, migration ->
            val expectedStart = 4 + index
            assertEquals("Gap or wrong startVersion at step $index", expectedStart, migration.startVersion)
            assertEquals("Non-unit step at version $expectedStart", expectedStart + 1, migration.endVersion)
        }
    }

    @Test
    fun `exported Room schemas exist for versions 25 through current version`() {
        val dbVersion = GalleryDatabase.DB_VERSION
        val schemaDir = listOf(
            File("schemas/org.fossify.gallery.databases.GalleryDatabase"),
            File("app/schemas/org.fossify.gallery.databases.GalleryDatabase"),
        ).firstOrNull { it.isDirectory }

        assertTrue("Expected exported Room schema directory to exist", schemaDir != null)
        for (version in 25..dbVersion) {
            val schemaFile = File(schemaDir!!, "$version.json")
            assertTrue("Missing Room schema snapshot: ${schemaFile.path}", schemaFile.isFile)
            val content = schemaFile.readText()
            assertTrue(
                "Schema $version.json does not declare version $version",
                content.contains("\"version\": $version"),
            )
        }
    }
}
