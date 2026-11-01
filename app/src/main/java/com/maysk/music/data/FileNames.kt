package com.maysk.music.data

private val FORBIDDEN_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

/**
 * Приводит строку к безопасному имени файла (без расширения):
 * запрещённые символы и управляющие знаки заменяются на «_», края обрезаются,
 * точки и пробелы по краям убираются. Если ничего не осталось — возвращается [fallback].
 */
fun safeFileName(raw: String?, fallback: String, maxLength: Int = 60): String {
    val cleaned = (raw ?: "")
        .trim()
        .replace(FORBIDDEN_CHARS, "_")
        .trim(' ', '.')
        .take(maxLength)
        .trim(' ', '.')
    return cleaned.ifBlank { fallback }
}
