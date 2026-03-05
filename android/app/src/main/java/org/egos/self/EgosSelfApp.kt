package org.egos.self

import android.app.Application
import java.util.UUID

object EgosDevice {
    var id: String = ""
}

class EgosSelfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("egos_self", MODE_PRIVATE)
        val stored: String? = prefs.getString("device_id", null)
        EgosDevice.id = if (stored != null) {
            stored
        } else {
            val newId = UUID.randomUUID().toString().replace("-", "_")
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
    }
}
