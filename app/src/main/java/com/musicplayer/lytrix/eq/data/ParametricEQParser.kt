package com.musicplayer.lytrix.eq.data

import timber.log.Timber

/**
 * Parser and validator for Equalizer APO / AutoEq ParametricEQ.txt files.
 */
object ParametricEQParser {

    private const val TAG = "ParametricEQParser"

    fun parseText(content: String): ParametricEQ {
        val lines = content.lines()
        var preamp = 0.0
        val bands = mutableListOf<ParametricEQBand>()
        val metadata = mutableMapOf<String, String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("Preamp:", ignoreCase = true) -> {
                    preamp = parsePreamp(trimmed)
                }
                trimmed.startsWith("Filter", ignoreCase = true) -> {
                    parseFilterLine(trimmed)?.let { bands.add(it) }
                }
                else -> {
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size == 2) {
                        metadata[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        }

        return ParametricEQ(
            preamp = preamp,
            bands = bands,
            metadata = metadata
        )
    }

    private fun parsePreamp(line: String): Double {
        val regex = Regex("""Preamp:\s*([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun parseFilterLine(line: String): ParametricEQBand? {
        return try {
            if (!line.contains("ON", ignoreCase = true)) return null
            val filterType = parseFilterType(line) ?: return null
            val frequency = parseValue(line, "Fc", "Hz") ?: return null
            val gain = if (filterType == FilterType.LPQ || filterType == FilterType.HPQ) {
                parseValue(line, "Gain", "dB") ?: 0.0
            } else {
                parseValue(line, "Gain", "dB") ?: return null
            }
            val q = parseValue(line, "Q", null) ?: 0.707

            ParametricEQBand(
                frequency = frequency,
                gain = gain,
                q = q,
                filterType = filterType
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse filter line: %s", line)
            null
        }
    }

    private fun parseFilterType(line: String): FilterType? {
        return when {
            line.contains("LSC", ignoreCase = true) -> FilterType.LSC
            line.contains("HSC", ignoreCase = true) -> FilterType.HSC
            line.contains("PK", ignoreCase = true) -> FilterType.PK
            line.contains("LPQ", ignoreCase = true) -> FilterType.LPQ
            line.contains("HPQ", ignoreCase = true) -> FilterType.HPQ
            else -> null
        }
    }

    private fun parseValue(line: String, keyword: String, unit: String?): Double? {
        val unitPattern = if (unit != null) "\\s*$unit" else ""
        val regex = Regex("""$keyword\s+([-+]?\d+\.?\d*)$unitPattern""", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    fun validate(eq: ParametricEQ): List<String> {
        val errors = mutableListOf<String>()

        if (eq.preamp < -50.0 || eq.preamp > 50.0) {
            errors.add("Preamp value ${eq.preamp} dB is out of range (-50 to +50 dB)")
        }
        if (eq.bands.isEmpty()) {
            errors.add("EQ profile must have at least one active band")
        }
        if (eq.bands.size > ParametricEQ.MAX_BANDS) {
            errors.add("EQ profile has ${eq.bands.size} bands, maximum is ${ParametricEQ.MAX_BANDS}")
        }

        eq.bands.forEachIndexed { index, band ->
            if (band.frequency <= 0.0 || band.frequency > 100000.0) {
                errors.add("Band ${index + 1}: Frequency ${band.frequency} Hz is out of range")
            }
            if (band.gain < -30.0 || band.gain > 30.0) {
                errors.add("Band ${index + 1}: Gain ${band.gain} dB is out of range (-30 to +30 dB)")
            }
            if (band.q <= 0.0 || band.q > 20.0) {
                errors.add("Band ${index + 1}: Q factor ${band.q} is out of range (0.01 to 20)")
            }
        }

        return errors
    }
}
