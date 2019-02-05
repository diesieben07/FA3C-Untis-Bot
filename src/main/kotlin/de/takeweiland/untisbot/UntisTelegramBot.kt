package de.takeweiland.untisbot

import kotlinx.coroutines.runBlocking
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.bots.AbsSender
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UntisTelegramBot(
    username: String,
    private val token: String,
    private val giphy: GiphyApi,
    private val untis: UntisApi,
    private val classId: Int,
    private val cls: String
) :
    TelegramLongPollingCommandBot(username) {

    private val subscribedChats = HashSet<Long>()

    init {
        register(RaumCommand())
        register(SubscribeCommand())
        register(UnsubscribeCommand())
        register(TimetableCommand())
        register(WichtigCommand())
        register(TroffiCommand())
        register(GiphyCommand())

        loadSubscriptions()
    }

    override fun getBotToken() = token

    override fun processNonCommandUpdate(update: Update?) {}

    private fun saveSubscriptions() {
        File("subscriptions.txt").bufferedWriter().use { writer ->
            for (chat in subscribedChats) {
                writer.write(chat.toString(Character.MAX_RADIX))
                writer.write('\n'.toInt())
            }
        }
    }

    private fun loadSubscriptions() {
        try {
            File("subscriptions.txt").bufferedReader().use { reader ->
                do {
                    val line = reader.readLine() ?: break
                    subscribedChats += line.toLongOrNull(Character.MAX_RADIX) ?: continue
                } while (true)
            }
        } catch (e: FileNotFoundException) {
            // ignored
        }
    }

    fun notifySubscriptions() {
        val chats = synchronized(this) {
            subscribedChats
        }
        notifyHours(chats, displayEmpty = false)
    }

    private fun notifyHours(chats: Collection<Long>, overwriteDate: LocalDate? = null, displayEmpty: Boolean = false) {
        val now = overwriteDate ?: LocalDate.now(ZoneId.of("Europe/Berlin"))
        val message = StringBuilder()
        val periods = runBlocking { untis.timeTableEntries(classId, now, true) }

        if (periods.isEmpty()) {
            if (!displayEmpty) return
            message.append("*Hier ist gar nix.*")
        } else {
            message.append("*Es steht an:*")

            for (period in periods) {
                message.append("\n- ")
                message.append(period.getRoom(true))
            }
        }

        for (chat in chats) {
            execute(SendMessage(chat, message.toString()).apply { setParseMode("Markdown") })
        }
    }

    private val teacherShort = Regex("^[a-zA-Z]{1,3}$")
    private val roomRegex = Regex("^R[0-9].*")

    private fun UntisApi.TimeTableEntry.getRoom(inverse: Boolean = false): String {
        val sorted = names
            .filterNot { teacherShort.matches(it) || it.equals(cls, true) }
            .sortedBy { if (roomRegex.matches(it)) inverse else !inverse }
        return sorted.joinToString()
    }

    private inner class TimetableCommand : BotCommand("timetable", "Was is los?") {
        override fun execute(absSender: AbsSender?, user: User?, chat: Chat, arguments: Array<out String>) {
            val now = if (arguments.isEmpty()) {
                LocalDate.now(ZoneId.of("Europe/Berlin"))
            } else {
                LocalDate.parse(arguments.first())
            }
            notifyHours(listOf(chat.id), now, displayEmpty = true)
        }
    }

    private inner class WichtigCommand : BotCommand("wichtig", "") {
        override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>?) {
            absSender.execute(SendMessage(chat.id, "Nö."))
        }
    }

    private inner class TroffiCommand : BotCommand("emrah", "") {
        override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>?) {
            absSender.execute(SendPhoto().apply {
                setChatId(chat.id)
                setPhoto("egg.jpg", Thread.currentThread().contextClassLoader.getResourceAsStream("egg.jpg"))
                caption = "Easter-Egg!"
            })
        }
    }

    private inner class RaumCommand : BotCommand("raum", "WO HAM WIR?") {

        override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>) {
            try {
                val zone = ZoneId.of("Europe/Berlin")
                val now = if (arguments.isEmpty()) {
                    LocalDateTime.now(zone)
                } else {
                    LocalDateTime.parse(arguments.first())
                }
                // TODO: do this properly
                val periods =
                    runBlocking { untis.timeTableEntries(classId, now.toLocalDate(), true) }
                val response = if (periods.isEmpty()) {
                    "Hier is heut nix. Geh nach Hause!"
                } else {
                    val currentPeriod = periods.firstOrNull { now.toLocalTime() >= it.start && now.toLocalTime() < it.end }
                    val nextPeriod = periods.firstOrNull { it.start > now.toLocalTime() }
                    val b = StringBuilder()
                    if (currentPeriod != null) {
                        val timeLeft = Duration.between(now.toLocalTime(), currentPeriod.end)
                        b.append("Auf nach ${currentPeriod.getRoom()}")
                        if (timeLeft.toMinutes() <= 15) {
                            b.append(", aber das kannste dir jetzt auch schenken, so spät wie das ist")
                        }
                        b.append(".")
                    }

                    if (nextPeriod != null) {
                        if (currentPeriod == null) {
                            b.append("Auf nach ${nextPeriod.getRoom()}.")
                        } else {
                            b.append("\nDanach geht's nach ${nextPeriod.getRoom()}.")
                        }
                    }

                    b.toString()
                }

                val answer = SendMessage(chat.id, response)
                absSender.execute(answer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private inner class SubscribeCommand : BotCommand("subscribe", "Benachrichtigungen in diesen Chat senden") {
        override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>?) {
            val added = synchronized(this@UntisTelegramBot) {
                if (subscribedChats.add(chat.id)) {
                    saveSubscriptions()
                    true
                } else {
                    false
                }
            }
            absSender.execute(SendMessage(chat.id, if (added) "Geht klar." else "Mach ich doch schon!"))
        }
    }

    private inner class UnsubscribeCommand :
        BotCommand("unsubscribe", "Keine Benachrichtigungen mehr in diesen Chat senden") {
        override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>?) {
            val added = synchronized(this@UntisTelegramBot) {
                if (subscribedChats.remove(chat.id)) {
                    saveSubscriptions()
                    true
                } else {
                    false
                }
            }
            absSender.execute(SendMessage(chat.id, if (added) "Geht klar." else "Ich kenn dich doch gar nicht!"))
        }
    }

    private inner class GiphyCommand : BotCommand("easteregg", "Tüdelü") {

        override fun execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array<out String>) {
            val tag = arguments.getOrNull(0)
            val gif = runBlocking {
                giphy.getRandomGif(tag)
            }
            val connection = URL(gif).openConnection()
            connection.addRequestProperty("Accept", "video/mp4")
            absSender.execute(SendAnimation().apply {
                setChatId(chat.id)
                setAnimation("giphy.mp4", connection.getInputStream())
                caption = "Tüdelü!"
            })
        }
    }
}