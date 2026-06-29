package com.example.utils

data class SubtitleCue(
    val start: Long,
    val end: Long,
    val text: String
)

object SubtitleParser {
    fun parseSrt(srtContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            // Normalize line endings and split by empty lines (supports both CRLF and LF)
            val normalized = srtContent.replace("\r\n", "\n")
            val blocks = normalized.trim().split(Regex("\n\\s*\n"))
            
            for (block in blocks) {
                val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size >= 3) {
                    val timeLine = lines[1]
                    if (timeLine.contains("-->")) {
                        val parts = timeLine.split("-->")
                        if (parts.size == 2) {
                            val startMs = parseTime(parts[0].trim())
                            val endMs = parseTime(parts[1].trim())
                            val text = lines.drop(2).joinToString("\n")
                            cues.add(SubtitleCue(startMs, endMs, text))
                        }
                    }
                } else if (lines.size == 2) {
                    // Try parsing if index is omitted (some files omit cue index)
                    val timeLine = lines[0]
                    if (timeLine.contains("-->")) {
                        val parts = timeLine.split("-->")
                        if (parts.size == 2) {
                            val startMs = parseTime(parts[0].trim())
                            val endMs = parseTime(parts[1].trim())
                            val text = lines.drop(1).joinToString("\n")
                            cues.add(SubtitleCue(startMs, endMs, text))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cues.sortedBy { it.start }
    }

    private fun parseTime(timeStr: String): Long {
        // Formats: HH:MM:SS,mmm or HH:MM:SS.mmm
        val cleanStr = timeStr.replace(',', '.')
        val parts = cleanStr.split(':')
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val secondsParts = parts[2].split('.')
            val seconds = secondsParts[0].toLongOrNull() ?: 0L
            val millis = if (secondsParts.size == 2) {
                val msStr = secondsParts[1].padEnd(3, '0').take(3)
                msStr.toLongOrNull() ?: 0L
            } else {
                0L
            }
            return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
        }
        return 0L
    }

    // Generate simulated subtitles for Big Buck Bunny and Sintel demo videos
    fun getDemoSubtitles(): String {
        return """
            1
            00:00:02,000 --> 00:00:06,000
            Welcome to the VLC Playit Media Player!
            
            2
            00:00:06,500 --> 00:00:10,000
            This app features seamless local playback and gestures.
            
            3
            00:00:10,500 --> 00:00:15,000
            Swipe up/down on the LEFT side of screen for Brightness.
            
            4
            00:00:15,500 --> 00:00:20,000
            Swipe up/down on the RIGHT side of screen for Volume.
            
            5
            00:00:20,500 --> 00:00:24,000
            Swipe LEFT or RIGHT horizontally to seek back or forward.
            
            6
            00:00:24,500 --> 00:00:28,000
            Try the SUBTITLE DELAY buttons in the control panel!
            
            7
            00:00:28,500 --> 00:00:33,000
            You can advance or delay subtitles by steps of 100 milliseconds.
            
            8
            00:00:33,500 --> 00:00:37,500
            This allows perfect synchronization for local films.
            
            9
            00:00:38,000 --> 00:00:43,000
            Double tap on left/right to skip 10 seconds. Enjoy your movie!
        """.trimIndent()
    }
}
