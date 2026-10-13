package ru.anime.bridge

import java.util.Base64

data class LegacySave(val coins: Long, val scene: Int, val unlocked: Boolean,
    val completed: Int, val upgrades: Int, val review: Boolean, val sound: Boolean,
    val purchases: Set<String>)

object LegacyMigration {
    fun decode(text: String?, sceneCount: Int = 74): LegacySave? = runCatching {
        val p = requireNotNull(text).split('|')
        require((p[0] == "1" && p.size == 8) || (p[0] == "2" && p.size == 9) || (p[0] == "3" && p.size == 10))
        val count = if(p[0] == "3") p[9].toInt() else 85
        require(count in 1..10000)
        fun flag(i: Int): Boolean { require(p[i] == "0" || p[i] == "1");return p[i] == "1" }
        val coins=p[1].toLong();val scene=p[2].toInt();val unlocked=flag(3);val completed=p[4].toInt();val upgrades=p[5].toInt()
        require(coins in 0..1_000_000_000_000L && scene in 0 until count && upgrades in 0..60)
        require(completed == scene || (scene == count-1 && completed == count && unlocked))
        val ids=if(p[0] != "1" && p[8].isNotEmpty())p[8].split(',').map { String(Base64.getUrlDecoder().decode(it),Charsets.UTF_8).also { id -> require(id.isNotBlank()) } }.toSet() else emptySet()
        val finished=completed>=sceneCount
        LegacySave(coins,if(finished)sceneCount-1 else scene,unlocked||finished,
            completed.coerceAtMost(sceneCount),upgrades,flag(6),flag(7),ids)
    }.getOrNull()
}
