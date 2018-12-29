package de.takeweiland.untisbot

import kotlinx.coroutines.runBlocking
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.logging.BotLogger
import java.util.logging.Level
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    ApiContextInitializer.init()
    BotLogger.setLevel(Level.ALL)

    val config = loadConfig()
    if (config == null) {
        println("Please correct config file")
        exitProcess(0)
    }

    val api = UntisApi(config.untis.url, config.untis.school, config.untis.user, config.untis.password)
    val classId = runBlocking {
        api.getClassId(config.untis.cls) ?: throw Exception("Unknown class")
    }
    println("Class ID: $classId")

    val telegram = TelegramBotsApi()
    val telegramBot =
        UntisTelegramBot(config.telegram.botName, config.telegram.token, api, classId, config.untis.cls)
    telegram.registerBot(telegramBot)

    val scheduler = StdSchedulerFactory.getDefaultScheduler()
    scheduler.start()

    val job = JobBuilder
        .newJob(SubscriptionsJob::class.java)
        .usingJobData(JobDataMap(hashMapOf("telegramBot" to telegramBot)))
        .build()
    val trigger = TriggerBuilder.newTrigger()
        .startNow()
        .withSchedule(CronScheduleBuilder.cronSchedule(config.schedule))
        .build()

    scheduler.scheduleJob(job, trigger)

}