package com.averykarlin.averytask.domain.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
<<<<<<< HEAD
=======
import java.time.Month
>>>>>>> a2293f05d619015c47d595dca2556c3e2fc16dae
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTask(
    val title: String,
    val dueDate: Long? = null,
    val dueTime: Long? = null,
    val tags: List<String> = emptyList(),
    val projectName: String? = null,
    val priority: Int = 0,
    val recurrenceHint: String? = null
)

@Singleton
class NaturalLanguageParser @Inject constructor() {

<<<<<<< HEAD
    fun parse(input: String): ParsedTask {
        var remaining = input.trim()
        if (remaining.isEmpty()) return ParsedTask(title = "")

        val tags = mutableListOf<String>()
        val extractedProject: String?
        val extractedPriority: Int
        val extractedRecurrence: String?
        val extractedDate: LocalDate?
        val extractedTime: LocalTime?

        // 1. Tags — #word (must be at start or preceded by whitespace)
        val tagPattern = Regex("""(?:^|\s)#(\w+)""")
        tags.addAll(tagPattern.findAll(remaining).map { it.groupValues[1] })
        remaining = tagPattern.replace(remaining, " ")

        // 2. Projects — @word (must be at start or preceded by whitespace, not an email)
        val projectPattern = Regex("""(?:^|\s)@(\w+)""")
        val projectMatches = projectPattern.findAll(remaining).toList()
        // Filter out email-like patterns: check that the char before @ is not alphanumeric
        val validProjectMatches = projectMatches.filter { match ->
            val fullMatchStart = match.range.first
            if (fullMatchStart > 0) {
                val charBefore = remaining[fullMatchStart]
                // If the match starts with whitespace, the @ is at fullMatchStart+1
                // Check if there's an alphanumeric char directly before the @
                val atIndex = remaining.indexOf('@', fullMatchStart)
                atIndex == 0 || atIndex > 0 && !remaining[atIndex - 1].isLetterOrDigit()
            } else {
                true
            }
        }
        extractedProject = validProjectMatches.firstOrNull()?.groupValues?.get(1)
        if (extractedProject != null) {
            // Only remove the first valid project match
            val match = validProjectMatches.first()
            remaining = remaining.removeRange(match.range)
        }

        // 3. Priority — !urgent, !high, !medium, !med, !low, !!!!, !!!, !!, !, !4, !3, !2, !1
        extractedPriority = extractPriority(remaining)
        remaining = removePriorityToken(remaining)

        // 4. Recurrence hints
        extractedRecurrence = extractRecurrence(remaining)
        remaining = removeRecurrenceToken(remaining)

        // 5. Date/time parsing
        val (date, timeVal, dateTimeRemaining) = extractDateTime(remaining)
        extractedDate = date
        extractedTime = timeVal
        remaining = dateTimeRemaining

        // 6. Clean up title
        remaining = remaining.replace(Regex("""\s{2,}"""), " ").trim()
        val title = if (remaining.isNotEmpty()) {
            remaining[0].uppercaseChar() + remaining.substring(1)
        } else {
            remaining
        }

        val dueDateMillis = extractedDate?.let {
            it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val dueTimeMillis = extractedTime?.let {
            val baseDate = extractedDate ?: LocalDate.now()
            baseDate.atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
=======
    private val zone: ZoneId = ZoneId.systemDefault()

    fun parse(input: String): ParsedTask {
        var text = input
        val today = LocalDate.now()

        // 1. Tags — #word but not C# (must be preceded by space or at start)
        val tags = mutableListOf<String>()
        val tagRegex = Regex("""(?:^|(?<=\s))#(\w+)""")
        tags.addAll(tagRegex.findAll(text).map { it.groupValues[1] })
        text = tagRegex.replace(text, "")

        // 2. Projects — @word but not emails (@ must be preceded by space or at start)
        var projectName: String? = null
        val projectRegex = Regex("""(?:^|(?<=\s))@(\w+)""")
        val projectMatch = projectRegex.find(text)
        if (projectMatch != null) {
            projectName = projectMatch.groupValues[1]
            text = projectRegex.replace(text, "")
        }

        // 3. Priority — use space/start boundary since \b doesn't work before !
        var priority = 0
        val priorityPatterns = listOf(
            Regex("""(?i)(?:^|(?<=\s))!urgent(?:\s|$)""") to 4,
            Regex("""(?:^|(?<=\s))!{4}(?:\s|$)""") to 4,
            Regex("""(?:^|(?<=\s))!4(?:\s|$)""") to 4,
            Regex("""(?i)(?:^|(?<=\s))!high(?:\s|$)""") to 3,
            Regex("""(?:^|(?<=\s))!{3}(?!!)(?:\s|$)""") to 3,
            Regex("""(?:^|(?<=\s))!3(?:\s|$)""") to 3,
            Regex("""(?i)(?:^|(?<=\s))!med(?:ium)?(?:\s|$)""") to 2,
            Regex("""(?:^|(?<=\s))!{2}(?!!)(?:\s|$)""") to 2,
            Regex("""(?:^|(?<=\s))!2(?:\s|$)""") to 2,
            Regex("""(?i)(?:^|(?<=\s))!low(?:\s|$)""") to 1,
            Regex("""(?:^|(?<=\s))!1(?:\s|$)""") to 1,
            Regex("""(?:^|(?<=\s))!(?!\w|!)(?:\s|$)""") to 1,
        )
        for ((regex, level) in priorityPatterns) {
            if (regex.containsMatchIn(text)) {
                priority = level
                text = regex.replace(text, "")
                break
            }
        }

        // 4. Recurrence hints (before date parsing to avoid conflicts)
        var recurrenceHint: String? = null
        val recurrencePatterns = listOf(
            Regex("""(?i)\bevery\s+day\b""") to "daily",
            Regex("""(?i)\bdaily\b""") to "daily",
            Regex("""(?i)\bevery\s+week\b""") to "weekly",
            Regex("""(?i)\bweekly\b""") to "weekly",
            Regex("""(?i)\bevery\s+month\b""") to "monthly",
            Regex("""(?i)\bmonthly\b""") to "monthly",
            Regex("""(?i)\bevery\s+year\b""") to "yearly",
            Regex("""(?i)\byearly\b""") to "yearly",
        )
        for ((regex, hint) in recurrencePatterns) {
            if (regex.containsMatchIn(text)) {
                recurrenceHint = hint
                text = regex.replace(text, "")
                break
            }
        }

        // 5. Time parsing
        var parsedTime: LocalTime? = null

        // "at noon"
        val noonRegex = Regex("""(?i)\bat\s+noon\b""")
        if (noonRegex.containsMatchIn(text)) {
            parsedTime = LocalTime.NOON
            text = noonRegex.replace(text, "")
        }
        // "at midnight"
        val midnightRegex = Regex("""(?i)\bat\s+midnight\b""")
        if (parsedTime == null && midnightRegex.containsMatchIn(text)) {
            parsedTime = LocalTime.MIDNIGHT
            text = midnightRegex.replace(text, "")
        }
        // "at 3pm", "at 3:30pm", "at 15:00"
        val timeRegex = Regex("""(?i)\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")
        if (parsedTime == null) {
            val timeMatch = timeRegex.find(text)
            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toInt()
                val minute = timeMatch.groupValues[2].ifEmpty { "0" }.toInt()
                val ampm = timeMatch.groupValues[3].lowercase()
                when {
                    ampm == "pm" && hour < 12 -> hour += 12
                    ampm == "am" && hour == 12 -> hour = 0
                }
                parsedTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                text = text.removeRange(timeMatch.range)
            }
        }

        // 6. Date parsing
        var parsedDate: LocalDate? = null

        // "today"
        val todayRegex = Regex("""(?i)\btoday\b""")
        if (todayRegex.containsMatchIn(text)) {
            parsedDate = today
            text = todayRegex.replace(text, "")
        }

        // "tomorrow" / "tmrw"
        if (parsedDate == null) {
            val tomorrowRegex = Regex("""(?i)\b(?:tomorrow|tmrw)\b""")
            if (tomorrowRegex.containsMatchIn(text)) {
                parsedDate = today.plusDays(1)
                text = tomorrowRegex.replace(text, "")
            }
        }

        // "next monday", "next tue", etc.
        if (parsedDate == null) {
            val nextDayRegex = Regex("""(?i)\bnext\s+(${dayNames()})\b""")
            val nextDayMatch = nextDayRegex.find(text)
            if (nextDayMatch != null) {
                val dow = parseDayOfWeek(nextDayMatch.groupValues[1])
                if (dow != null) {
                    // "next X" means the occurrence at least 7 days out
                    parsedDate = today.with(TemporalAdjusters.next(dow))
                    if (parsedDate!!.isBefore(today.plusDays(7))) {
                        parsedDate = parsedDate!!.plusWeeks(1)
                    }
                    text = text.removeRange(nextDayMatch.range)
                }
            }
        }

        // "in N days/weeks/months"
        if (parsedDate == null) {
            val relativeRegex = Regex("""(?i)\bin\s+(\d+)\s+(days?|weeks?|months?)\b""")
            val relMatch = relativeRegex.find(text)
            if (relMatch != null) {
                val n = relMatch.groupValues[1].toLong()
                val unit = relMatch.groupValues[2].lowercase()
                parsedDate = when {
                    unit.startsWith("day") -> today.plusDays(n)
                    unit.startsWith("week") -> today.plusWeeks(n)
                    unit.startsWith("month") -> today.plusMonths(n)
                    else -> null
                }
                text = text.removeRange(relMatch.range)
            }
        }

        // "on the 1st" / "on the 15th"
        if (parsedDate == null) {
            val ordinalRegex = Regex("""(?i)\bon\s+the\s+(\d{1,2})(?:st|nd|rd|th)\b""")
            val ordinalMatch = ordinalRegex.find(text)
            if (ordinalMatch != null) {
                val dayOfMonth = ordinalMatch.groupValues[1].toInt()
                if (dayOfMonth in 1..31) {
                    parsedDate = if (today.dayOfMonth < dayOfMonth) {
                        today.withDayOfMonth(dayOfMonth.coerceAtMost(today.lengthOfMonth()))
                    } else {
                        today.plusMonths(1).withDayOfMonth(dayOfMonth.coerceAtMost(today.plusMonths(1).lengthOfMonth()))
                    }
                    text = text.removeRange(ordinalMatch.range)
                }
            }
        }

        // Day name without "next": "monday", "tue", etc.
        if (parsedDate == null) {
            val dayRegex = Regex("""(?i)\b(${dayNames()})\b""")
            val dayMatch = dayRegex.find(text)
            if (dayMatch != null) {
                val dow = parseDayOfWeek(dayMatch.groupValues[1])
                if (dow != null) {
                    parsedDate = today.with(TemporalAdjusters.nextOrSame(dow))
                    if (parsedDate == today) {
                        parsedDate = today.with(TemporalAdjusters.next(dow))
                    }
                    text = text.removeRange(dayMatch.range)
                }
            }
        }

        // Absolute: "jan 15", "march 3", "december 25"
        if (parsedDate == null) {
            val monthDayRegex = Regex("""(?i)\b(${monthNames()})\s+(\d{1,2})\b""")
            val monthDayMatch = monthDayRegex.find(text)
            if (monthDayMatch != null) {
                val month = parseMonth(monthDayMatch.groupValues[1])
                val day = monthDayMatch.groupValues[2].toInt()
                if (month != null && day in 1..31) {
                    var date = LocalDate.of(today.year, month, day.coerceAtMost(month.length(today.isLeapYear)))
                    if (date.isBefore(today)) {
                        date = date.plusYears(1)
                    }
                    parsedDate = date
                    text = text.removeRange(monthDayMatch.range)
                }
            }
        }

        // Slash date: "5/20", "12/25"
        if (parsedDate == null) {
            val slashRegex = Regex("""\b(\d{1,2})/(\d{1,2})\b""")
            val slashMatch = slashRegex.find(text)
            if (slashMatch != null) {
                val m = slashMatch.groupValues[1].toInt()
                val d = slashMatch.groupValues[2].toInt()
                if (m in 1..12 && d in 1..31) {
                    val month = Month.of(m)
                    var date = LocalDate.of(today.year, month, d.coerceAtMost(month.length(today.isLeapYear)))
                    if (date.isBefore(today)) {
                        date = date.plusYears(1)
                    }
                    parsedDate = date
                    text = text.removeRange(slashMatch.range)
                }
            }
        }

        // ISO date: "2026-05-15"
        if (parsedDate == null) {
            val isoRegex = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
            val isoMatch = isoRegex.find(text)
            if (isoMatch != null) {
                try {
                    parsedDate = LocalDate.of(
                        isoMatch.groupValues[1].toInt(),
                        isoMatch.groupValues[2].toInt(),
                        isoMatch.groupValues[3].toInt()
                    )
                    text = text.removeRange(isoMatch.range)
                } catch (_: Exception) { }
            }
        }

        // If time parsed but no date, default to today
        if (parsedTime != null && parsedDate == null) {
            parsedDate = today
        }

        // Convert to epoch millis
        val dueDateMillis = parsedDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val dueTimeMillis = parsedTime?.let { time ->
            val date = parsedDate ?: today
            date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }

        // 7. Clean up title
        val title = text.trim().replace(Regex("""\s{2,}"""), " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
>>>>>>> a2293f05d619015c47d595dca2556c3e2fc16dae
        }

        return ParsedTask(
            title = title,
            dueDate = dueDateMillis,
            dueTime = dueTimeMillis,
            tags = tags,
<<<<<<< HEAD
            projectName = extractedProject,
            priority = extractedPriority,
            recurrenceHint = extractedRecurrence
        )
    }

    private fun extractPriority(text: String): Int {
        // Named priorities (case-insensitive)
        val namedPattern = Regex("""(?:^|\s)!(urgent|high|med(?:ium)?|low)\b""", RegexOption.IGNORE_CASE)
        val namedMatch = namedPattern.find(text)
        if (namedMatch != null) {
            return when (namedMatch.groupValues[1].lowercase()) {
                "urgent" -> 4
                "high" -> 3
                "medium", "med" -> 2
                "low" -> 1
                else -> 0
            }
        }
        // Numbered: !4, !3, !2, !1
        val numberedPattern = Regex("""(?:^|\s)!([1-4])\b""")
        val numberedMatch = numberedPattern.find(text)
        if (numberedMatch != null) {
            return numberedMatch.groupValues[1].toInt()
        }
        // Bang count: !!!! = 4, !!! = 3, !! = 2, ! = 1 (standalone)
        val bangPattern = Regex("""(?:^|\s)(!{1,4})(?:\s|$)""")
        val bangMatch = bangPattern.find(text)
        if (bangMatch != null) {
            return bangMatch.groupValues[1].length.coerceAtMost(4)
        }
        return 0
    }

    private fun removePriorityToken(text: String): String {
        var result = text
        // Remove named priority
        result = Regex("""(?:^|\s)!(urgent|high|med(?:ium)?|low)\b""", RegexOption.IGNORE_CASE).replace(result, " ")
        // Remove numbered priority
        result = Regex("""(?:^|\s)!([1-4])\b""").replace(result, " ")
        // Remove bang-only priority
        result = Regex("""(?:^|\s)(!{1,4})(?:\s|$)""").replace(result, " ")
        return result
    }

    private fun extractRecurrence(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("every day") || lower.contains("daily") -> "daily"
            lower.contains("every week") || lower.contains("weekly") -> "weekly"
            lower.contains("every month") || lower.contains("monthly") -> "monthly"
            lower.contains("every year") || lower.contains("yearly") -> "yearly"
            else -> null
        }
    }

    private fun removeRecurrenceToken(text: String): String {
        var result = text
        result = Regex("""(?i)\bevery\s+day\b""").replace(result, " ")
        result = Regex("""(?i)\bdaily\b""").replace(result, " ")
        result = Regex("""(?i)\bevery\s+week\b""").replace(result, " ")
        result = Regex("""(?i)\bweekly\b""").replace(result, " ")
        result = Regex("""(?i)\bevery\s+month\b""").replace(result, " ")
        result = Regex("""(?i)\bmonthly\b""").replace(result, " ")
        result = Regex("""(?i)\bevery\s+year\b""").replace(result, " ")
        result = Regex("""(?i)\byearly\b""").replace(result, " ")
        return result
    }

    private data class DateTimeResult(val date: LocalDate?, val time: LocalTime?, val remaining: String)

    private fun extractDateTime(text: String): DateTimeResult {
        var remaining = text
        var date: LocalDate? = null
        var time: LocalTime? = null
        val today = LocalDate.now()

        // Time extraction first (so we don't confuse time tokens with date tokens)
        // "at noon"
        Regex("""(?i)\bat\s+noon\b""").find(remaining)?.let {
            time = LocalTime.of(12, 0)
            remaining = remaining.removeRange(it.range)
        }
        // "at midnight"
        if (time == null) {
            Regex("""(?i)\bat\s+midnight\b""").find(remaining)?.let {
                time = LocalTime.of(0, 0)
                remaining = remaining.removeRange(it.range)
            }
        }
        // "at 3pm" / "at 3:30pm" / "at 15:00"
        if (time == null) {
            Regex("""(?i)\bat\s+(\d{1,2}):(\d{2})\s*(am|pm)\b""").find(remaining)?.let {
                val hour = it.groupValues[1].toInt()
                val minute = it.groupValues[2].toInt()
                val ampm = it.groupValues[3].lowercase()
                time = toLocalTime(hour, minute, ampm)
                remaining = remaining.removeRange(it.range)
            }
        }
        if (time == null) {
            Regex("""(?i)\bat\s+(\d{1,2})\s*(am|pm)\b""").find(remaining)?.let {
                val hour = it.groupValues[1].toInt()
                val ampm = it.groupValues[2].lowercase()
                time = toLocalTime(hour, 0, ampm)
                remaining = remaining.removeRange(it.range)
            }
        }
        if (time == null) {
            Regex("""(?i)\bat\s+(\d{1,2}):(\d{2})\b""").find(remaining)?.let {
                val hour = it.groupValues[1].toInt()
                val minute = it.groupValues[2].toInt()
                if (hour in 0..23 && minute in 0..59) {
                    time = LocalTime.of(hour, minute)
                    remaining = remaining.removeRange(it.range)
                }
            }
        }

        // Date extraction
        // "today"
        Regex("""(?i)\btoday\b""").find(remaining)?.let {
            date = today
            remaining = remaining.removeRange(it.range)
        }
        // "tomorrow" / "tmrw"
        if (date == null) {
            Regex("""(?i)\b(?:tomorrow|tmrw)\b""").find(remaining)?.let {
                date = today.plusDays(1)
                remaining = remaining.removeRange(it.range)
            }
        }
        // "next monday" etc.
        if (date == null) {
            Regex("""(?i)\bnext\s+(mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:rs(?:day)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b""").find(remaining)?.let {
                val dow = parseDayOfWeek(it.groupValues[1])
                if (dow != null) {
                    date = today.with(TemporalAdjusters.next(dow))
                    // If that gives us this week, push to next
                    if (date!! <= today.plusDays(7)) {
                        date = today.with(TemporalAdjusters.next(dow))
                    }
                    remaining = remaining.removeRange(it.range)
                }
            }
        }
        // "in N days/weeks/months"
        if (date == null) {
            Regex("""(?i)\bin\s+(\d+)\s+(day|days|week|weeks|month|months)\b""").find(remaining)?.let {
                val n = it.groupValues[1].toLong()
                date = when (it.groupValues[2].lowercase().trimEnd('s')) {
                    "day" -> today.plusDays(n)
                    "week" -> today.plusWeeks(n)
                    "month" -> today.plusMonths(n)
                    else -> null
                }
                remaining = remaining.removeRange(it.range)
            }
        }
        // Day name without "next": "monday", "wednesday" etc.
        if (date == null) {
            Regex("""(?i)\b(mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:rs(?:day)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b""").find(remaining)?.let {
                val dow = parseDayOfWeek(it.groupValues[1])
                if (dow != null) {
                    date = today.with(TemporalAdjusters.nextOrSame(dow))
                    if (date == today) {
                        date = today.with(TemporalAdjusters.next(dow))
                    }
                    remaining = remaining.removeRange(it.range)
                }
            }
        }
        // Absolute: "jan 15", "march 3", "december 25"
        if (date == null) {
            Regex("""(?i)\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})\b""").find(remaining)?.let {
                val month = parseMonth(it.groupValues[1])
                val day = it.groupValues[2].toInt()
                if (month != null && day in 1..31) {
                    var candidate = LocalDate.of(today.year, month, day.coerceAtMost(LocalDate.of(today.year, month, 1).lengthOfMonth()))
                    if (candidate.isBefore(today)) {
                        candidate = LocalDate.of(today.year + 1, month, day.coerceAtMost(LocalDate.of(today.year + 1, month, 1).lengthOfMonth()))
                    }
                    date = candidate
                    remaining = remaining.removeRange(it.range)
                }
            }
        }
        // Slash date: "5/20", "12/25"
        if (date == null) {
            Regex("""\b(\d{1,2})/(\d{1,2})\b""").find(remaining)?.let {
                val month = it.groupValues[1].toInt()
                val day = it.groupValues[2].toInt()
                if (month in 1..12 && day in 1..31) {
                    val maxDay = LocalDate.of(today.year, month, 1).lengthOfMonth()
                    var candidate = LocalDate.of(today.year, month, day.coerceAtMost(maxDay))
                    if (candidate.isBefore(today)) {
                        val maxDayNext = LocalDate.of(today.year + 1, month, 1).lengthOfMonth()
                        candidate = LocalDate.of(today.year + 1, month, day.coerceAtMost(maxDayNext))
                    }
                    date = candidate
                    remaining = remaining.removeRange(it.range)
                }
            }
        }
        // ISO date: "2026-05-15"
        if (date == null) {
            Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").find(remaining)?.let {
                try {
                    date = LocalDate.of(
                        it.groupValues[1].toInt(),
                        it.groupValues[2].toInt(),
                        it.groupValues[3].toInt()
                    )
                    remaining = remaining.removeRange(it.range)
                } catch (_: Exception) { }
            }
        }

        // If time was parsed but no date, default to today
        if (time != null && date == null) {
            date = today
        }

        return DateTimeResult(date, time, remaining)
    }

    private fun toLocalTime(hour: Int, minute: Int, ampm: String): LocalTime? {
        val h = when {
            ampm == "am" && hour == 12 -> 0
            ampm == "pm" && hour != 12 -> hour + 12
            else -> hour
        }
        return if (h in 0..23 && minute in 0..59) LocalTime.of(h, minute) else null
    }

    private fun parseDayOfWeek(text: String): DayOfWeek? {
        val lower = text.lowercase()
        return when {
            lower.startsWith("mon") -> DayOfWeek.MONDAY
            lower.startsWith("tue") -> DayOfWeek.TUESDAY
            lower.startsWith("wed") -> DayOfWeek.WEDNESDAY
            lower.startsWith("thu") -> DayOfWeek.THURSDAY
            lower.startsWith("fri") -> DayOfWeek.FRIDAY
            lower.startsWith("sat") -> DayOfWeek.SATURDAY
            lower.startsWith("sun") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun parseMonth(text: String): Int? {
        val lower = text.lowercase()
        return when {
            lower.startsWith("jan") -> 1
            lower.startsWith("feb") -> 2
            lower.startsWith("mar") -> 3
            lower.startsWith("apr") -> 4
            lower == "may" -> 5
            lower.startsWith("jun") -> 6
            lower.startsWith("jul") -> 7
            lower.startsWith("aug") -> 8
            lower.startsWith("sep") -> 9
            lower.startsWith("oct") -> 10
            lower.startsWith("nov") -> 11
            lower.startsWith("dec") -> 12
            else -> null
        }
=======
            projectName = projectName,
            priority = priority,
            recurrenceHint = recurrenceHint
        )
    }

    private fun dayNames(): String =
        "monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun"

    private fun parseDayOfWeek(name: String): DayOfWeek? = when (name.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun monthNames(): String =
        "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"

    private fun parseMonth(name: String): Month? = when (name.lowercase().take(3)) {
        "jan" -> Month.JANUARY
        "feb" -> Month.FEBRUARY
        "mar" -> Month.MARCH
        "apr" -> Month.APRIL
        "may" -> Month.MAY
        "jun" -> Month.JUNE
        "jul" -> Month.JULY
        "aug" -> Month.AUGUST
        "sep" -> Month.SEPTEMBER
        "oct" -> Month.OCTOBER
        "nov" -> Month.NOVEMBER
        "dec" -> Month.DECEMBER
        else -> null
>>>>>>> a2293f05d619015c47d595dca2556c3e2fc16dae
    }
}
