package io.github.starwishsama.comet.objects.gacha.pool

import cn.hutool.core.util.RandomUtil
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.BotVariables.hiddenOperators
import io.github.starwishsama.comet.objects.BotUser
import io.github.starwishsama.comet.objects.gacha.items.ArkNightOperator
import io.github.starwishsama.comet.objects.gacha.items.GachaItem
import io.github.starwishsama.comet.utils.DrawUtil
import java.math.RoundingMode
import java.util.stream.Collectors

/**
 * Operator info from https://github.com/Kengxxiao/ArknightsGameData/
 *
 * Operator Picture from http://prts.wiki/
 *
 * You can download here: https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData/master/zh_CN/gamedata/excel/character_table.json
 *
 * @author Nameless
 */
class ArkNightPool(override val name: String = "标准寻访" ) : GachaPool() {
    override val tenjouCount: Int = -1
    override val tenjouRare: Int = -1
    override val poolItems: MutableList<ArkNightOperator> = BotVariables.arkNight.stream().filter { !hiddenOperators.contains(it.name) && it.obtain?.contains("招募寻访") == true }.collect(Collectors.toList())

    override fun doDraw(time: Int): List<ArkNightOperator> {
        val result = mutableListOf<ArkNightOperator>()
        var r6UpRate = 0.00

        repeat(time) {
            // 在五十连之后的保底
            if (it >= 50 && r6UpRate == 0.0) {
                r6UpRate += 0.02
            }

            val probability = RandomUtil.randomDouble(2, RoundingMode.HALF_DOWN)

            // 按默认抽卡规则抽出对应星级干员, 超过五十连后保底机制启用

            val rare: Int =  when (probability) {
                in 0.50..0.52 + r6UpRate -> 6 // 2%
                in 0.0..0.08 -> 5 // 8%
                in 0.09..0.49 -> 4 // 40%
                else -> 3 // 50%
            }

            when {
                // 如果在保底的基础上抽到了六星, 则重置加倍概率
                r6UpRate > 0 -> {
                    if (rare == 6) r6UpRate = 0.0 else r6UpRate += 0.02
                }
                // 究极非酋之后必爆一个六星
                rare != 6 && r6UpRate > 0.5 -> {
                    result.add(getGachaItem(6, r6UpRate))
                    r6UpRate = 0.0
                    return@repeat
                }
            }

            result.add(getGachaItem(rare, probability))
        }
        return result
    }

    override fun getGachaItem(rare: Int): GachaItem {
        return getGachaItem(rare, -1.0)
    }

    // 使用自定义抽卡方式
    private fun getGachaItem(rare: Int, probability: Double): ArkNightOperator {
        // 然后检查是否到了出 UP 角色的时候
        if (highProbabilityItems.isNotEmpty()) {
            val targetUps = highProbabilityItems.keys.parallelStream().collect(Collectors.toList())
            val targetUp = targetUps[RandomUtil.randomInt(0, targetUps.size - 1)]

            val targetProbability = highProbabilityItems[targetUp]

            if (targetProbability != null && probability in targetProbability) {
                return targetUp as ArkNightOperator
            }
        }

        val rareItems = poolItems.parallelStream().filter { it.rare == (rare - 1) }.collect(Collectors.toList())
        require(rareItems.isNotEmpty()) { "获取干员列表失败: 空列表. 等级为 ${rare - 1}, 池内干员数 ${poolItems.size}" }
        return rareItems[RandomUtil.randomInt(0, rareItems.size - 1)]
    }

    /**
     * 明日方舟抽卡结果
     */
    fun getArkDrawResult(user: BotUser, time: Int = 1): List<ArkNightOperator> {
        return if (DrawUtil.checkHasGachaTime(user, time)) {
            user.decreaseTime(time)
            doDraw(time)
        } else {
            emptyList()
        }
    }

    /**
     * 明日方舟抽卡，返回文字
     */
    fun getArkDrawResultAsString(user: BotUser, drawResult: List<ArkNightOperator>): String {
        if (drawResult.isNotEmpty()) {
            when (drawResult.size) {
                1 -> {
                    val (name, _, rare) = drawResult[0]
                    return "单次寻访结果\n$name ${DrawUtil.getStar(rare, true)}"
                }
                10 -> {
                    return StringBuilder("十连寻访结果:\n").apply {
                        for ((name, _, rare) in drawResult) {
                            append(name).append(" ").append(DrawUtil.getStar(rare, true)).append(" ")
                        }
                    }.trim().toString()
                }
                else -> {
                    val r6Count = drawResult.parallelStream().filter { it.rare + 1 == 6 }.count()
                    val r5Count = drawResult.parallelStream().filter { it.rare + 1 == 4 }.count()
                    val r4Count = drawResult.parallelStream().filter { it.rare + 1 == 3 }.count()
                    val r3Count = drawResult.size - r6Count - r5Count - r4Count

                    return "寻访结果:\n" +
                            "寻访次数: ${drawResult.size}\n" +
                            "结果: ${r6Count}|${r5Count}|${r4Count}|${r3Count}\n" +
                            "使用合成玉 ${drawResult.size * 600}"
                }
            }
        } else {
            return DrawUtil.overTimeMessage + "\n剩余次数: ${user.commandTime}"
        }
    }

    /**
     * 明日方舟抽卡，返回文字
     */
    fun getArkDrawResultAsString(user: BotUser, time: Int): String = getArkDrawResultAsString(user, getArkDrawResult(user, time))
}