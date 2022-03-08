package com.mell0r.music.library.extending.plugin

import com.h0tk3y.player.MusicApp
import com.h0tk3y.player.MusicLibrary
import com.h0tk3y.player.MusicLibraryContributorPlugin
import java.io.InputStream
import java.io.OutputStream

class MusicLibraryContributor(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
    override val preferredOrder = 1
    private val consoleContributor by lazy {
        musicAppInstance.getPlugins(ConsoleContributor::class.java).single()
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override fun contribute(current: MusicLibrary): MusicLibrary {
        if (!consoleContributor.needToContributeToLibrary)
            return current

        current.playlists.remove(consoleContributor.playlistToDelete)

        current.playlists += consoleContributor.formingPlaylist ?: return current
        return current
    }
}