package de.takeweiland.untisbot

import com.electronwill.nightconfig.core.ConfigSpec
import com.electronwill.nightconfig.core.file.FileConfig

data class Config(val telegram: Telegram, val untis: Untis) {

    data class Telegram(val token: String, val botName: String)
    data class Untis(val url: String, val school: String, val cls: String, val user: String, val password: String)

}

fun loadConfig(): Config? {
    val spec = ConfigSpec().apply {
        define("telegram.token", "", Any::isNonBlankString)
        define("telegram.botName", "", Any::isNonBlankString)
        define("untis.url", "", Any::isNonBlankString)
        define("untis.school", "", Any::isNonBlankString)
        define("untis.class", "", Any::isNonBlankString)
        define("untis.user", "", Any::isNonBlankString)
        define("untis.password", "", Any::isNonBlankString)
    }

    FileConfig.builder("config.toml")
        .defaultData(Thread.currentThread().contextClassLoader.getResource("blank-config.toml"))
        .build()
        .use { config ->
            config.load()
            config.save()
            return if (spec.isCorrect(config)) {
                Config(
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