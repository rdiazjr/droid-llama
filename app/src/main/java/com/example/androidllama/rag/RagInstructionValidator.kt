package com.example.androidllama.rag

import org.json.JSONException
import org.json.JSONObject

object RagInstructionValidator {
    fun parse(rawJson: String): RagInstruction {
        val json = try {
            JSONObject(rawJson)
        } catch (error: JSONException) {
            throw IllegalArgumentException("The selected file is not valid JSON.", error)
        }

        val instruction = try {
            RagInstruction(
                name = json.getString("name").trim(),
                version = json.optInt("version", 1),
                systemPrompt = json.getString("systemPrompt").trim(),
                noContextResponse = json.optString(
                    "noContextResponse",
                    "The stored documents do not contain enough information."
                ).trim(),
                chunkSize = json.optInt("chunkSize", 700),
                chunkOverlap = json.optInt("chunkOverlap", 100),
                topK = json.optInt("topK", 5),
                minimumScore = json.optDouble("minimumScore", 0.25),
                includeCitations = json.optBoolean("includeCitations", true)
            )
        } catch (error: JSONException) {
            throw IllegalArgumentException(
                "JSON must contain the string fields name and systemPrompt.",
                error
            )
        }

        require(instruction.name.isNotBlank()) { "Instruction name cannot be blank." }
        require(instruction.version == 1) { "Only instruction version 1 is supported." }
        require(instruction.systemPrompt.isNotBlank()) { "systemPrompt cannot be blank." }
        require(instruction.chunkSize in 100..4000) { "chunkSize must be between 100 and 4000." }
        require(instruction.chunkOverlap in 0 until instruction.chunkSize) {
            "chunkOverlap must be smaller than chunkSize."
        }
        require(instruction.topK in 1..50) { "topK must be between 1 and 50." }
        require(instruction.minimumScore in 0.0..1.0) {
            "minimumScore must be between 0 and 1."
        }
        return instruction
    }
}
