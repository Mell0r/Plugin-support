package com.h0tk3y.player

import com.h0tk3y.player.TrackMetadataKeys.PATH
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

private const val NEW_PLAYLIST = "=== "

private fun parsePlaylists(text: List<String>?): List<Playlist> {
    if (text == null)
        return emptyList()

    return text.fold(mutableListOf()) { current, line ->
        if (line.startsWith(NEW_PLAYLIST)) {
            current += Playlist(line.removePrefix(NEW_PLAYLIST), mutableListOf())
            return@fold current
        }

        val args = line.split("\\s".toRegex())
        val trackFile = File(args[0])
        if (!trackFile.exists()) {
            println("Saved track '${args[0]}' doesn't exist now, so it was skipped.")
            return@fold current
        }
        current.last().tracks += Track(
            args.drop(1).windowed(2, 2).associate { it[0] to it[1] },
            trackFile
        )
        current
    }
}

class MusicLibraryPersistencePlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    override fun init(persistedState: InputStream?) {
        musicAppInstance.musicLibrary.playlists.addAll(parsePlaylists(persistedState?.reader()?.readLines()))
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.writer(StandardCharsets.UTF_8).use { writer ->
            musicAppInstance.musicLibrary.playlists.forEach { playlist ->
                writer.append(NEW_PLAYLIST + playlist.name + '\n')
                playlist.tracks.forEach inner@ { track ->
                    val path = track.metadata[PATH] ?: return@inner
                    writer.append("$path ")
                    writer.append(
                        track.metadata.toList().flatMap {
                            if (it.first == PATH)
                                listOf()
                            else
                                listOf(it.first, it.second)
                        }.joinToString(separator = " ", postfix = "\n")
                    )
                }
            }
        }
    }
}