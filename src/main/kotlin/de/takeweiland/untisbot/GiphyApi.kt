package de.takeweiland.untisbot

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.url

class GiphyApi(private val apiKey: String) {

    private val baseUrl = "https://api.giphy.com/v1"

    private val http = HttpClient {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        followRedirects = false
    }

    suspend fun getRandomGif(): String {
        val response = http.request<JsonNode> {
            url("$baseUrl/gifs/random")
            parameter("api_key", apiKey)
        }
        return response["data"]["images"]["original_mp4"]["mp4"].asText()
    }


}