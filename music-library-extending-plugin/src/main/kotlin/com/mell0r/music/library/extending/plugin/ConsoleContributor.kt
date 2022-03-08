package com.mell0r.music.library.extending.plugin

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ConsoleContributor(override val musicAppInstance: MusicApp) : ConsoleHandlerPlugin {
    override val helpSectionName = "Custom playlists extension"

    override val preferredOrder = 1

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override fun printHelp() = println(
        """
            newPlaylist: creates new empty forming playlist
            + 'trackFile' 'metadata'(property space value space property space value etc.): adds new track to current forming playlist
            endPlaylist: finishes playlist forming and adds current forming playlist to the library
            formingPlaylistStatus: prints the current status of forming playlist
            deletePlaylist: deletes existing playlist from library
        """.trimIndent()
    )

    var needToContributeToLibrary: Boolean = false
    var formingPlaylist: Playlist? = null
    var playlistToDelete: Playlist? = null

    private fun checkNoForming(): Boolean {
        if (formingPlaylist != null) {
            println("Playlist '${ formingPlaylist?.name }' is now forming, tracks currently added:")
            formingPlaylist?.tracks?.forEach {
                println("   ${it.fullDataString}")
            }
            return false
        }
        return true
    }

    override fun contribute(current: List<String>?): List<String>? {
        if (current == null)
            return null

        val playlists = musicAppInstance.musicLibrary.playlists

        val handledValue = when (current[0]) {
            "newPlaylist" -> run {
                if (!checkNoForming())
                    return@run false

                if (current.size < 2) {
                    println("Playlist name is empty.")
                    return@run false
                }
                val newPlaylistName = current[1]
                if (playlists.find { it.name == newPlaylistName } != null) {
                    println("Playlist with given name is already exists.")
                    return@run false
                }

                formingPlaylist = Playlist(newPlaylistName, mutableListOf())

                true
            }

            "+" -> run {
                if (formingPlaylist == null) {
                    println("Nothing is forming now. Please, type 'addPlaylist' first.")
                    return@run false
                }
                if (current.size == 1) {
                    println("Song list is empty.")
                    return@run false
                }
                val trackFile = File(current[1])
                if (!trackFile.exists()) {
                    println("File $trackFile is not exists.")
                    return@run false
                }

                val metadata = current.drop(2).windowed(2, 2).associate { it[0] to it[1] }
                formingPlaylist?.tracks?.add(Track(metadata, trackFile))

                true
            }

            "endPlaylist" -> {
                needToContributeToLibrary = true
                musicAppInstance.contributeToMusicLibrary()
                formingPlaylist = null
                true
            }

            "formingPlaylistStatus" -> {
                if (checkNoForming())
                    println("Nothing is forming.")

                true
            }

            "deletePlaylist" -> run {
                if (!checkNoForming())
                    return@run false

                if (current.size < 2) {
                    println("Playlist name is empty.")
                    return@run false
                }

                val newPlaylistName = current[1]
                playlistToDelete = playlists.find { it.name == newPlaylistName }
                if (playlistToDelete == null) {
                    println("Playlist with given name is not exists.")
                    return@run false
                }
                needToContributeToLibrary = true
                musicAppInstance.contributeToMusicLibrary()

                true
            }

            else -> {
                false
            }
        }

        return if (handledValue) null else current
    }
}