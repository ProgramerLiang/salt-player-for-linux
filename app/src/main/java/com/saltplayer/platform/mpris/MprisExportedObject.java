/* SPDX-License-Identifier: LGPL-3.0-or-later
 * Copyright (C) 2026 Corripo */
package com.saltplayer.platform.mpris;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;


/**
 * MPRIS D-Bus object. Implemented in Java so dbus-java's generic
 * `&lt;A&gt; A Get(String, String)` signature is exported correctly (Kotlin cannot override it).
 */
public final class MprisExportedObject implements DBusInterface, Introspectable {
    private final String objectPath;
    private final Callbacks callbacks;
    private final String introspectionXml;

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    public interface Root extends DBusInterface {
        void Raise();
        void Quit();
    }

    @DBusInterfaceName("org.mpris.MediaPlayer2.Player")
    public interface Player extends DBusInterface {
        void Next();
        void Previous();
        void Pause();
        void PlayPause();
        void Stop();
        void Play();
        void Seek(long offsetMicros);
        void SetPosition(DBusPath trackId, long positionMicros);
        void OpenUri(String uri);
    }

    public interface Callbacks {
        Map<String, Variant<?>> properties(String interfaceName);
        void set(String interfaceName, String propertyName, Object value);
        void raise();
        void quit();
        void next();
        void previous();
        void pause();
        void playPause();
        void stop();
        void play();
        void seek(long micros);
        void setPosition(long micros);
        void openUri(String uri);
        String introspect();
    }

    public MprisExportedObject(String objectPath, Callbacks callbacks, String introspectionXml) {
        this.objectPath = objectPath;
        this.callbacks = callbacks;
        this.introspectionXml = introspectionXml;
    }

    @Override public String getObjectPath() { return objectPath; }

    public void Raise() { callbacks.raise(); }
    public void Quit() { callbacks.quit(); }
    public void Next() { callbacks.next(); }
    public void Previous() { callbacks.previous(); }
    public void Pause() { callbacks.pause(); }
    public void PlayPause() { callbacks.playPause(); }
    public void Stop() { callbacks.stop(); }
    public void Play() { callbacks.play(); }
    public void Seek(long offsetMicros) { callbacks.seek(offsetMicros); }
    public void SetPosition(DBusPath trackId, long positionMicros) { callbacks.setPosition(positionMicros); }
    public void OpenUri(String uri) { callbacks.openUri(uri); }

    // --- MPRIS properties (dbus-java exports these through org.freedesktop.DBus.Properties) ---
    private Variant<?> prop(String name) {
        Map<String, Variant<?>> all = callbacks.properties("org.mpris.MediaPlayer2.Player");
        Variant<?> playerValue = all.get(name);
        if (playerValue != null) return playerValue;
        return callbacks.properties("org.mpris.MediaPlayer2").get(name);
    }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ, type = String.class)
    public String getPlaybackStatus() { return unwrap("PlaybackStatus", String.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ, type = String.class)
    public String getLoopStatus() { return unwrap("LoopStatus", String.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getShuffle() { return unwrap("Shuffle", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public double getVolume() { return unwrap("Volume", Double.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public long getPosition() { return unwrap("Position", Long.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanGoNext() { return unwrap("CanGoNext", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanGoPrevious() { return unwrap("CanGoPrevious", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanPlay() { return unwrap("CanPlay", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanPause() { return unwrap("CanPause", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanSeek() { return unwrap("CanSeek", Boolean.class); }

    @org.freedesktop.dbus.annotations.DBusBoundProperty(access = org.freedesktop.dbus.annotations.DBusProperty.Access.READ)
    public boolean getCanControl() { return unwrap("CanControl", Boolean.class); }

    @SuppressWarnings("unchecked")
    private <T> T unwrap(String name, Class<T> type) {
        Variant<?> variant = prop(name);
        Object value = variant == null ? null : variant.getValue();
        if (type.isInstance(value)) return (T) value;
        if (type == long.class && value instanceof Number) return (T) Long.valueOf(((Number) value).longValue());
        if (type == double.class && value instanceof Number) return (T) Double.valueOf(((Number) value).doubleValue());
        if (type == boolean.class && value instanceof Boolean) return (T) value;
        if (type == String.class && value != null) return (T) value.toString();
        return null;
    }

    @Override public String Introspect() { return introspectionXml; }
}
