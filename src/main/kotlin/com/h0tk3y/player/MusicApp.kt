package com.h0tk3y.player

import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Constructor
import java.net.URLClassLoader

fun Class<*>.tryGetConstructor(parameterTypes: List<Class<*>>): Constructor<*>? =
    try {
        getConstructor(*parameterTypes.toTypedArray())
    } catch (e: NoSuchMethodException) {
        null
    }

open class MusicApp(
    private val pluginPaths: List<MusicPluginPath>
) : AutoCloseable {
    private val PLUGINS_DATA_FOLDER = "pluginsData"

    /** Инициализирует плагины с помощью функции [MusicPlugin.init],
     *  предоставляя им байтовые потоки их состояния (для тех плагинов, для которых они сохранены,
     *  для остальных – null).
     *  Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
     *       [IllegalPluginException] и [PluginClassNotFoundException].
     **/
    fun init() {
        plugins.forEach {
            val initStream = try {
                File("$PLUGINS_DATA_FOLDER/${it.pluginId}").inputStream()
            } catch(e: FileNotFoundException) {
                null
            }
            it.init(initStream)
            initStream?.close()
        }

        contributeToMusicLibrary()

        player.init()
    }

    /** Очищает сохраненные данные плагинов */
    fun wipePersistedPluginData() =
        File(PLUGINS_DATA_FOLDER).deleteRecursively()

    /** Загружает плагины, перечисленные в [pluginPaths]. */
    private val plugins: List<MusicPlugin> by lazy<List<MusicPlugin>> {
        pluginPaths.flatMap { pluginPath ->
            val loader = URLClassLoader(pluginPath.pluginClasspath.map { it.toURI().toURL() }.toTypedArray())
            pluginPath.pluginClasses.map { className ->
                val runtimeClass = try {
                    loader.loadClass(className)
                } catch(e: Exception) {
                    throw PluginClassNotFoundException(className)
                }
                val musicAppConstructor = runtimeClass.tryGetConstructor(listOf(MusicApp::class.java))
                val emptyConstructor = runtimeClass.tryGetConstructor(emptyList())
                if (musicAppConstructor != null) {
                    try {
                        musicAppConstructor.newInstance(this)
                    } catch(e: Exception) {
                        throw IllegalPluginException(runtimeClass)
                    }
                }
                else if (emptyConstructor != null) {
                    val instance = try {
                        emptyConstructor.newInstance()
                    } catch(e: Exception) {
                        throw IllegalPluginException(runtimeClass)
                    }

                    try {
                        runtimeClass.getField("musicAppInstance").set(instance, this)
                    } catch(e : Exception) {
                        throw IllegalPluginException(runtimeClass)
                    }

                    instance
                }
                else throw IllegalPluginException(runtimeClass)
            }
        }.filterIsInstance<MusicPlugin>().distinctBy { it.pluginId }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.find { it.pluginId == pluginClassName }


    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary = MusicLibrary(mutableListOf())

    fun contributeToMusicLibrary() {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(musicLibrary) { acc, it -> it.contribute(acc) }
    }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(playbackListeners)
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumedFromPause = false
        )
    }

    fun nextOrStop(): Boolean =
        player.playbackState.playlistPosition?.let {
            val nextPosition = it.position + 1
            val newState = if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumedFromPause = false
                )
            else
                PlaybackState.Stopped
            player.playbackState = newState
            if (newState is PlaybackState.Playing) true else false
        } ?: false

    @Volatile
    var isClosed = false
        private set

    /** Сохраняет состояние плагинов с помощью [MusicPlugin.persist]. */
    override fun close() {
        if (isClosed) return
        isClosed = true

        val dataFolder = File(PLUGINS_DATA_FOLDER)
        if (!dataFolder.exists())
            dataFolder.mkdir()

        plugins.forEach { plugin ->
            val currentData = dataFolder.resolve(plugin.pluginId)
            currentData.createNewFile()
            val pluginStream = currentData.outputStream()
            plugin.persist(pluginStream)
            pluginStream.close()
        }

        player.close()
    }
}