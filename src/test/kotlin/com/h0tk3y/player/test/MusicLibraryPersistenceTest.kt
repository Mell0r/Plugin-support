package com.h0tk3y.player.test

import com.h0tk3y.player.*
import org.junit.Test
import kotlin.test.assertEquals

internal class MusicLibraryPersistenceTest {
    private val staticContributorPath =
        MusicPluginPath(listOf(StaticPlaylistsLibraryContributor::class.java.canonicalName), listOf())
    private val libraryPersistencePath =
        MusicPluginPath(listOf(MusicLibraryPersistencePlugin::class.java.canonicalName), listOf())

    @Test
    fun musicLibraryPersistenceTest() {
        val app = TestableMusicApp(listOf(staticContributorPath, libraryPersistencePath))
        app.wipePersistedPluginData()
        app.init()
        val library = app.musicLibrary
        app.close()

        val newApp = TestableMusicApp(listOf(staticContributorPath, libraryPersistencePath))
        newApp.init()
        assertEquals(library.playlists.size, newApp.musicLibrary.playlists.size)
        for (i in library.playlists.indices) {
            assertEquals(library.playlists[i].name, newApp.musicLibrary.playlists[i].name)
            assertEquals(library.playlists[i].tracks.size, newApp.musicLibrary.playlists[i].tracks.size)
            for (j in library.playlists[i].tracks.indices)
                assertEquals(library.playlists[i].tracks[j].fullDataString, newApp.musicLibrary.playlists[i].tracks[j].fullDataString)
        }
    }
}