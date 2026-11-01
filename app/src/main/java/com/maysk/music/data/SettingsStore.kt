package com.maysk.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AppLang(val title: String) {
    RU("Русский"),
    KK("Қазақша"),
    EN("English"),
}

enum class ThemePreset(val title: String, val subtitle: String) {
    AURORA("Aurora Glass", "Мягкие градиенты и стекло"),
    AMOLED("AMOLED Ultra", "Чёрный фон, минимализм"),
    NEON("Neon Glass", "Яркие неоновые эффекты"),
    DYNAMIC("Maysk Dynamic", "Цвета из обложки"),
    MINIMAL("Modern Minimal", "Простой и чистый"),
    CLASSIC("Classic Dark", "Классический тёмный"),
    LIGHT("Light Modern", "Светлая тема"),
}

enum class PlayerStyle(val title: String) { COVER("Обложка"), VINYL("Винил"), MINIMAL("Минимал") }
enum class MiniPlayerStyle(val title: String) { GLASS("Стекло"), COMPACT("Компакт") }
enum class BackgroundStyle(val title: String) { GRADIENT("Градиент"), AURORA("Аврора"), SOLID("Сплошной") }
enum class VisualizerStyle(val title: String) { OFF("Выкл"), SPECTRUM("Спектр"), WAVES("Волны"), CIRCLE("Неоновый круг") }

data class AppSettings(
    val theme: ThemePreset = ThemePreset.AURORA,
    val followSystem: Boolean = false,
    val accent: Long = 0xFF8B5CF6,
    val buttonColor: Long = 0xFF8B5CF6,
    val cardAlpha: Float = 0.10f,
    val corner: Int = 20,
    val coverSize: Int = 1, // 0 small, 1 medium, 2 large
    val grid: Int = 3,
    val fontScale: Float = 1.0f,
    val miniPlayer: MiniPlayerStyle = MiniPlayerStyle.GLASS,
    val player: PlayerStyle = PlayerStyle.COVER,
    val background: BackgroundStyle = BackgroundStyle.GRADIENT,
    val animSpeed: Float = 1.0f, // 0 = выключено
    val glow: Boolean = true,
    val simpleMode: Boolean = false,
    val highContrast: Boolean = false,
    val visualizer: VisualizerStyle = VisualizerStyle.OFF,
    val excludedFolders: Set<String> = emptySet(),
    val hideSystemFolders: Boolean = true,
    val minDurationSec: Int = 20,
    val autoUpdateCheck: Boolean = true,
    val customUpdateRepo: String = "",
    /** Плавный переход между треками (затухание/нарастание), секунды. 0 = выкл. */
    val crossfadeSec: Int = 0,
    /** Баланс: -1 = только левый, 0 = центр, 1 = только правый. */
    val balance: Float = 0f,
    val language: AppLang = AppLang.RU,
) {
    val animationsEnabled: Boolean get() = animSpeed > 0f
}

/** Сохранённая пользовательская тема (часть настроек оформления). */
data class CustomTheme(val name: String, val settings: AppSettings)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("maysk_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val raw = prefs.getString("settings", null) ?: return AppSettings()
        return try { fromJson(JSONObject(raw)) } catch (e: Exception) { AppSettings() }
    }

    private fun save(s: AppSettings) {
        prefs.edit().putString("settings", toJson(s).toString()).apply()
    }

    // ---- Пользовательские темы ----
    fun customThemes(): List<CustomTheme> {
        val raw = prefs.getString("custom_themes", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                CustomTheme(o.getString("name"), fromJson(o.getJSONObject("s")))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveCustomTheme(name: String, s: AppSettings) {
        val list = customThemes().filter { it.name != name } + CustomTheme(name, s)
        writeThemes(list)
    }

    fun deleteCustomTheme(name: String) = writeThemes(customThemes().filter { it.name != name })

    private fun writeThemes(list: List<CustomTheme>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("s", toJson(it.settings))) }
        prefs.edit().putString("custom_themes", arr.toString()).apply()
    }

    fun exportJson(): JSONObject = JSONObject()
        .put("settings", toJson(current))
        .put("customThemes", JSONArray(prefs.getString("custom_themes", "[]")))

    fun importJson(o: JSONObject) {
        o.optJSONObject("settings")?.let { val s = fromJson(it); _state.value = s; save(s) }
        o.optJSONArray("customThemes")?.let { prefs.edit().putString("custom_themes", it.toString()).apply() }
    }

    companion object {
        fun toJson(s: AppSettings): JSONObject = JSONObject().apply {
            put("theme", s.theme.name)
            put("followSystem", s.followSystem)
            put("accent", s.accent)
            put("buttonColor", s.buttonColor)
            put("cardAlpha", s.cardAlpha.toDouble())
            put("corner", s.corner)
            put("coverSize", s.coverSize)
            put("grid", s.grid)
            put("fontScale", s.fontScale.toDouble())
            put("miniPlayer", s.miniPlayer.name)
            put("player", s.player.name)
            put("background", s.background.name)
            put("animSpeed", s.animSpeed.toDouble())
            put("glow", s.glow)
            put("simpleMode", s.simpleMode)
            put("highContrast", s.highContrast)
            put("visualizer", s.visualizer.name)
            put("excludedFolders", JSONArray(s.excludedFolders.toList()))
            put("hideSystemFolders", s.hideSystemFolders)
            put("minDurationSec", s.minDurationSec)
            put("autoUpdateCheck", s.autoUpdateCheck)
            put("customUpdateRepo", s.customUpdateRepo)
            put("crossfadeSec", s.crossfadeSec)
            put("balance", s.balance.toDouble())
            put("language", s.language.name)
        }

        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            val ex = o.optJSONArray("excludedFolders")
            return AppSettings(
                theme = enumOr(o.optString("theme"), d.theme),
                followSystem = o.optBoolean("followSystem", d.followSystem),
                accent = o.optLong("accent", d.accent),
                buttonColor = o.optLong("buttonColor", d.buttonColor),
                cardAlpha = o.optDouble("cardAlpha", d.cardAlpha.toDouble()).toFloat(),
                corner = o.optInt("corner", d.corner),
                coverSize = o.optInt("coverSize", d.coverSize),
                grid = o.optInt("grid", d.grid),
                fontScale = o.optDouble("fontScale", d.fontScale.toDouble()).toFloat(),
                miniPlayer = enumOr(o.optString("miniPlayer"), d.miniPlayer),
                player = enumOr(o.optString("player"), d.player),
                background = enumOr(o.optString("background"), d.background),
                animSpeed = o.optDouble("animSpeed", d.animSpeed.toDouble()).toFloat(),
                glow = o.optBoolean("glow", d.glow),
                simpleMode = o.optBoolean("simpleMode", d.simpleMode),
                highContrast = o.optBoolean("highContrast", d.highContrast),
                visualizer = enumOr(o.optString("visualizer"), d.visualizer),
                excludedFolders = if (ex != null) (0 until ex.length()).map { ex.getString(it) }.toSet() else d.excludedFolders,
                hideSystemFolders = o.optBoolean("hideSystemFolders", d.hideSystemFolders),
                minDurationSec = o.optInt("minDurationSec", d.minDurationSec),
                autoUpdateCheck = o.optBoolean("autoUpdateCheck", d.autoUpdateCheck),
                customUpdateRepo = o.optString("customUpdateRepo", d.customUpdateRepo),
                crossfadeSec = o.optInt("crossfadeSec", d.crossfadeSec),
                balance = o.optDouble("balance", d.balance.toDouble()).toFloat(),
                language = enumOr(o.optString("language"), d.language),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: def
    }
}
