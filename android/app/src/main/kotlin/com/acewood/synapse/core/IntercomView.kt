package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.acewood.synapse.logic.Intercom
import com.acewood.synapse.logic.IntercomTarget

@SuppressLint("ViewConstructor")
class IntercomView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = g.col(context)
    private val roomRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val message = EditText(context)
    private val status = TextView(context)
    private var target: IntercomTarget? = null
    private var selectedRoom: String? = null
    private val canned = listOf("Dinner's ready", "Bedtime in 15", "Come here please")

    init {
        background = g.ground()
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(g.dp(context, 16f), g.dp(context, 14f), g.dp(context, 16f), g.dp(context, 14f))
        }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM) { onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Intercom"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(TextView(context).apply { text = "Announce to a room"; textSize = 12f; setTextColor(Glass.INK_DIM) })
            })
        })
        outer.addView(g.spacer(context, h = 14))
        outer.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun open() {
        target = Intercom.resolve(HaRepository.cache)
        val t = target
        selectedRoom = selectedRoom?.takeIf { t?.rooms?.contains(it) == true } ?: t?.defaultRoom
        render()
    }

    private fun render() {
        body.removeAllViews()
        val t = target
        if (t == null) {
            body.addView(status.apply { text = "Intercom is not configured in Home Assistant.\nExpected input_select.intercom_room and script.intercom_send." })
            return
        }
        body.addView(g.label(context, "ROOM"))
        body.addView(g.spacer(context, h = 7))
        roomRow.removeAllViews()
        t.rooms.forEach { room ->
            roomRow.addView(roomKey(room, room == selectedRoom))
            roomRow.addView(g.spacer(context, w = 7))
        }
        body.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(roomRow) })
        body.addView(g.spacer(context, h = 16))
        body.addView(g.label(context, "QUICK ANNOUNCEMENTS"))
        body.addView(g.spacer(context, h = 7))
        body.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                canned.forEach { text -> addView(chip(text) { message.setText(text); message.setSelection(message.length()) }) ; addView(g.spacer(context, w = 7)) }
            })
        })
        body.addView(g.spacer(context, h = 16))
        message.apply {
            hint = "Type an announcement…"; setHintTextColor(Glass.INK_FAINT); setTextColor(Glass.INK); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = g.panel(context, 16f); setPadding(g.dp(context, 14f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f)); minLines = 3
        }
        body.addView(message, LinearLayout.LayoutParams(-1, -2))
        body.addView(g.spacer(context, h = 12))
        body.addView(key("ANNOUNCE", Glass.BLUE) { send() })
        body.addView(status.apply { text = if (t.messageInputId == null) "No matching input_text message helper was found." else "Master bedroom is never selected automatically."; setTextColor(Glass.INK_DIM); textSize = 12f; setPadding(0, g.dp(context, 12f), 0, 0) })
    }

    private fun send() {
        val t = target ?: return
        val room = selectedRoom
        if (room == null) { status.text = "Choose a room first."; return }
        if (t.messageInputId == null) { status.text = "No intercom message input_text was found."; return }
        if (HaRepository.sendIntercom(t, room, message.text?.toString().orEmpty())) {
            status.text = "Sent to $room"
            message.setText("")
        } else status.text = "Type an announcement first."
    }

    private fun roomKey(room: String, selected: Boolean): View = TextView(context).apply {
        text = room; textSize = 13f; setTextColor(if (selected) Glass.INK else Glass.BLUE); gravity = Gravity.CENTER
        background = g.tile(context, Glass.BLUE, selected, 999f); setPadding(g.dp(context, 15f), g.dp(context, 10f), g.dp(context, 15f), g.dp(context, 10f))
        tap { selectedRoom = room; render() }
    }
    private fun chip(label: String, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 12.5f; setTextColor(Glass.BLUE); background = g.tile(context, Glass.BLUE, false, 999f)
        setPadding(g.dp(context, 13f), g.dp(context, 8f), g.dp(context, 13f), g.dp(context, 8f)); tap { action() }
    }
    private fun key(label: String, color: Int, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 12f; setTextColor(color); typeface = g.disp(context); gravity = Gravity.CENTER; letterSpacing = 0.06f
        background = g.tile(context, color, false, 14f); setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f)); tap { action() }
    }
}
