package com.example.core.common

import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
        }
    }

    fun formatNumber(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
