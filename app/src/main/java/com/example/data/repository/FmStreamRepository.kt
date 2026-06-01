package com.example.data.repository

import android.util.Log
import com.example.data.model.FmStreamStation
import com.example.data.model.StreamOption
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class FmStreamRepository {

    private fun getSafeString(array: JsonArray, index: Int): String? {
        if (index >= array.size()) return null
        val elem = array.get(index)
        if (elem == null || elem.isJsonNull) return null
        return if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
            elem.asString
        } else {
            elem.toString().replace("\"", "")
        }
    }

    private fun getSafeInt(array: JsonArray, index: Int, default: Int): Int {
        if (index >= array.size()) return default
        val elem = array.get(index)
        if (elem == null || elem.isJsonNull) return default
        if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) return elem.asInt
        return try { elem.asString.toInt() } catch(e: Exception) { default }
    }

    suspend fun searchStations(query: String): List<FmStreamStation> = withContext(Dispatchers.IO) {
        val parsedStations = mutableListOf<FmStreamStation>()
        try {
            val url = if (query.isBlank()) {
                "https://fmstream.org/index.php"
            } else {
                // Safety delay to prevent swift subsequent keystroke request spam
                delay(500L)
                val finalQuery = if (query.endsWith("*")) query else "$query*"
                "https://fmstream.org/index.php?s=$finalQuery"
            }
            Log.d("FmStreamRepository", "Connecting to URL: $url")
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(10000)
                .get()

            val html = document.html()
            Log.d("FmStreamRepository", "Successfully fetched document. HTML length: ${html.length}")

            val stnblocks = document.select("div.stnblock")
            if (stnblocks.isNotEmpty()) {
                Log.d("FmStreamRepository", "Found ${stnblocks.size} stnblock elements in DOM.")
                for (block in stnblocks) {
                    val id = block.attr("data-stnid")
                    val name = block.selectFirst("div.stn-info-col h3")?.text() ?: ""
                    
                    var location: String? = null
                    val infoCol = block.selectFirst("div.stn-info-col")
                    if (infoCol != null) {
                        val nodes = infoCol.childNodes()
                        val textParts = mutableListOf<String>()
                        for (node in nodes) {
                            if (node is org.jsoup.nodes.TextNode) {
                                val txt = node.text().trim()
                                if (txt.isNotEmpty()) {
                                    textParts.add(txt)
                                }
                            } else if (node is org.jsoup.nodes.Element) {
                                if (node.tagName() == "span") {
                                    val txt = node.text().trim()
                                    if (txt.isNotEmpty()) {
                                        textParts.add(txt)
                                    }
                                }
                            }
                        }
                        val locationCandidate = textParts.firstOrNull { it.any { char -> char.isLetter() || char.isDigit() } }
                        if (locationCandidate != null) {
                            location = locationCandidate
                        }
                    }
                    
                    val streamLinks = block.select("div.stn-stream-col a")
                    val streams = streamLinks.mapNotNull { link ->
                        var streamUrl = link.attr("href").trim()
                        if (streamUrl.isBlank() || streamUrl == "#" || streamUrl.startsWith("javascript", ignoreCase = true)) {
                            val onclick = link.attr("onclick")
                            if (onclick.isNotBlank()) {
                                val regex = "https?://[^'\"]+".toRegex()
                                val match = regex.find(onclick)
                                if (match != null) {
                                    streamUrl = match.value
                                } else {
                                    val quotesRegex = "['\"](.*?)['\"]".toRegex()
                                    val quotesMatches = quotesRegex.findAll(onclick).map { it.groupValues[1] }.toList()
                                    val foundUrl = quotesMatches.firstOrNull { it.startsWith("http") || it.contains(".") }
                                    if (foundUrl != null) {
                                        streamUrl = foundUrl
                                    }
                                }
                            }
                        }
                        if (streamUrl.isBlank() || streamUrl == "#" || streamUrl.startsWith("javascript", ignoreCase = true)) {
                            null
                        } else {
                            val text = link.text().trim()
                            StreamOption(bitrate = text, url = streamUrl)
                        }
                    }
                    
                        if (id.isNotBlank() && name.isNotBlank()) {
                            val finalStreams = if (streams.isEmpty()) {
                                listOf(StreamOption(bitrate = "Unknown", url = ""))
                            } else {
                                streams
                            }
                            
                            val firstLinkText = streamLinks.firstOrNull()?.text() ?: ""
                            val codec = if (firstLinkText.contains("aac", ignoreCase = true)) "aac" else "mp3"
                            
                            var imageUrl: String? = null
                            // Safely inspect any anchors for external/homepage links to get a favicon domain
                            val homepageAnchor = block.selectFirst("a[href]")
                            val homepageUrl = homepageAnchor?.attr("href")
                            if (!homepageUrl.isNullOrBlank() && !homepageUrl.startsWith("index.php") && !homepageUrl.startsWith("javascript:")) {
                                val cleanedDomain = homepageUrl.substringAfter("://").substringBefore("/")
                                if (cleanedDomain.isNotBlank() && cleanedDomain.contains(".")) {
                                    imageUrl = "https://www.google.com/s2/favicons?sz=64&domain=$cleanedDomain"
                                }
                            }
                            
                            parsedStations.add(
                                FmStreamStation(
                                    id = id,
                                    name = name,
                                    imageUrl = imageUrl,
                                    streams = finalStreams,
                                    codec = codec,
                                    location = location
                                )
                            )
                        }
                }
            } else {
                Log.d("FmStreamRepository", "No stnblock elements found, falling back to fetchIds/stations2.php endpoint parser.")
                val fetchIdsRegex = """const\s+fetchIds\s*=\s*"([^"]*)"""".toRegex()
                val matchResult = fetchIdsRegex.find(html)
                val fetchIds = matchResult?.groups?.get(1)?.value ?: ""
                Log.d("FmStreamRepository", "Found fetchIds: $fetchIds")

                if (fetchIds.isNotBlank()) {
                    val response = Jsoup.connect("https://fmstream.org/stations2.php")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .data("ids", fetchIds)
                        .data("app", "fmstream")
                        .ignoreContentType(true)
                        .method(org.jsoup.Connection.Method.POST)
                        .timeout(10000)
                        .execute()

                    val jsonBody = response.body()
                    if (!jsonBody.isNullOrBlank()) {
                        val jsonArray = JsonParser.parseString(jsonBody).asJsonArray
                        for (element in jsonArray) {
                            if (!element.isJsonArray) continue
                            val row = element.asJsonArray
                            if (row.size() < 3) continue

                            val id = getSafeString(row, 0) ?: continue
                            val name = getSafeString(row, 1) ?: "Unknown Station"

                            val urlsElement = row.get(2)
                            val urlsArray = if (urlsElement.isJsonArray) {
                                urlsElement.asJsonArray
                            } else if (urlsElement.isJsonNull) {
                                null
                            } else {
                                val urlsStr = urlsElement.asString
                                try {
                                    JsonParser.parseString(urlsStr).asJsonArray
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            val streams = mutableListOf<StreamOption>()
                            var fallbackCodec = "mp3"
                            if (urlsArray != null && urlsArray.size() > 0) {
                                for (urlElem in urlsArray) {
                                    if (!urlElem.isJsonArray) continue
                                    val urlObj = urlElem.asJsonArray
                                    val rawStreamUrl = getSafeString(urlObj, 0) ?: ""
                                    if (rawStreamUrl.isNotBlank()) {
                                        val codec = getSafeString(urlObj, 1) ?: "mp3"
                                        fallbackCodec = codec
                                        val rawBitrate = getSafeInt(urlObj, 2, 0)
                                        val bitrate = if (rawBitrate > 1000) rawBitrate / 1000 else rawBitrate
                                        val displayBitrate = if (bitrate > 0) "$bitrate" else "Live"
                                        streams.add(StreamOption(bitrate = displayBitrate, url = rawStreamUrl))
                                    }
                                }
                            }

                            val finalStreams = if (streams.isEmpty()) {
                                listOf(StreamOption(bitrate = "Unknown", url = ""))
                            } else {
                                streams
                            }

                            val homepage = getSafeString(row, 8)
                            val imageUrl = if (!homepage.isNullOrBlank()) {
                                val cleanedDomain = homepage.substringAfter("://").substringBefore("/")
                                "https://www.google.com/s2/favicons?sz=64&domain=$cleanedDomain"
                            } else {
                                null
                            }

                            val ctry = getSafeString(row, 3)
                            val rgn = getSafeString(row, 4)
                            val cty = getSafeString(row, 5)
                            val locList = mutableListOf<String>()
                            if (!ctry.isNullOrBlank()) locList.add(ctry)
                            if (!cty.isNullOrBlank() && cty != ctry) locList.add(cty)
                            val location = if (locList.isNotEmpty()) locList.joinToString(", ") else null

                            parsedStations.add(
                                FmStreamStation(
                                    id = id,
                                    name = name,
                                    imageUrl = imageUrl,
                                    streams = finalStreams,
                                    codec = fallbackCodec,
                                    location = location
                                )
                            )
                        }
                    }
                }
            }

            if (parsedStations.isEmpty()) {
                Log.d("FmStreamScraper", "HTML length: ${document.html().length}")
                Log.d("FmStreamScraper", "HTML Body Snippet: ${document.body().html().take(1000)}")
            }

        } catch (e: Exception) {
            Log.e("FmStreamRepository", "Error fetching/parsing fmstream data for query: $query", e)
        }
        parsedStations
    }
}
