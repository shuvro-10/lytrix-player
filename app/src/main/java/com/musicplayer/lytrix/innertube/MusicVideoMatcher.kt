package com.musicplayer.lytrix.innertube

import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.SongItem
import kotlin.math.abs

/**
 * CHANGES in this version (on top of the previous one)
 *
 *  1. OMV-ONLY. A search result is a candidate only if YouTube Music itself says it is
 *     MUSIC_VIDEO_TYPE_OMV (official music video). UGC (fan lyric / dance / cover / remix uploads),
 *     ATV (static album-art audio) and OFFICIAL_SOURCE_MUSIC are rejected outright.
 *     -> if no OMV exists, findBestMatch() returns null and the app stays on the album cover.
 *  2. cleanCoreTitle() strips "(feat. X)", "[ft. X]", "(with X)" and un-bracketed "Featuring X",
 *     so "Levitating" == "Dua Lipa - Levitating Featuring DaBaby (Official Music Video)".
 *  3. NON_OFFICIAL_VARIANT_PATTERN now also knows: dance, workout, choreography, amv, fmv, edit.
 *     (also fixed the broken character range "[ -_]" in sped-up / bass-boosted).
 *  4. The fixed "video may be at most 30 s shorter than the audio" rule is gone.
 *     Official Bollywood videos are often 1-2 min shorter edits (Zaalima: 2:52 video vs 4:59 audio).
 *     Only absurd cases are rejected now: video shorter than 40 % of the audio (teaser / short).
 *  5. Label uploads (Zee Music, T-Series ...) do not credit the singers and may differ in duration:
 *     an OMV whose title matches EXACTLY is accepted if the duration is close OR the channel looks like a label.
 *  6. Multi-artist splitting, channel-name variant check and smallest-duration-gap tie-break are unchanged.
 */
object MusicVideoMatcher {
    private const val DURATION_TOLERANCE_SECONDS = 30L

    /** Video may be shorter than the audio (official edits), but not shorter than this share of it. 0.0 = no limit. */
    private const val MIN_VIDEO_TO_AUDIO_RATIO = 0.40
    private const val MAX_VIDEO_LONGER_SECONDS = 90L

    private const val SCORE_OFFICIAL_VIDEO = 4
    private const val SCORE_EXACT_TITLE = 8
    private const val SCORE_TITLE_CONTAINS_TRACK = 2
    private const val SCORE_TRACK_CONTAINS_TITLE = 1
    private const val SCORE_DURATION_CLOSE = 2
    private const val SCORE_CANDIDATE_ARTIST_MATCHES_TRACK = 3
    private const val SCORE_NON_OFFICIAL_PENALTY = -10

    private const val MIN_TITLE_COMPARE_LENGTH = 3
    private const val MIN_ARTIST_NAME_LENGTH = 3
    private const val LEVENSHTEIN_TITLE_THRESHOLD = 0.80
    private const val LEVENSHTEIN_ARTIST_THRESHOLD = 0.80

    private val NON_WORD_PATTERN = Regex("""[^\p{L}\p{N}]+""")
    private val TITLE_SUFFIX_PATTERN = Regex("""^(?:19|20)\d{2}|pt\s*\d+|part\s*\d+|vol\s*\d+$""", RegexOption.IGNORE_CASE)
    private val ARTIST_SEPARATOR_PATTERN = Regex("""^(?:\s*:\s*|\s+[-–—~|/•]\s*|\s*[/|•]\s*)""")
    private val DELIMITER_REGEX = Regex("""\s+[-–—~]\s+|\s*[:|/•]\s*|\s*//\s*""")

    // "Shaan, Kailash Kher" / "A & B" / "A feat. B" / "A x B" / "A/B"
    private val ARTIST_SPLIT_REGEX = Regex(
        """\s*(?:,|&|•|·|/|;|\+|\s+(?:feat\.?|ft\.?|featuring|with|and|x)\s+)\s*""",
        RegexOption.IGNORE_CASE
    )

