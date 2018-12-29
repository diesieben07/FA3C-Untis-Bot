package de.takeweiland.untisbot

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.cookies.cookies
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import org.threeten.extra.PackedFields
import java.time.*


class UntisApi(private val baseUrl: String, private val school: String) {

    private val http = HttpClient {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()

        }
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
//        install(Logging) {
//            logger = Logger.SIMPLE
//        }
    }

    var sessionCookieTime: Instant? = null

    private suspend fun obtainSessionCookie() {
        val hasCookie = http.cookies(baseUrl).any { it.name == "JSESSIONID" }
        val sct = sessionCookieTime
        val now = Instant.now()

        if (!hasCookie || sct == null || Duration.between(now, sct).toMinutes() >= 5) {
            http.call {
                url("$baseUrl/")
                parameter("school", school)
            }
            http.call {
                url("$baseUrl/j_spring_security_check")
                method = HttpMethod.Post
                body = FormDataContent(Parameters.build {
                    append("school", school)
                    append("j_username", "schueler")
                    append("j_password", "schueler")
                    append("token", "")
                })
            }
            sessionCookieTime = now
        }
    }

    suspend fun getClassId(cls: String): Int? {
        return allClasses().singleOrNull { it.displayname == cls }?.id
    }

    private suspend fun allClasses(): List<PageConfigDataElement> {
        obtainSessionCookie()

        val now = Instant.now().atZone(ZoneId.of("Europe/Berlin")).toLocalDate()
            .toString()
        val response = http.request<PageConfigResponse> {
            url("$baseUrl/api/public/timetable/weekly/pageconfig")
            parameter("type", "1")
            parameter("date", now)
        }
        return response.data.elements
    }

    suspend fun timeTableEntries(classId: Int, date: LocalDate, mergeEntries: Boolean = false): List<TimeTableEntry> {
        obtainSessionCookie()
        val data = http.request<DataResponse> {
            url("$baseUrl/api/public/timetable/weekly/data")
            parameter("elementType", 1)
            parameter("elementId", classId)
            parameter("date", date.toString())
            parameter("formatId", 1)
        }.data.result.data
        val periods = data.elementPeriods[classId.toString()] ?: emptyList()
        val elements = data.elements.associateBy { it.id }
        val entries = periods
            .filter { it.localDate == date }
            .sortedBy { it.startLocalTime }
            .map { period ->
                val matchElements = period.elements.map { elements[it.id]!! }
                val names = matchElements.mapNotNullTo(HashSet()) { element ->
                    arrayOf(element.displayname, element.longName, element.name).firstOrNull { !it.isNullOrBlank() }
                }
                TimeTableEntry(period.localDate, period.startLocalTime, period.endLocalTime, names)
            }
        return if (mergeEntries) mergeEntries(entries) else entries
    }

    fun mergeEntries(entries: List<TimeTableEntry>): List<TimeTableEntry> {
        if (entries.isEmpty()) {
            return entries
        }

        val result = ArrayList<TimeTableEntry>()
        var current: TimeTableEntry? = null
        var prev: TimeTableEntry? = null
        for (entry in entries) {
            if (current == null) {
                current = entry
            } else {
                if (entry.date != current.date || entry.names != current.names) {
                    result += current.copy(end = prev!!.end)
                    current = entry
                }
            }

            prev = entry
        }
        result += current!!.copy(end = prev!!.end)
        return result
    }

    data class TimeTableEntry(val date: LocalDate, val start: LocalTime, val end: LocalTime, val names: Set<String>)

    data class PageConfigResponse(val data: PageConfigResponseData)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageConfigResponseData(val elements: List<PageConfigDataElement>)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageConfigDataElement(val id: Int, val displayname: String)

    data class DataResponse(val data: DataResponseData)

    data class DataResponseData(val result: DataResponseResultData)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DataResponseResultData(val data: DataResponseResult)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DataResponseResult(val elementPeriods: Map<String, List<ElementPeriod>>, val elements: List<Element>)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ElementPeriod(val id: Int, val date: Int, val startTime: Int, val endTime: Int, val elements: List<ElementPeriodElement>) {
        val localDate: LocalDate = LocalDate.MIN.with(PackedFields.PACKED_DATE, date.toLong())
        val startLocalTime = LocalTime.MIN.with(PackedFields.PACKED_HOUR_MIN, startTime.toLong())
        val endLocalTime = LocalTime.MIN.with(PackedFields.PACKED_HOUR_MIN, endTime.toLong())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ElementPeriodElement(val id: Int)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Element(val type: Int, val id: Int, val name: String?, val longName: String?, val displayname: String?)
}