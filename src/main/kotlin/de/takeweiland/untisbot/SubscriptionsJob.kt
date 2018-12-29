package de.takeweiland.untisbot

import org.quartz.Job
import org.quartz.JobExecutionContext

class SubscriptionsJob : Job {

    lateinit var telegramBot: UntisTelegramBot

    override fun execute(context: JobExecutionContext?) {
        telegramBot.notifySubscriptions()
    }
}