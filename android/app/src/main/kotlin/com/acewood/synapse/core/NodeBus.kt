package com.acewood.synapse.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-process message bus between the always-on NodeService and the screen (MainActivity).
 * Think of it as the wire harness: the service and the screen never hold references to each other.
 */
object NodeBus {
    enum class Command { WAKE, AMBIENT, RELOAD, CONFIG_CHANGED }
    enum class Event { TOUCH }

    private val main = Handler(Looper.getMainLooper())
    private val commandListeners = CopyOnWriteArraySet<(Command) -> Unit>()
    private val eventListeners = CopyOnWriteArraySet<(Event) -> Unit>()

    /** "active" or "ambient", or "off" when the activity isn't showing. Written by MainActivity. */
    @Volatile var screenMode: String = "off"

    fun onCommand(l: (Command) -> Unit) { commandListeners.add(l) }
    fun removeCommand(l: (Command) -> Unit) { commandListeners.remove(l) }
    fun onEvent(l: (Event) -> Unit) { eventListeners.add(l) }
    fun removeEvent(l: (Event) -> Unit) { eventListeners.remove(l) }

    /** Delivered on the main thread. */
    fun send(c: Command) = main.post { commandListeners.forEach { it(c) } }
    fun emit(e: Event) = main.post { eventListeners.forEach { it(e) } }
}
