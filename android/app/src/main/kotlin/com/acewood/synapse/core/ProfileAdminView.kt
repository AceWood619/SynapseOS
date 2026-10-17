package com.acewood.synapse.core

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.acewood.synapse.logic.Layout
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Profiles
import com.acewood.synapse.logic.Role

/** Profile CRUD surface. Clear PINs exist only in transient EditText fields and are never logged. */
class ProfileAdminView(
    context: Context,
    private var profiles: Profiles,
    private val onBack: () -> Unit,
) : ScrollView(context) {
    private val g = Glass
    private val body = g.col(context)
    private val roomChoices get() = HaRepository.rooms.map { it.id to it.name }
    private val appChoices get() = AppCatalog.all(context).map { AppCatalog.key(it) to it.label }

    init {
        background = g.ground(); addView(body)
        render()
    }

    private fun render() {
        body.removeAllViews()
        body.setPadding(g.dp(context, 18f), g.dp(context, 16f), g.dp(context, 18f), g.dp(context, 22f))
        body.addView(g.row(context).apply {
            addView(key("‹  SETTINGS", Glass.INK_DIM) { onBack() })
            addView(TextView(context).apply { text = "PROFILE ADMIN"; textSize = 20f; setTextColor(Glass.INK); typeface = g.disp(context); setPadding(g.dp(context, 14f), 0, 0, 0) })
        })
        body.addView(g.spacer(context, h = 10))
        body.addView(TextView(context).apply {
            text = "PINs are stored as hashes only. Blank PIN on an existing profile keeps its current hash."
            textSize = 12f; setTextColor(Glass.INK_DIM); setPadding(0, 0, 0, g.dp(context, 10f))
        })
        body.addView(key("＋  ADD PROFILE", Glass.BLUE) { edit(null) })
        body.addView(g.spacer(context, h = 12))
        profiles.list.forEach { profile ->
            body.addView(profileRow(profile)); body.addView(g.spacer(context, h = 9))
        }
    }

    private fun profileRow(profile: Profile): View = g.col(context).apply {
        background = g.panel(context, 16f); setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = profile.name; textSize = 16f; setTextColor(Glass.INK); typeface = g.body(context) })
                addView(TextView(context).apply { text = "${profile.role.name.lowercase()}  ·  ${if (profile.pinHash.isEmpty()) "no PIN" else "PIN protected"}"; textSize = 11f; setTextColor(Glass.INK_DIM) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(key("EDIT", Glass.BLUE) { edit(profile) })
            addView(g.spacer(context, w = 7))
            addView(key("DELETE", Glass.RED) { confirmDelete(profile) })
        })
        addView(TextView(context).apply {
            val rooms = profile.layout.rooms.size.takeIf { it > 0 }?.let { "$it rooms" } ?: "all rooms"
            val apps = profile.layout.homeApps.size.takeIf { it > 0 }?.let { "$it apps" } ?: "all apps"
            text = "$rooms  ·  $apps"; textSize = 11f; setTextColor(Glass.INK_FAINT); setPadding(0, g.dp(context, 8f), 0, 0)
        })
    }

    private fun edit(existing: Profile?) {
        val form = g.col(context).apply { setPadding(g.dp(context, 4f), 0, g.dp(context, 4f), 0) }
        val name = field("Name", existing?.name.orEmpty())
        val id = field("ID", existing?.id.orEmpty().ifBlank { "profile" + (profiles.list.size + 1) })
        if (existing != null) id.isEnabled = false
        val pin = field("PIN (blank keeps existing)", "").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val role = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, Role.entries.map { it.name.lowercase() })
            setSelection(Role.entries.indexOf(existing?.role ?: Role.USER).coerceAtLeast(0))
        }
        form.addView(name); form.addView(g.spacer(context, h = 7)); form.addView(id); form.addView(g.spacer(context, h = 7))
        form.addView(TextView(context).apply { text = "ROLE"; textSize = 10f; setTextColor(Glass.INK_FAINT) }); form.addView(role)
        form.addView(g.spacer(context, h = 7)); form.addView(pin)
        val roomChecks = checks(form, "ALLOWED ROOMS", roomChoices, existing?.layout?.rooms.orEmpty())
        val appChecks = checks(form, "ALLOWED APPS", appChoices, existing?.layout?.homeApps.orEmpty())
        AlertDialog.Builder(context).setTitle(if (existing == null) "Add profile" else "Edit ${existing.name}")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("SAVE", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val profileId = id.text.toString().trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
                        val profileName = name.text.toString().trim()
                        val selectedRole = Role.from(role.selectedItem?.toString())
                        val clearPin = pin.text.toString()
                        if (profileId.isBlank() || profileName.isBlank()) { Toast.makeText(context, "Name and ID are required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                        if (existing == null && selectedRole != Role.GUEST && clearPin.isBlank()) { Toast.makeText(context, "A PIN is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                        val layout = existing?.layout ?: Layout()
                        val next = profiles.upsert(profileId, profileName, selectedRole, clearPin.ifBlank { null }, layout.copy(
                            rooms = compactSelection(roomChecks, roomChoices.map { it.first }),
                            homeApps = compactSelection(appChecks, appChoices.map { it.first }),
                        ))
                        val errors = next.validate()
                        if (errors.isNotEmpty()) { Toast.makeText(context, errors.joinToString("; "), Toast.LENGTH_LONG).show(); return@setOnClickListener }
                        profiles = next; ProfileStore.save(context, profiles); NodeBus.send(NodeBus.Command.CONFIG_CHANGED); dialog.dismiss(); render()
                    }
                }
            }.show()
    }

    private fun checks(parent: LinearLayout, title: String, choices: List<Pair<String, String>>, selected: List<String>): List<Pair<String, CheckBox>> {
        parent.addView(TextView(context).apply { text = title; textSize = 10f; setTextColor(Glass.INK_FAINT); setPadding(0, g.dp(context, 10f), 0, 0) })
        val out = choices.map { (id, label) -> id to CheckBox(context).apply {
            text = label; textSize = 13f; setTextColor(Glass.INK); isChecked = selected.isEmpty() || id in selected
        } }
        out.forEach { parent.addView(it.second) }
        return out
    }
    private fun compactSelection(checks: List<Pair<String, CheckBox>>, all: List<String>): List<String> =
        checks.filter { it.second.isChecked }.map { it.first }.let { if (it.size == all.size) emptyList() else it }

    private fun confirmDelete(profile: Profile) {
        if (profile.role == Role.ADMIN && profiles.list.count { it.role == Role.ADMIN } <= 1) {
            Toast.makeText(context, "Keep at least one admin profile", Toast.LENGTH_SHORT).show(); return
        }
        AlertDialog.Builder(context).setTitle("Delete ${profile.name}?").setMessage("This removes the profile from this node.")
            .setNegativeButton("Cancel", null).setPositiveButton("DELETE") { _, _ ->
                profiles = profiles.without(profile.id); ProfileStore.save(context, profiles); NodeBus.send(NodeBus.Command.CONFIG_CHANGED); render()
            }.show()
    }

    private fun field(hint: String, value: String) = EditText(context).apply {
        this.hint = hint; setHintTextColor(Glass.INK_FAINT); setTextColor(Glass.INK); textSize = 14f; setText(value)
        background = g.panel(context, 12f); setPadding(g.dp(context, 12f), g.dp(context, 9f), g.dp(context, 12f), g.dp(context, 9f))
    }
    private fun key(label: String, color: Int, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 11f; setTextColor(if (color == Glass.RED) Color.WHITE else color); gravity = Gravity.CENTER
        background = g.tile(context, color, false, 12f); setPadding(g.dp(context, 11f), g.dp(context, 9f), g.dp(context, 11f), g.dp(context, 9f)); tap { action() }
    }
}
