package de.coaster.cringepvp.listeners

import com.destroystokyo.paper.ParticleBuilder
import de.coaster.cringepvp.CringePvP
import de.coaster.cringepvp.enums.Titles
import de.coaster.cringepvp.extensions.*
import de.coaster.cringepvp.managers.PlayerCache
import de.coaster.cringepvp.managers.PlayerCache.updateCringeUser
import dev.fruxz.ascend.extension.data.randomInt
import dev.fruxz.stacked.text
import io.papermc.paper.event.player.PlayerOpenSignEvent
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.spigotmc.event.player.PlayerSpawnLocationEvent
import kotlin.math.ceil
import kotlin.math.roundToInt


class CringeListener : Listener {

    @EventHandler
    fun onChat(event: PlayerCommandPreprocessEvent) = with(event) {
        if(message.startsWith("/plugins")) {
            player.sendMessage(text("<red>PluginsHide »</red> <red>Du darfst keine Plugins anschauen!</red>"))
            event.isCancelled = true
        } else if(message == "/pl") {
            player.sendMessage(text("<dark_purple><i><u>NoPlugins</u></i>: <dark_red><b>Du darfst Plugins nicht anschauen!</b></dark_red>"))
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) = with(event) {
        player.onSpawn()
        joinMessage(text("<#7bed9f>${event.player.name} betritt unsere Cringeschlacht."))
    }

    @EventHandler
    fun onRespawnEvent(event: PlayerRespawnEvent) = with(event) {
        player.onSpawn()
    }

    @EventHandler
    fun onPlayerSpawn(event: PlayerSpawnLocationEvent) = with(event) {
        spawnLocation = player.world.spawnLocation
        player.onSpawn()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) = with(event) {
        PlayerCache.remove(player.uniqueId)
        quitMessage(text("<#ff6b6b>${event.player.name} verlässt unsere Cringeschlacht."))
    }

    @EventHandler
    fun onHealthRegain(event: EntityRegainHealthEvent) {
        val entity = event.entity
        val healthAmount = event.amount

        ParticleBuilder(Particle.HEART)
            .location(entity.location)
            .count(ceil(5 * healthAmount.let { if (it >= 1) it else 1.0 }).roundToInt())
            .offset(.35, .5, .35)
            .extra(.01)
            .spawn()
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) = with(event) {
        if(entity.location.distanceToWorldSpawn < 30) {
            foodLevel = 20
        }
    }

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) = with(event) {
        if (event.entity !is LivingEntity) {
            if(damager is Player && (damager as Player).isBuilder) return@with
            isCancelled = true
            return@with
        }

        val entity = event.entity as LivingEntity
        if ((damager !is Player && damager !is Arrow) || entity is Pig) return@with

        val damageEntity : Entity = if (damager is Player) damager else ((damager as Arrow).shooter as Entity? ?: return@with)
        if (damageEntity is Player) {
            if (!damageEntity.isBuilder)  {
                val coordinatesFirst = damageEntity.location
                val coordinatesSecond = entity.location
                if (coordinatesFirst.distanceToWorldSpawn < 30 || coordinatesSecond.distanceToWorldSpawn < 30) {
                    isCancelled = true
                    return@with
                }
            }
        }

        entity.world.playSound(entity.location, Sound.BLOCK_BONE_BLOCK_BREAK, SoundCategory.PLAYERS, 1F, 2F)
        entity.world.playSound(entity.location, Sound.BLOCK_STONE_BREAK, SoundCategory.PLAYERS,1F, 2F)

        ParticleBuilder(Particle.BLOCK_CRACK)
            .data(Material.REDSTONE_BLOCK.createBlockData())
            .count(ceil(15 * (event.damage.let { if (it >= 1) it else 1.0 })).roundToInt())
            .location(entity.location)
            .offset(.5, .75, .5)
            .extra(.01)
            .spawn()

        if (event.damager is Player && (event.entity as LivingEntity).health - event.damage <= 0) {
            (event.damager as Player).giveExp(2)
            var cringeUser = (event.damager as Player).toCringeUser()
            cringeUser = cringeUser.copy(kills = cringeUser.kills + 1, xp = cringeUser.xp + 2, coins = cringeUser.coins + (cringeUser.idleCash * randomInt(1 .. 3).toDouble()))
            updateCringeUser(cringeUser)

            if (event.entity is Player) {
                entity.world.playSound(entity.location, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 2F, 1F)
            }
        }
    }

    @EventHandler
    fun onSignEdit(event: PlayerOpenSignEvent) = with(event) {
        if (player.isBuilder) return@with

        isCancelled = true
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) = with(event) {
        var deadCringeUser = player.toCringeUser()
        deadCringeUser = deadCringeUser.copy(deaths = deadCringeUser.deaths + 1)
        updateCringeUser(deadCringeUser)


        val soulBoundInventory = Bukkit.createInventory(null, 54, text("<color:#4aabff><b>Soulbound</b></color>"))
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i) ?: continue
            if(item.soulbound) {
                soulBoundInventory.setItem(i, item)
            }
        }
        player.saveInventory(soulBoundInventory, "soulbounds")

        // Nothing should drop on death
        drops.clear()
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) = with(event) {
        if (entity is Player) return@with

        if(entity.lastDamageCause?.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            val damager = (entity.lastDamageCause as EntityDamageByEntityEvent).damager
            if(damager !is Player) return@with
            val mobKills = damager.getStatistic(Statistic.MOB_KILLS)

            if(mobKills >= 100) damager.addTitle(Titles.MonsterSchlachter)
        }
    }

    private fun Player.onSpawn() {
        getAttribute(Attribute.GENERIC_ATTACK_SPEED)?.let { it.baseValue = 9999.0 }

        val cringeUser = toCringeUser()
        getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.let { it.baseValue = cringeUser.baseAttack }
        getAttribute(Attribute.GENERIC_ARMOR)?.let { it.baseValue = cringeUser.baseDefense }
        getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.let { it.baseValue = cringeUser.baseSpeed }
        getAttribute(Attribute.GENERIC_MAX_HEALTH)?.let { it.baseValue = cringeUser.baseHealth }

        health = cringeUser.baseHealth
        foodLevel = 20

        Bukkit.getScheduler().runTaskLater(CringePvP.instance, Runnable {
            loadInventory("soulbounds")
            removeInventory("soulbounds")
        }, 20)

        world.spawnParticle(Particle.EXPLOSION_HUGE, world.spawnLocation, 100, 0.0, 0.0, 0.0, 20.0)
        playSound(location, Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.AMBIENT,0.1f, 1f)
    }
}