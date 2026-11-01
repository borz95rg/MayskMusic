package com.maysk.music.data

/** Минимальная длина обрезаемого фрагмента, мс. */
const val MIN_TRIM_MS = 500L

/**
 * Проверяет диапазон обрезки. Возвращает текст ошибки или null, если диапазон корректный.
 * [durationMs] — длительность трека; допускается небольшой запас (2 с) из-за неточных метаданных.
 */
fun trimRangeError(startMs: Long, endMs: Long, durationMs: Long): String? {
    if (endMs <= startMs + MIN_TRIM_MS) return "Слишком короткий фрагмент (минимум ~0.5 с)"
    if (startMs < 0 || endMs > durationMs + 2000) return "Неверный диапазон"
    return null
}
