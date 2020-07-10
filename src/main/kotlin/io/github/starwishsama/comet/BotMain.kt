package io.github.starwishsama.comet

import com.hiczp.bilibili.api.BilibiliClient
import io.github.starwishsama.comet.BotMain.bot
import io.github.starwishsama.comet.BotMain.startTime
import io.github.starwishsama.comet.api.bilibili.BiliBiliApi
import io.github.starwishsama.comet.api.twitter.TwitterApi
import io.github.starwishsama.comet.commands.CommandExecutor
import io.github.starwishsama.comet.commands.subcommands.chats.*
import io.github.starwishsama.comet.commands.subcommands.console.StopCommand
import io.github.starwishsama.comet.commands.subcommands.console.TestCommand
import io.github.starwishsama.comet.file.BackupHelper
import io.github.starwishsama.comet.file.DataSetup
import io.github.starwishsama.comet.listeners.ConvertLightAppListener
import io.github.starwishsama.comet.listeners.RepeatListener
import io.github.starwishsama.comet.listeners.SessionListener
import io.github.starwishsama.comet.managers.TaskManager
import io.github.starwishsama.comet.tasks.CheckLiveStatus
import io.github.starwishsama.comet.tasks.HitokotoUpdater
import io.github.starwishsama.comet.tasks.LatestTweetChecker
import io.github.starwishsama.comet.utils.getContext
import io.github.starwishsama.comet.utils.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.kronos.rkon.core.Rcon
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.data.EmptyMessageChain
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.PlatformLogger
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

object BotMain {
    val filePath: File = File(getPath())
    const val version = "0.3.8-DEV-a0fa6ec-20200710"
    var qqId = 0L
    lateinit var password: String
    lateinit var bot: Bot
    val client = BilibiliClient()
    lateinit var startTime: LocalDateTime
    lateinit var service: ScheduledExecutorService
    lateinit var logger: MiraiLogger
    var rCon: Rcon? = null
    lateinit var log: File

    fun executeCommand() {
        val scanner = Scanner(System.`in`)
        var command: String
        while (scanner.hasNextLine()) {
            command = scanner.nextLine()
            runBlocking {
                val result = CommandExecutor.executeConsole(command)
                if (result.isNotEmpty()) {
                    logger.info(result)
                }
            }
        }
        scanner.close()
    }

    private fun getPath(): String {
        var path: String = BotMain::class.java.protectionDomain.codeSource.location.path
        if (System.getProperty("os.name").toLowerCase().contains("dows")) {
            path = path.substring(1)
        }
        if (path.contains("jar")) {
            path = path.substring(0, path.lastIndexOf("/"))
            return path
        }
        val location = File(path.replace("target/classes/", ""))
        return location.path
    }

    fun setupRCon() {
        val url = BotConstants.cfg.rConUrl
        val pwd = BotConstants.cfg.rConPassword
        if (url != null && pwd != null && rCon == null) {
            rCon = Rcon(url, BotConstants.cfg.rConPort, pwd.toByteArray())
        }
    }

    fun initLog() {
        try {
            val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
            val initTime = LocalDateTime.now()
            val parent = File(getPath() + File.separator + "logs")
            if (!parent.exists()) {
                parent.mkdirs()
            }
            log = File(parent, "log-${dateFormatter.format(initTime)}.log")
            log.createNewFile()
        } catch (e: IOException) {
            error("尝试输出 Log 失败")
        }
    }
}

