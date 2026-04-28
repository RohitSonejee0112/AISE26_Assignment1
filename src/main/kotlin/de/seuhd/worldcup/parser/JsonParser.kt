package de.seuhd.worldcup.parser

import kotlinx.serialization.json.Json
import de.seuhd.worldcup.model.WorldCupData

object JsonParser {
    fun load(filename: String = "world_cup_2026_filled_data.json"): WorldCupData {
        val jsonText = object {}.javaClass.getResourceAsStream("/$filename")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not find JSON file '$filename' in resources!")

        return Json { ignoreUnknownKeys = true }
            .decodeFromString(WorldCupData.serializer(), jsonText)
    }
}