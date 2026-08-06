package com.arena.assistantia

import android.content.Context
import org.json.JSONObject

object ObjectMemory {
    private const val PREFS = "grand_sage_objects"
    private const val KEY_SEEN = "seen_objects"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    data class DetectedObject(val label: String, val name: String = "")

    fun recordSeen(label: String, name: String = "") {
        LearningEngine.observe("objects", label)
    }

    fun allSeen(): List<DetectedObject> {
        return LearningEngine.topOf("objects", 20).map { DetectedObject(it) }
    }
}
