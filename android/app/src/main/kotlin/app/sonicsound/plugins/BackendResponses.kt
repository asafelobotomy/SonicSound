package app.sonicsound.plugins

import com.getcapacitor.JSObject
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONException

/** Shared ok/error JSObject responses for Capacitor plugin calls. */
class BackendResponses(private val gsonProvider: () -> Gson) {
    private val gson: Gson get() = gsonProvider()

    fun error(error: String?): JSObject {
        val ret = JSObject()
        ret.put("status", "error")
        ret.put("error", error)
        ret.put("value", null)
        return ret
    }

    @Throws(JSONException::class)
    fun ok(value: Any?): JSObject {
        val ret = JSObject()
        ret.put("status", "ok")
        ret.put("error", null)
        val valueJson = gson.toJson(value)
        ret.put("value", JSObject(valueJson))
        return ret
    }

    @Throws(JSONException::class)
    fun okArray(value: Any): JSObject {
        val ret = JSObject()
        ret.put("status", "ok")
        ret.put("error", null)
        val array = gson.toJson(value)
        ret.put("value", JSONArray(array))
        return ret
    }

    fun ok(value: String?): JSObject {
        val ret = JSObject()
        ret.put("status", "ok")
        ret.put("error", null)
        ret.put("value", value)
        return ret
    }

    fun ok(value: Boolean): JSObject {
        val ret = JSObject()
        ret.put("status", "ok")
        ret.put("error", null)
        ret.put("value", value)
        return ret
    }
}