suspend fun main() {
    startTime = LocalDateTime.now()
    println("""
        
           ______                     __ 
          / ____/___  ____ ___  ___  / /_
         / /   / __ \/ __ `__ \/ _ \/ __/
        / /___/ /_/ / / / / / /  __/ /_  
        \____/\____/_/ /_/ /_/\___/\__/  


    """.trimIndent())
    BotMain.initLog()
    DataSetup.initData()
    BotMain.qqId = BotConstants.cfg.botId
    BotMain.password = BotConstants.cfg.botPassword

    if (BotMain.qqId == 0L) {
        println("请到 config.json 里填写机器人的QQ号&密码")
        exitProcess(0)
    } else {
        val config = BotConfiguration.Default
        config.botLoggerSupplier = { it ->
            PlatformLogger("Bot ${it.id}", {
                BotMain.log.writeString(BotMain.log.getContext() + "$it\n")
                println(it)
            })
        }
        config.networkLoggerSupplier = { it ->
            PlatformLogger("Net ${it.id}", {
                BotMain.log.writeString(BotMain.log.getContext() + "$it\n")
                println(it)
            })
        }
        config.heartbeatPeriodMillis = BotConstants.cfg.heartBeatPeriod * 60 * 1000
        config.fileBasedDeviceInfo()
        bot = Bot(qq = BotMain.qqId, password = BotMain.password, configuration = config)
        bot.alsoLogin()
        BotMain.logger = bot.logger
        CommandExecutor.setupCommand(
            arrayOf(
                    AdminCommand(),
                    BiliBiliCommand(),
                    CheckInCommand(),
                    ClockInCommand(),
                    DebugCommand(),
                    DivineCommand(),
                    GachaCommand(),
                    GuessNumberCommand(),
                    HelpCommand(),
                    InfoCommand(),
                    MusicCommand(),
                    MuteCommand(),
                    PictureSearch(),
                    R6SCommand(),
                    RConCommand(),
                    TwitterCommand(),
                    VersionCommand(),
                    // Console Command
                    StopCommand(),
                    TestCommand()
            )
        )

        val listeners = arrayOf(ConvertLightAppListener, RepeatListener, SessionListener)
        val apis = arrayOf(BiliBiliApi, TwitterApi)

        BotMain.logger.info("[命令] 已注册 " + CommandExecutor.commands.size + " 个命令")

        BotMain.setupRCon()

        BotMain.service = Executors.newScheduledThreadPool(
            4,
            BasicThreadFactory.Builder().namingPattern("bot-service-%d").daemon(true).build()
        )

        /** 定时任务 */
        BackupHelper.scheduleBackup()
        TaskManager.runScheduleTaskAsync(
            { BotConstants.users.forEach { it.addTime(100) } },
            5,
            5,
            TimeUnit.HOURS)
        TaskManager.runScheduleTaskAsyncIf(
            CheckLiveStatus::run,
            BotConstants.cfg.checkDelay,
            BotConstants.cfg.checkDelay,
            TimeUnit.MINUTES,
            BotConstants.cfg.subList.isNotEmpty()
        )
        TaskManager.runAsync({
            BotMain.client.runCatching {
                val pwd = BotConstants.cfg.biliPassword
                val username = BotConstants.cfg.biliUserName

                if (pwd != null && username != null) {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            login(username = username, password = pwd)
                        }
                    }
                }
            }
        }, 5)
        TaskManager.runScheduleTaskAsync({ apis.forEach { it.resetTime() } }, 25, 25, TimeUnit.MINUTES)
        TaskManager.runScheduleTaskAsyncIf(
                LatestTweetChecker::run,
                1,
                8,
                TimeUnit.MINUTES,
                (BotConstants.cfg.twitterSubs.isNotEmpty() && BotConstants.cfg.tweetPushGroups.isNotEmpty())
        )
        TaskManager.runAsync(HitokotoUpdater::run, 5)

        /** 监听器 */
        listeners.forEach {
            it.register(bot)
            BotMain.logger.info("[监听器] 已注册 ${it.getName()} 监听器")
        }

        val time = Duration.between(startTime, LocalDateTime.now())
        val startUsedTime = "${time.toSecondsPart()}s${time.toMillisPart()}ms"

        BotMain.logger.info("无名 Bot 启动成功, 耗时 $startUsedTime")

        Runtime.getRuntime().addShutdownHook(Thread {
            BotMain.logger.info("[Bot] 正在关闭 Bot...")
            DataSetup.saveFiles()
            BotMain.service.shutdown()
            BotMain.rCon?.disconnect()
        })

        bot.subscribeMessages {
            always {
                if (sender.id != 80000000L) {
                    val result = CommandExecutor.execute(this)
                    if (result !is EmptyMessageChain) {
                        reply(result)
                    }
                }
            }
        }

        BotMain.executeCommand()

        bot.join() // 等待 Bot 离线, 避免主线程退出
    }
}
