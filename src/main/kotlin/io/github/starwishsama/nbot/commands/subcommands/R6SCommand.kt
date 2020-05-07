package io.github.starwishsama.nbot.commands.subcommands

import cn.hutool.core.util.StrUtil
import io.github.starwishsama.nbot.commands.CommandProps
import io.github.starwishsama.nbot.commands.interfaces.UniversalCommand
import io.github.starwishsama.nbot.enums.UserLevel
import io.github.starwishsama.nbot.objects.BotUser
import io.github.starwishsama.nbot.util.BotUtil.getLocalMessage
import io.github.starwishsama.nbot.util.BotUtil.isLegitId
import io.github.starwishsama.nbot.util.BotUtil.isNoCoolDown
import io.github.starwishsama.nbot.util.BotUtil.sendLocalMessage
import io.github.starwishsama.nbot.util.BotUtil.toMirai
import io.github.starwishsama.nbot.util.R6SUtils.getR6SInfo
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.EmptyMessageChain
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.message.data.toMessage

class R6SCommand : UniversalCommand {
    override suspend fun execute(event: MessageEvent, args: List<String>, user: BotUser): MessageChain {
        if (isNoCoolDown(event.sender.id) && event is GroupMessageEvent) {
            if (args.isEmpty()) {
                return (getLocalMessage("msg.bot-prefix") + "/r6s info [Uplay账号名]").toMirai()
            } else {
                when (args[0].toLowerCase()) {
                    "info", "查询" -> {
                        return if (user.r6sAccount != null && args.size == 1) {
                            event.reply(sendLocalMessage("msg.bot-prefix", "查询中..."))
                            val result = getR6SInfo(user.r6sAccount!!)
                            event.sender.at().plus("\n" + result.toMessage())
                        } else if (args.size == 2 && args[1].isNotEmpty() && isLegitId(args[1])) {
                            event.reply(sendLocalMessage("msg.bot-prefix", "查询中..."))
                            val result = getR6SInfo(args[1])
                            event.sender.at().plus("\n" + result.toMessage())
                        } else {
                            ("${getLocalMessage("msg.bot-prefix")} /r6 查询 [ID] 或者 /r6 绑定 [id]\n" +
                                    "绑定彩虹六号账号 无需输入ID快捷查询游戏数据").toMirai()
                        }
                    }
                    "bind", "绑定" ->
                        if (StrUtil.isNotEmpty(args[1]) && args.size == 2) {
                            if (isLegitId(args[1])) {
                                if (BotUser.isUserExist(event.sender.id)) {
                                    val botUser1 = BotUser.getUser(event.sender.id)
                                    if (botUser1 != null) {
                                        botUser1.r6sAccount = args[1]
                                        return (getLocalMessage("msg.bot-prefix") + "绑定成功!").toMirai()
                                    }
                                } else return (getLocalMessage("msg.bot-prefix") + "使用 /qd 签到自动注册机器人系统").toMirai()
                            } else return (getLocalMessage("msg.bot-prefix") + "ID 格式有误!").toMirai()
                        }
                    else -> return (getLocalMessage("msg.bot-prefix") + "/r6s info [Uplay账号名]").toMirai()
                }
            }
        }
        return EmptyMessageChain
    }

    override fun getProps(): CommandProps =
        CommandProps("r6", arrayListOf("r6s", "彩六"), "彩虹六号数据查询", "nbot.commands.r6s", UserLevel.USER)

    override fun getHelp(): String = """
        ======= 命令帮助 =======
        /r6 info [Uplay账号名] 查询战绩
        /r6 bind [Uplay账号名] 绑定账号
        /r6 info 查询战绩 (需要绑定账号)
    """.trimIndent()
}