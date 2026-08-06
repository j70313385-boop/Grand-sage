package com.arena.assistantia.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ProfileManager — Gestion des profils multiples.
 */
object ProfileManager {
    private const val PREFS = "grand_sage_profiles"
    private const val KEY_ACTIVE = "active_profile"
    private const val KEY_PROFILES = "profiles_list"

    private lateinit var prefs: android.content.SharedPreferences

    data class Profile(
        val id: String,
        val name: String,
        val nicknames: List<String>,
        val city: String,
        val createdAt: Long
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun createProfile(name: String, nicknames: List<String> = emptyList(), city: String = "Paris"): String {
        val id = "profile_${System.currentTimeMillis()}_${(0..999).random()}"
        val profile = Profile(id, name, nicknames.ifEmpty { listOf("Mon seigneur") }, city, System.currentTimeMillis())
        val profiles = allProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
        return id
    }

    fun switchTo(profileId: String) {
        if (allProfiles().any { it.id == profileId }) {
            prefs.edit().putString(KEY_ACTIVE, profileId).apply()
        }
    }

    fun activeProfile(): Profile? {
        val activeId = prefs.getString(KEY_ACTIVE, null) ?: return null
        return allProfiles().firstOrNull { it.id == activeId }
    }

    fun allProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Profile>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val nicknames = if (j.has("nicknames")) {
                val narr = j.getJSONArray("nicknames")
                (0 until narr.length()).map { narr.getString(it) }
            } else emptyList()
            out.add(Profile(
                id = j.getString("id"),
                name = j.getString("name"),
                nicknames = nicknames,
                city = j.optString("city", "Paris"),
                createdAt = j.getLong("createdAt")
            ))
        }
        return out
    }

    private fun saveProfiles(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("nicknames", JSONArray(p.nicknames))
                put("city", p.city)
                put("createdAt", p.createdAt)
            })
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }
}