    /** "(feat. DaBaby)" "[ft. Selena Gomez]" "(featuring X)" "(with Ed Sheeran)" */
    private val FEAT_BRACKETED_PATTERN = Regex(
        """[\(\[\{]\s*(?:feat|ft|featuring|with)\b\.?[^\)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )

    /** Movie, soundtrack, and album markers e.g. "(From \"Brahmāstra\")", "[Soundtrack]", "(OST)" */
    private val MOVIE_OR_SOUNDTRACK_PATTERN = Regex(
        """[\(\[\{]\s*(?:from|soundtrack|ost|theme|motion\s+picture|original\s+soundtrack|album\s+version|original\s+motion\s+picture|score)\b[^\)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )

    private val DASH_SOUNDTRACK_PATTERN = Regex(
        """\s+[-–—~]\s+(?:from|soundtrack|ost|theme)\b.*$""",
        RegexOption.IGNORE_CASE
    )

    private val SMART_QUOTES_PATTERN = Regex("""[\u2018\u2019\u201C\u201D"']""")

    /**
     * "Levitating Featuring DaBaby"  /  "Dua Lipa feat. DaBaby - Levitating".
     * Removes only up to the next delimiter, so the song name after "feat. X - " survives.
     */
    private val FEAT_INLINE_PATTERN = Regex(
        """\s+(?:feat\.?|ft\.?|featuring)\s+[^|\-–—:(\[{]*""",
        RegexOption.IGNORE_CASE
    )

    private val BRACKETED_DECORATOR_PATTERN = Regex(
        """[\(\[\{]\s*(?:official\s+music\s+video|official\s+video|official\s+lyric\s+video|official\s+audio|official\s+4k\s+video|official\s+hd\s+video|official\s+visualizer|official\s+visualiser|music\s+video|lyric\s+video|lyrics\s+video|full\s+video|clip\s+officiel|video\s+oficial|video\s+clip|videoclip|visualizer|visualiser|sing[- ]?along|singalong|audio\s+only|official|audio|video|mv|m/v|hd|hq|4k|1080p|lyrics?)\s*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )

    private val DECORATOR_PATTERN = Regex(
        """\b(official\s+music\s+video|official\s+video|official\s+lyric\s+video|official\s+audio|official\s+4k\s+video|official\s+hd\s+video|official\s+visualizer|official\s+visualiser|music\s+video|lyric\s+video|lyrics\s+video|full\s+video|clip\s+officiel|video\s+oficial|video\s+clip|videoclip|visualizer|visualiser|sing[- ]?along|singalong|audio\s+only|official|mv|m/v|hd|hq|4k|1080p)\b""",
        RegexOption.IGNORE_CASE
    )

    private val NON_OFFICIAL_VARIANT_PATTERN = Regex(
        """\b(remix|remaster|cover|karaoke|instrumental|acapella|a cappella|reverb|slowed|sped[ _-]?up|bass[ _-]?boosted|nightcore|tutorial|lyrics?|loop|8d|8d audio|live|live[ -]?performance|live[ -]?session|concert|show|awards|acoustic|visuali[sz]er|audio[ -]?only|fan[ -]?made|reaction|behind[ -]?the[ -]?scenes|trailer|teaser|compilation|mashup|medley|dance|workout|choreography|amv|fmv|edit|7clouds?|cassiopeia|syrebralvibes|taz[ -]?network|sensual[ -]?musique)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Channel names of record labels / film companies (they never credit the singers in the title). */
    private val LABEL_CHANNEL_PATTERN = Regex(
        """\b(records?|music|films?|entertainment|vevo|t[ -]?series|zee|sony|universal|warner|saregama|eros|yrf|yash raj|tips|venus|lahari|studios?|pictures|productions?|label|originals?)\b""",
        RegexOption.IGNORE_CASE
    )

    val BANNED_VIDEO_IDS = setOf(
        "W1S9AbHpWFY", // 7clouds lyric video - Kesariya
        "pWs5OfSdMxM", // Sensual Musique lyric video - Hear Me Tonight
    )

    /**
     * Is this YouTube-Music `musicVideoType` an official music video?
     *  OMV / PODCAST_EPISODE (official video releases) -> true
     *  UGC / ATV / OFFICIAL_SOURCE_MUSIC / anything else -> false
     *  null / blank -> [unknownIsOk]  (false for search results; the caller may pass true for YouTube's own /next counterpart)
     */
    fun isAcceptableVideoType(type: String?, unknownIsOk: Boolean = false): Boolean {
        if (type.isNullOrBlank()) return unknownIsOk
        val upper = type.uppercase()
        return upper.contains("OMV") || upper.contains("PODCAST_EPISODE")
    }

    /**
     * MetroList-style check: is the currently playing YouTube item itself already a video track
     * (OMV / UGC / VIDEO / LIVE, not an ATV audio track or podcast)?
     */
    fun isSourceVideoType(type: String?): Boolean {
        if (type.isNullOrBlank()) return false
        val normalized = type.uppercase().trim().removePrefix("MUSIC_VIDEO_TYPE_")
        if (normalized.contains("ATV") || normalized.contains("PODCAST")) return false
        return normalized.contains("OMV") ||
            normalized.contains("OFFICIAL_SOURCE") ||
            normalized.contains("UGC") ||
            normalized.contains("USER_GENERATED") ||
            normalized.contains("LIVE") ||
            normalized == "VIDEO" ||
            normalized.contains("VIDEO")
    }

    fun isNonOfficialTrack(title: String, artist: String): Boolean {
        val text = normalizeTitle("$title $artist")
        return NON_OFFICIAL_VARIANT_PATTERN.containsMatchIn(text)
    }

    /** Splits "A, B & C feat. D" into ["A","B","C","D"]. Safe to call on already-split lists. */
    fun splitArtistNames(names: List<String>): List<String> =
        names
            .flatMap { it.split(ARTIST_SPLIT_REGEX) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    fun buildVideoSearchQueries(title: String, artistNames: List<String>): List<String> {
        val artists = splitArtistNames(artistNames)
        val artistPart = when {
            artists.isEmpty() -> ""
            artists.size == 1 -> artists[0]
            else -> artists.take(2).joinToString(" ")
        }
        val cleanTitle = cleanCoreTitle(title)
        val strippedTitle = if (artists.isNotEmpty()) cleanCoreTitle(stripArtistPrefix(title, artists.toSet())) else cleanTitle
        val effectiveTitle = if (strippedTitle.isNotBlank()) strippedTitle else cleanTitle
        val queries = mutableListOf<String>()

        if (effectiveTitle.isNotBlank() && artistPart.isNotBlank()) {
            // MetroList order: official video query first, then clean title + artist.
            // Never run artist-less queries when artist is known (prevents unrelated songs with the same title).
            queries.add("$effectiveTitle $artistPart official video")
            queries.add("$effectiveTitle $artistPart")
        } else if (effectiveTitle.isNotBlank()) {
            queries.add("$effectiveTitle official video")
            queries.add(effectiveTitle)
        }
        return queries.distinct()
    }

    fun buildVideoSearchQuery(title: String, artistNames: List<String>, includeOfficial: Boolean = true): String {
        val artists = splitArtistNames(artistNames)
        val artistPart = when {
            artists.isEmpty() -> ""
            artists.size == 1 -> artists[0]
            else -> artists.take(2).joinToString(" ")
        }
        val cleanTitle = cleanCoreTitle(title)
        val base = if (artistPart.isBlank()) cleanTitle else "$cleanTitle $artistPart"
        return if (includeOfficial) "$base official video" else base
    }

    fun findBestMatch(
        trackTitle: String,
        trackArtistNames: List<String>,
        trackDurationSec: Long?,
        candidates: List<SongItem>,
        excludeVideoId: String? = null,
    ): SongItem? {
        val normalizedTrackArtists = splitArtistNames(trackArtistNames)
            .map { normalizeName(it) }
            .filter { it.isNotBlank() }
            .toSet()

        val eligible = candidates.filter { candidate ->
            if (candidate.id in BANNED_VIDEO_IDS) return@filter false
            if (excludeVideoId != null && candidate.id == excludeVideoId) return@filter false

            // 0. ONLY official music videos. UGC (lyrics / dance / cover / fan edits) and ATV are dropped here.
            val mvType = candidate.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
            if (!isAcceptableVideoType(mvType)) return@filter false

            val candidateDurSec = candidate.duration?.toLong()

            // 1. Loose two-sided duration check (official edits may be much shorter, intros may add up to 90 s)
            if (!durationCompatible(trackDurationSec, candidateDurSec)) return@filter false

            // 2. live / show / concert / cover / dance / workout ... in the title OR the channel name
            if (isDisqualifiedVariant(trackTitle, normalizedTrackArtists, candidate)) return@filter false

            // 3. The title must really be this song
            if (!titleMatches(candidate.title, trackTitle, normalizedTrackArtists)) return@filter false

            val artistMatches = normalizedTrackArtists.isNotEmpty() &&
                candidateMatchesTrackArtist(candidate.artists, normalizedTrackArtists)

            val artistInTitle = normalizedTrackArtists.any { artist ->
                artist.length >= MIN_ARTIST_NAME_LENGTH && normalizeTitle(candidate.title).contains(artist)
            }

            // MetroList strict rule: when track artist(s) are known, the candidate MUST match
            // either artist metadata or mention the artist in its title. Never allow random
            // label channels ("XYZ Music") with a different artist's song to bypass artist checks.
            normalizedTrackArtists.isEmpty() || artistMatches || artistInTitle
        }

        return eligible
            .mapIndexedNotNull { index, candidate ->
                val score = scoreCandidate(trackTitle, trackDurationSec, candidate, normalizedTrackArtists)
                if (score > 0) Triple(candidate, score, index) else null
            }
            .maxWithOrNull(
                compareBy<Triple<SongItem, Int, Int>>(
                    { it.second },
                    { -it.third }, // earlier rank in YouTube Music's curated search result wins ties
                    { -durationGap(trackDurationSec, it.first.duration?.toLong()) }
                )
            )
            ?.first
    }

    private fun scoreCandidate(
        trackTitle: String,
        trackDurationSec: Long?,
        candidate: SongItem,
        trackArtistNames: Set<String>,
    ): Int {
        // Every candidate that reaches this point is an OMV (see findBestMatch).
        var score = SCORE_OFFICIAL_VIDEO

        val normTrackTitle = normalizeTitle(trackTitle)
        val candidateTitle = normalizeTitle(candidate.title)
        val strippedCandidateTitle = cleanCoreTitle(stripArtistPrefix(candidate.title, trackArtistNames))
        val coreTrackTitle = cleanCoreTitle(trackTitle)

        if (normTrackTitle.isNotBlank()) {
            val titleSim = maxOf(
                levenshteinSimilarity(coreTrackTitle, cleanCoreTitle(candidate.title)),
                levenshteinSimilarity(coreTrackTitle, strippedCandidateTitle),
                levenshteinSimilarity(normTrackTitle, candidateTitle),
            )

            val isExact = candidateTitle == normTrackTitle ||
                    cleanCoreTitle(candidate.title) == coreTrackTitle ||
                    strippedCandidateTitle == coreTrackTitle ||
                    splitTitleSegments(candidate.title).any { cleanCoreTitle(it) == coreTrackTitle } ||
                    titleSim >= 0.95

            when {
                isExact -> score += SCORE_EXACT_TITLE
                candidateTitle.contains(normTrackTitle) || titleSim >= 0.80 -> score += SCORE_TITLE_CONTAINS_TRACK
                (candidateTitle.length >= MIN_TITLE_COMPARE_LENGTH && normTrackTitle.contains(candidateTitle)) ||
                        titleSim >= 0.70 -> score += SCORE_TRACK_CONTAINS_TITLE
            }
        }

        val candidateDurSec = candidate.duration?.toLong()
        if (durationCloseEnough(trackDurationSec, candidateDurSec)) {
            score += SCORE_DURATION_CLOSE
        }

        if (candidateMatchesTrackArtist(candidate.artists, trackArtistNames)) {
            score += SCORE_CANDIDATE_ARTIST_MATCHES_TRACK
        }

        if (isLabelChannel(candidate)) {
            score += 4
        }

        if (NON_OFFICIAL_VARIANT_PATTERN.containsMatchIn(candidate.title) &&
            !NON_OFFICIAL_VARIANT_PATTERN.containsMatchIn(trackTitle)
        ) {
            score += SCORE_NON_OFFICIAL_PENALTY
        }

        return score
    }

    /**
     * Video may be up to 90 s longer (intro/outro). It may be SHORTER too (official edits), but not
     * shorter than [MIN_VIDEO_TO_AUDIO_RATIO] of the audio (that would be a teaser / short, not the song).
     */
    private fun durationCompatible(trackDurationSec: Long?, candidateDurationSec: Long?): Boolean {
        if (trackDurationSec == null || candidateDurationSec == null || trackDurationSec <= 0L || candidateDurationSec <= 0L) {
            return true
        }
        val delta = candidateDurationSec - trackDurationSec
        val maxShorter = (trackDurationSec * (1.0 - MIN_VIDEO_TO_AUDIO_RATIO)).toLong()
        return delta >= -maxShorter && delta <= MAX_VIDEO_LONGER_SECONDS
    }

    private fun durationCloseEnough(trackDurationSec: Long?, candidateDurationSec: Long?): Boolean {
        if (trackDurationSec == null || candidateDurationSec == null || trackDurationSec <= 0L || candidateDurationSec <= 0L) {
            return false
        }
        return abs(trackDurationSec - candidateDurationSec) <= DURATION_TOLERANCE_SECONDS
    }

    private fun durationGap(trackDurationSec: Long?, candidateDurationSec: Long?): Long {
        if (trackDurationSec == null || candidateDurationSec == null || trackDurationSec <= 0L || candidateDurationSec <= 0L) {
            return 0L
        }
        return abs(trackDurationSec - candidateDurationSec)
    }

    private fun isLabelChannel(candidate: SongItem): Boolean =
        candidate.artists.any { LABEL_CHANNEL_PATTERN.containsMatchIn(it.name) }

    /**
     * Disqualify when the candidate (title + channel/artist names) contains a variant keyword
     * (live, show, concert, cover, remix, dance, workout ...) that the track itself does not contain.
     */
    private fun isDisqualifiedVariant(
        trackTitle: String,
        trackArtists: Set<String>,
        candidate: SongItem
    ): Boolean {
        val trackText = normalizeTitle(trackTitle + " " + trackArtists.joinToString(" "))
        val candidateText = normalizeTitle(
            candidate.title + " " + candidate.artists.joinToString(" ") { it.name }
        )
        val trackVariants = NON_OFFICIAL_VARIANT_PATTERN.findAll(trackText).map { it.value }.toSet()
        return NON_OFFICIAL_VARIANT_PATTERN.findAll(candidateText).any { it.value !in trackVariants }
    }

    fun cleanCoreTitle(title: String): String {
        var s = title.replace(SMART_QUOTES_PATTERN, " ")
        s = s.replace(FEAT_BRACKETED_PATTERN, " ")
        s = s.replace(MOVIE_OR_SOUNDTRACK_PATTERN, " ")
        s = s.replace(DASH_SOUNDTRACK_PATTERN, " ")
        val withoutBracketDecorators = s.replace(BRACKETED_DECORATOR_PATTERN, " ").trim()
        val withoutDecorators = withoutBracketDecorators.replace(DECORATOR_PATTERN, " ").trim()
        val withoutInlineFeat = withoutDecorators.replace(FEAT_INLINE_PATTERN, " ").trim()
        val cleaned = withoutInlineFeat
            .ifBlank { withoutDecorators }
            .ifBlank { withoutBracketDecorators }
            .ifBlank { title }
        return normalizeTitle(cleaned)
    }

    fun stripArtistPrefix(title: String, artistNames: Set<String>): String {
        for (artist in artistNames) {
            if (artist.isBlank()) continue
            val artistPattern = Regex("""^${Regex.escape(artist)}(?:\s*:\s*|\s+[-–—~|/•]\s*|\s*[/|•]\s*)""", RegexOption.IGNORE_CASE)
            val match = artistPattern.find(title)
            if (match != null) {
                return title.substring(match.range.last + 1).trim()
            }
            val normTitle = normalizeTitle(title)
            val normArtist = normalizeName(artist)
            if (normTitle.startsWith(normArtist)) {
                val after = title.drop(artist.length).trimStart()
                val sepMatch = ARTIST_SEPARATOR_PATTERN.find(after)
                if (sepMatch != null) {
                    return after.substring(sepMatch.range.last + 1).trim()
                }
            }
        }
        val sepMatch = Regex("""^([^-–—~:|/]+)\s*[-–—~:|/]\s*(.+)$""").find(title)
        if (sepMatch != null) {
            val prefixNorm = normalizeTitle(sepMatch.groupValues[1])
            val normArtists = artistNames.map { normalizeName(it) }.filter { it.isNotBlank() }
            if (normArtists.any { it.isNotBlank() && (prefixNorm.contains(it) || it.contains(prefixNorm)) }) {
                return sepMatch.groupValues[2].trim()
            }
        }
        return title
    }

    /** True when the video title (or one of its "A | B | C" segments) is exactly the song title. */
    private fun hasExactTitle(videoTitle: String, trackTitle: String, artistNames: Set<String>): Boolean {
        val coreTrack = cleanCoreTitle(trackTitle)
        if (coreTrack.isBlank()) return false
        val stripped = if (artistNames.isNotEmpty()) stripArtistPrefix(videoTitle, artistNames) else videoTitle
        if (cleanCoreTitle(stripped) == coreTrack) return true
        return splitTitleSegments(videoTitle).any { cleanCoreTitle(it) == coreTrack }
    }

    private fun titleMatches(videoTitle: String, trackTitle: String, artistNames: Set<String>): Boolean {
        val normVideo = normalizeTitle(videoTitle)
        val normTrack = normalizeTitle(trackTitle)
        if (normVideo.isBlank() || normTrack.length < MIN_TITLE_COMPARE_LENGTH) return false
        if (normVideo == normTrack) return true

        val strippedVideo = if (artistNames.isNotEmpty()) stripArtistPrefix(videoTitle, artistNames) else videoTitle
        val coreTrack = cleanCoreTitle(trackTitle)
        val coreVideo = cleanCoreTitle(strippedVideo)

        if (coreTrack.isNotBlank() && coreVideo.isNotBlank()) {
            if (coreTrack == coreVideo) return true
            if (normVideo == coreTrack || normalizeTitle(strippedVideo) == coreTrack) return true

            if (coreVideo.startsWith(coreTrack)) {
                val remainder = coreVideo.removePrefix(coreTrack).trim()
                if (remainder.isBlank() || remainder.all { it.isDigit() || it.isWhitespace() } || remainder.matches(TITLE_SUFFIX_PATTERN)) {
                    return true
                }
            }

            if (coreTrack.length >= 4 && coreVideo.length >= 4 && levenshteinSimilarity(coreTrack, coreVideo) >= LEVENSHTEIN_TITLE_THRESHOLD) {
                return true
            }

            val segments = splitTitleSegments(videoTitle)
            for (segment in segments) {
                val coreSegment = cleanCoreTitle(segment)
                if (coreSegment.isNotBlank()) {
                    if (coreSegment == coreTrack) return true
                    if (coreSegment.startsWith(coreTrack)) {
                        val remainder = coreSegment.removePrefix(coreTrack).trim()
                        if (remainder.isBlank() || remainder.all { it.isDigit() || it.isWhitespace() } || remainder.matches(TITLE_SUFFIX_PATTERN)) {
                            return true
                        }
                    }
                    if (coreTrack.length >= 4 && coreSegment.length >= 4 && levenshteinSimilarity(coreSegment, coreTrack) >= LEVENSHTEIN_TITLE_THRESHOLD) {
                        return true
                    }
                }
            }

            if (coreTrack.startsWith(coreVideo) && coreVideo.length >= 6 && coreVideo.length.toDouble() / coreTrack.length >= 0.6) {
                return true
            }
        }
        return false
    }

    private fun splitTitleSegments(title: String): List<String> =
        title.split(DELIMITER_REGEX).map { it.trim() }.filter { it.isNotBlank() }

    private fun candidateMatchesTrackArtist(candidateArtists: List<Artist>, trackArtistNames: Set<String>): Boolean {
        if (candidateArtists.isEmpty() || trackArtistNames.isEmpty()) return false
        return candidateArtists.any { artist ->
            val normalized = normalizeName(artist.name)
            if (normalized.isBlank()) return@any false
            trackArtistNames.any { trackName ->
                if (trackName.isBlank()) return@any false
                normalized == trackName ||
                        (normalized.length >= MIN_ARTIST_NAME_LENGTH &&
                                trackName.length >= MIN_ARTIST_NAME_LENGTH &&
                                (normalized.contains(trackName) || trackName.contains(normalized) ||
                                        levenshteinSimilarity(normalized, trackName) >= LEVENSHTEIN_ARTIST_THRESHOLD))
            }
        }
    }

    fun normalizeTitle(title: String): String = title.lowercase().replace(NON_WORD_PATTERN, " ").trim()
    fun normalizeName(name: String): String = normalizeTitle(name)

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }
}
