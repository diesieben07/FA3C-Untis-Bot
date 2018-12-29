package de.takeweiland.untisbot

import kotlinx.coroutines.runBlocking
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.bots.AbsSender
import org.telegram.telegrambots.meta.logging.BotLogger
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.logging.Level

fun main(args: Array<String>) {
    ApiContextInitializer.init()
    BotLogger.setLevel(Level.ALL)

    val api = UntisApi("https://mese.webuntis.com/WebUntis", "bbs-haarentor")
    val cls = "FA3C"
//
    val classId = runBlocking {
        api.getClassId(cls) ?: throw Exception("Unknown class")
    }
    println("Class ID: $classId")

    val telegramToken = File("telegram-token").readText().trim()
    val telegram = TelegramBotsApi()
    telegram.registerBot(MyBot("fa3c_untis_bot", telegramToken, api, classId, cls))
}

class MyBot(username: String, private val token: String, untis: UntisApi, classId: Int, cls: String) :
    TelegramLongPollingCommandBot(username) {

    init {
        register(RaumCommand(untis, classId, cls))
    }

    override fun getBotToken() = token

    override fun processNonCommandUpdate(update: Update?) {

    }
}

class RaumCommand(private val untis: UntisApi, private val classId: Int, private val cls: String) : BotCommand("raum", "WO HAM WIR?") {

    override fun execute(absSender: AbsSender, user: User?, chat: Chat, arguments: Array<out String>) {
        try {
            val zone = ZoneId.of("Europe/Berlin")
            val now = if (arguments.isEmpty()) {
                LocalDateTime.now(zone)
            } else {
                LocalDateTime.parse(arguments.first())
            }
            // TODO: do this properly
            val periods = runBlocking { untis.timeTableEntries(classId, now.toLocalDate(), true) }
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

    private fun UntisApi.TimeTableEntry.getRoom(): String {
        return /*names.firstOrNull { roomRegex.matches(it) } ?: */filterNames(names).joinToString()
    }

    private val teacherShort = Regex("^[a-zA-Z]{1,3}$")
    val roomRegex = Regex("^R[0-9].*")

    private fun filterNames(names: Collection<String>): Collection<String> {
        return names
            .filterNot { teacherShort.matches(it) || it.equals(cls, true) }
            .sortedBy { if (roomRegex.matches(it)) 0 else 1 }
    }

}