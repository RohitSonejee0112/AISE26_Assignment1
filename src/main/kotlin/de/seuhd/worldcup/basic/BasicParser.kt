package de.seuhd.worldcup.basic

import kotlinx.serialization.json.Json

object BasicParser {
    fun load(filename: String = "world_cup_2026_filled_data.json"): BasicWorldCupData {
        val jsonText = object {}.javaClass.getResourceAsStream("/$filename")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not find JSON file '$filename' in resources!")

        // Need custom parsing because the filled JSON has extra fields (odds) we ignore
        val raw = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(jsonText)

        // Parse manually to strip odds field
        val json = Json { ignoreUnknownKeys = true }

        // Actually, let's use a simpler approach - deserialize with ignoreUnknownKeys
        // The @Serializable classes don't have odds, so they'll be ignored
        return json.decodeFromString(BasicWorldCupData.serializer(), jsonText)
    }
}