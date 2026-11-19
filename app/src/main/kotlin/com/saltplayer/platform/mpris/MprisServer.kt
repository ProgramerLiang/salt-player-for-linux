// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform.mpris

import com.saltplayer.BuildInfo
import com.saltplayer.core.Log
import com.saltplayer.domain.model.Track
import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.playback.PlayerState
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/**
 * MPRIS 2.2 server (`org.mpris.MediaPlayer2`), so desktop media keys, media widgets, playerctl
 * and lock-screen controls work on GNOME, KDE, Sway, etc.
 *
 * D-Bus runs the methods on its own threads; every call is forwarded to the playback controller,
 * which serialises commands internally. Property changes are broadcast with
 * `PropertiesChanged` so widgets refresh immediately.
 */
class MprisServer(
    private val controls: Controls,
) : AutoCloseable {

    interface Controls {
        fun state(): PlayerState
        fun play()
        fun pause()
        fun playPause()
        fun next()
        fun previous()
        fun stop()
        fun seekTo(positionMs: Long)
        fun seekBy(deltaMs: Long)
        fun setVolume(volume: Double)
        fun setPlayMode(mode: PlayMode)
        fun artworkUri(track: Track?): String?
    }

    private var connection: DBusConnection? = null
    private var exported: MprisExportedObject? = null

    @Volatile
    private var lastState: PlayerState = PlayerState()
    @Volatile
    private var lastTrack: Track? = null

    val busName: String = BUS_NAME

    /** Connects to the session bus and exports the MPRIS object. Returns false when unusable. */
    fun start(): Boolean = runCatching {
        val conn = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        val objectPath = OBJECT_PATH
        val obj = exportedObject(objectPath)
        conn.exportObject(objectPath, obj)
        conn.requestBusName(BUS_NAME)
        connection = conn
        exported = obj
        BuildInfo.mprisActive = true
        Log.info("MPRIS", "exported $BUS_NAME at $objectPath")
        true
    }.onFailure { Log.warn("MPRIS", "could not start MPRIS service", it) }.getOrDefault(false)

    /** Called by the UI whenever playback state changes. */
    fun update(state: PlayerState, track: Track?) {
        val previous = lastState
        lastState = state
        lastTrack = track
        val conn = connection ?: return
        val changed = buildMap<String, Variant<*>> {
            if (previous.playing != state.playing) {
                put("PlaybackStatus", Variant(if (state.playing) "Playing" else "Paused", "s"))
            }
            if (previous.track?.id != track?.id) {
                put("Metadata", Variant(metadata(track), "a{sv}"))
            }
            if (previous.mode != state.mode) {
                put("LoopStatus", Variant(loopStatus(state.mode), "s"))
                put("Shuffle", Variant(state.mode.isShuffle, "b"))
            }
            if (previous.volume != state.volume) {
                put("Volume", Variant(state.volume.toDouble(), "d"))
            }
        }
        if (changed.isEmpty()) return
        runCatching {
            conn.sendMessage(PropertiesChangedSignal(OBJECT_PATH, PLAYER_INTERFACE, changed, emptyList()))
        }.onFailure { Log.warn("MPRIS", "failed to emit PropertiesChanged", it) }
    }

    fun notifySeeked(positionMs: Long) {
        val conn = connection ?: return
        runCatching {
            conn.sendMessage(SeekedSignal(OBJECT_PATH, positionMs * 1000))
        }
    }

    private fun loopStatus(mode: PlayMode): String = when (mode) {
        PlayMode.RepeatOne -> "Track"
        PlayMode.RepeatAll -> "Playlist"
        else -> "None"
    }

    private fun metadata(track: Track?): Map<String, Variant<*>> {
        val state = controls.state()
        if (track == null) return mapOf("mpris:trackid" to Variant(TRACK_PATH_PREFIX + 0, "o"))
        val map = linkedMapOf<String, Variant<*>>()
        map["mpris:trackid"] = Variant(TRACK_PATH_PREFIX + track.id, "o")
        if (track.durationMs > 0) map["mpris:length"] = Variant(track.durationMs * 1000, "x")
        controls.artworkUri(track)?.let { map["mpris:artUrl"] = Variant(it, "s") }
        map["xesam:title"] = Variant(track.displayTitle, "s")
        map["xesam:artist"] = Variant(listOf(track.displayArtist), "as")
        map["xesam:album"] = Variant(track.album, "s")
        if (track.trackNo > 0) map["xesam:trackNumber"] = Variant(track.trackNo, "i")
        map["xesam:url"] = Variant("file://" + track.path, "s")
        map["xesam:useCount"] = Variant(state.index + 1, "i")
        return map
    }

    /** Builds the exported MPRIS object with a Java bridge for the generic Properties API. */
    private fun exportedObject(path: String): MprisExportedObject {
        val callbacks = object : MprisExportedObject.Callbacks {
            override fun properties(interfaceName: String): Map<String, Variant<*>> {
                val state = controls.state()
                val track = state.track
                return when (interfaceName) {
                    ROOT_INTERFACE -> linkedMapOf(
                        "Identity" to Variant("Salt Shaker", "s"),
                        "DesktopEntry" to Variant("salt-shaker", "s"),
                        "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
                        "SupportedMimeTypes" to Variant(
                            listOf("audio/mpeg", "audio/flac", "audio/ogg", "audio/x-wav", "audio/mp4"), "as"
                        ),
                        "CanQuit" to Variant(true, "b"),
                        "CanRaise" to Variant(true, "b"),
                        "HasTrackList" to Variant(false, "b"),
                        "CanSetFullscreen" to Variant(false, "b"),
                        "Fullscreen" to Variant(false, "b"),
                    )

                    PLAYER_INTERFACE -> linkedMapOf(
                        "PlaybackStatus" to Variant(if (state.playing) "Playing" else "Paused", "s"),
                        "LoopStatus" to Variant(loopStatus(state.mode), "s"),
                        "Rate" to Variant(1.0, "d"),
                        "Shuffle" to Variant(state.mode.isShuffle, "b"),
                        "Metadata" to Variant(metadata(track), "a{sv}"),
                        "Volume" to Variant(state.volume.toDouble(), "d"),
                        "Position" to Variant(state.positionMs * 1000, "x"),
                        "MinimumRate" to Variant(1.0, "d"),
                        "MaximumRate" to Variant(1.0, "d"),
                        "CanGoNext" to Variant(state.queue.isNotEmpty(), "b"),
                        "CanGoPrevious" to Variant(state.queue.isNotEmpty(), "b"),
                        "CanPlay" to Variant(true, "b"),
                        "CanPause" to Variant(true, "b"),
                        "CanSeek" to Variant(true, "b"),
                        "CanControl" to Variant(true, "b"),
                    )

                    else -> emptyMap()
                }
            }

            override fun set(interfaceName: String, propertyName: String, value: Any?) {
                val unwrapped = (value as? Variant<*>)?.value ?: value
                when (propertyName) {
                    "Volume" -> controls.setVolume((unwrapped as? Number)?.toDouble() ?: return)
                    "LoopStatus" -> controls.setPlayMode(
                        when (unwrapped?.toString()) {
                            "Track" -> PlayMode.RepeatOne
                            "Playlist" -> PlayMode.RepeatAll
                            else -> PlayMode.Sequential
                        }
                    )

                    "Shuffle" -> controls.setPlayMode(
                        if (unwrapped as? Boolean == true) PlayMode.ShuffleNoRepeat else PlayMode.Sequential
                    )
                }
            }

            override fun raise() = Unit
            override fun quit() = controls.stop()
            override fun next() = controls.next()
            override fun previous() = controls.previous()
            override fun pause() = controls.pause()
            override fun playPause() = controls.playPause()
            override fun stop() = controls.stop()
            override fun play() = controls.play()
            override fun seek(micros: Long) {
                controls.seekBy(micros / 1000)
                notifySeeked(controls.state().positionMs)
            }

            override fun setPosition(micros: Long) {
                controls.seekTo(micros / 1000)
                notifySeeked(micros / 1000)
            }

            override fun openUri(uri: String) = Log.info("MPRIS", "OpenUri is not supported: $uri")
            override fun introspect(): String = INTROSPECT_XML
        }
        return MprisExportedObject(path, callbacks, INTROSPECT_XML)
    }

    private class PropertiesChangedSignal(
        path: String,
        interfaceName: String,
        changed: Map<String, Variant<*>>,
        invalidated: List<String>,
    ) : DBusSignal(
        'l'.code.toByte(),
        null,
        path,
        "org.freedesktop.DBus.Properties",
        "PropertiesChanged",
        "sa{sv}as",
        interfaceName,
        changed,
        invalidated,
    )

    private class SeekedSignal(path: String, positionMicros: Long) : DBusSignal(
        'l'.code.toByte(),
        null,
        path,
        PLAYER_INTERFACE,
        "Seeked",
        "x",
        positionMicros,
    )

    override fun close() {
        runCatching {
            connection?.releaseBusName(BUS_NAME)
            connection?.disconnect()
        }
        connection = null
        BuildInfo.mprisActive = false
    }

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.saltshaker"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        private const val TRACK_PATH_PREFIX = "/com/saltplayer/track/"

        private val INTROSPECT_XML = """
            <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
             "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
            <node>
              <interface name="org.mpris.MediaPlayer2">
                <method name="Raise"/>
                <method name="Quit"/>
                <property name="Identity" type="s" access="read"/>
                <property name="DesktopEntry" type="s" access="read"/>
                <property name="SupportedUriSchemes" type="as" access="read"/>
                <property name="SupportedMimeTypes" type="as" access="read"/>
                <property name="CanQuit" type="b" access="read"/>
                <property name="CanRaise" type="b" access="read"/>
                <property name="HasTrackList" type="b" access="read"/>
                <property name="CanSetFullscreen" type="b" access="read"/>
                <property name="Fullscreen" type="b" access="readwrite"/>
              </interface>
              <interface name="org.mpris.MediaPlayer2.Player">
                <method name="Next"/>
                <method name="Previous"/>
                <method name="Pause"/>
                <method name="PlayPause"/>
                <method name="Stop"/>
                <method name="Play"/>
                <method name="Seek"><arg name="Offset" type="x" direction="in"/></method>
                <method name="SetPosition">
                  <arg name="TrackId" type="o" direction="in"/>
                  <arg name="Position" type="x" direction="in"/>
                </method>
                <method name="OpenUri"><arg name="Uri" type="s" direction="in"/></method>
                <signal name="Seeked"><arg name="Position" type="x"/></signal>
                <property name="PlaybackStatus" type="s" access="read"/>
                <property name="LoopStatus" type="s" access="readwrite"/>
                <property name="Rate" type="d" access="readwrite"/>
                <property name="Shuffle" type="b" access="readwrite"/>
                <property name="Metadata" type="a{sv}" access="read"/>
                <property name="Volume" type="d" access="readwrite"/>
                <property name="Position" type="x" access="read"/>
                <property name="MinimumRate" type="d" access="read"/>
                <property name="MaximumRate" type="d" access="read"/>
                <property name="CanGoNext" type="b" access="read"/>
                <property name="CanGoPrevious" type="b" access="read"/>
                <property name="CanPlay" type="b" access="read"/>
                <property name="CanPause" type="b" access="read"/>
                <property name="CanSeek" type="b" access="read"/>
                <property name="CanControl" type="b" access="read"/>
              </interface>
              <interface name="org.freedesktop.DBus.Properties">
                <method name="Get">
                  <arg name="interface_name" type="s" direction="in"/>
                  <arg name="property_name" type="s" direction="in"/>
                  <arg name="value" type="v" direction="out"/>
                </method>
                <method name="GetAll">
                  <arg name="interface_name" type="s" direction="in"/>
                  <arg name="properties" type="a{sv}" direction="out"/>
                </method>
                <method name="Set">
                  <arg name="interface_name" type="s" direction="in"/>
                  <arg name="property_name" type="s" direction="in"/>
                  <arg name="value" type="v" direction="in"/>
                </method>
                <signal name="PropertiesChanged">
                  <arg name="interface_name" type="s"/>
                  <arg name="changed_properties" type="a{sv}"/>
                  <arg name="invalidated_properties" type="as"/>
                </signal>
              </interface>
            </node>
        """.trimIndent()
    }
}
