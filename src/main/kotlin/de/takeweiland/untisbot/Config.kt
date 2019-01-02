package de.takeweiland.untisbot

import com.electronwill.nightconfig.core.ConfigSpec
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import java.time.DateTimeException
import java.time.ZoneId

data class Config(val schedule: String, val timeZone: ZoneId,  val telegram: Telegram, val untis: Untis) {

    data class Telegram(val token: String, val botName: String)
    data class Untis(val url: String, val school: String, val cls: String, val user: String, val password: String)

}

fun loadConfig(): Config? {
    val spec = ConfigSpec().apply {
        define("schedule", "55 23 * * TUE", Any::isNonBlankString)
        define("timeZone", "Europe/Berlin") { tz ->
            if (tz is String) {
                try {
                    ZoneId.of(tz)
                    true
                } catch (e: DateTimeException) {
                    false
                }
            } else {
                false
            }
        }
        define("telegram.token", "", Any::isNonBlankString)
        define("telegram.botName", "", Any::isNonBlankString)
        define("untis.url", "", Any::isNonBlankString)
        define("untis.school", "", Any::isNonBlankString)
        define("untis.class", "", Any::isNonBlankString)
        define("untis.user", "", Any::isNonBlankString)
        define("untis.password", "", Any::isNonBlankString)
    }

    CommentedFileConfig.builder("config.toml")
        .defaultData(Thread.currentThread().contextClassLoader.getResource("blank-config.toml"))
        .build()
        .use { config ->
            config.load()

            config.setComment("schedule", "Crontab expression for when to report classes for the day")

            config.save()
            return if (spec.isCorrect(config)) {
                Config(
                    schedule = config.get("schedule"),
                    timeZone = ZoneId.of(config.get("timeZone")),
                    telegram = Config.Telegram(
                        token = config.get("telegram.token"),
                        botName = config.get("telegram.botName")
                    ),
                    untis = Config.Untis(
                        url = config.get("untis.url"),
                        school = config.get("untis.school"),
                        cls = config.get("untis.class"),
                        user = config.get("untis.user"),
                        password = config.get("untis.password")
                    )
                )
            } else {
                null
            }
        }
}

private fun Any.isNonBlankString() = this is String && this.isNotBlank()