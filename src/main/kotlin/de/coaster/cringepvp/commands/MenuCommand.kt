package de.coaster.cringepvp.commands

import de.coaster.cringepvp.CringePvP
import de.coaster.cringepvp.annotations.RegisterCommand
import de.coaster.cringepvp.database.model.CringeUser
import de.coaster.cringepvp.enums.Currency
import de.coaster.cringepvp.enums.Titles
import de.coaster.cringepvp.extensions.failSoundExecution
import de.coaster.cringepvp.extensions.soundExecution
import de.coaster.cringepvp.extensions.toCringeUser
import de.coaster.cringepvp.managers.PlayerCache
import de.moltenKt.core.extension.container.get
import de.moltenKt.core.extension.math.ceilToInt
import de.moltenKt.paper.extension.display.ui.item
import de.moltenKt.paper.extension.display.ui.set
import de.moltenKt.paper.extension.display.ui.skull
import de.moltenKt.paper.extension.paper.player
import de.moltenKt.paper.tool.display.canvas.Canvas
import de.moltenKt.paper.tool.display.canvas.CanvasSize
import de.moltenKt.paper.tool.display.canvas.buildCanvas
import de.moltenKt.unfold.extension.*
import de.moltenKt.unfold.text
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

@RegisterCommand(
    name = "menu",
    description = "Öffne das Menü",
    permission = "",
    aliases = ["m", "menü"]
)
class MenuCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true

        // Inhalte
        // Title, Skills upgraden,
        menuCanvas.display(sender)

        return true
    }

    companion object {

        val menuSkillsSkills = setOf(
            MenuSkill(
                "Angriffsschaden",
                Material.DIAMOND_SWORD,
                CringeUser::baseAttack,
                CringeUser::attackLevel,
                CringeUser::getPriceForNextAttack,
                CringeUser::getNextAttack,
                Currency.COINS,
                CringeUser::upgradeToNextAttack,
            ),
            MenuSkill(
                "Verteidigung",
                Material.SHIELD,
                CringeUser::baseDefense,
                CringeUser::defenseLevel,
                CringeUser::getPriceForNextDefense,
                CringeUser::getNextDefense,
                Currency.COINS,
                CringeUser::upgradeToNextAttack
            ),
            MenuSkill(
                "Laufgeschwindigkeit",
                Material.LEATHER_BOOTS,
                CringeUser::baseSpeed,
                CringeUser::speedLevel,
                CringeUser::getPriceForNextSpeed,
                CringeUser::getNextSpeed,
                Currency.GEMS,
                CringeUser::upgradeToNextAttack,
            ),
            MenuSkill(
                "Leben",
                Material.APPLE,
                CringeUser::baseHealth,
                CringeUser::healthLevel,
                CringeUser::getPriceForNextHealth,
                CringeUser::getNextHealth,
                Currency.GEMS,
                CringeUser::upgradeToNextAttack
            ),
        )

        val menuCanvas: Canvas = buildCanvas(CringePvP.key / "command.menu", CanvasSize.SMALL) {
            label = text("<gold>Dein Menü</gold>")

            disablePlayerItemGrabbing()

            this[11] = Material.NAME_TAG.item { label = text("<gold>Deine Title</gold>") }
            this[11] = { menuTitlesCanvas.display(this.player) }

            this[15] = Material.DIAMOND_SWORD.item { label = text("<gold>Deine Skills</gold>") }
            this[15] = { menuSkillsCanvas.display(this.player) }

        }

        private val backButton = skull("MHF_ArrowLeft", false).apply {
            label = text("<gold>Zurück</gold>")
            onClick { click ->
                menuCanvas.display(click.player)
            }
        }

        val menuSkillsCanvas: Canvas = buildCanvas(menuCanvas.key().subKey("skills", KeyingStrategy.SQUASH), CanvasSize.SMALL) {
            label = text("<#ff7f50>Skills")

            disablePlayerItemGrabbing()

            border(Material.BLACK_STAINED_GLASS_PANE.item.blankLabel().hideItemData())
            this[0] = backButton
            this[10..16 step 2] = Material.CLOCK.item.putLabel(text("<gold>Daten laden...</gold>"))

            onOpen {
                val cringe = it.player.toCringeUser()
                val keyColor = "<#aaaaaa>"
                val valColor = "<#ff7f50>"

                for (i in (10..16 step 2).withIndex()) {
                    val skill = menuSkillsSkills.get(i.index)
                    it.inventory[i.value] = skill.icon.item {
                        label = text("<#ff7f50>${skill.displayName}")
                        itemIdentity = menuCanvas.key().subKey("skill.${i.index}", KeyingStrategy.SQUASH).asString()
                        lore = buildList {
                            add("${keyColor}Aktuell: $valColor${skill.currentValue.get(cringe)}")
                            add("${keyColor}Aktuelles Level: $valColor${skill.currentLevel.get(cringe)}")
                            add(" ")
                            add("${keyColor}Upgrade: $valColor${skill.priceForNext.invoke(cringe)} ${skill.currency.display.asPlainString}")
                            add("${keyColor}Wird zu: $valColor${skill.nextBenefit.invoke(cringe)} ${skill.displayName}")
                        }.asStyledComponents
                        onClick { click ->
                            val user = click.player
                            val userCringe = user.toCringeUser()
                            val price = skill.priceForNext.invoke(userCringe)
                            val currency = skill.currency

                            if (currency.reference.get(userCringe) >= price) {
                                skill.upgradeCall.invoke(userCringe)
                                currency.reference.set(userCringe, currency.reference.get(userCringe) - price)
                                PlayerCache.updateCringeUser(userCringe)
                                user.soundExecution()
                                if (!click.isCancelled) this@buildCanvas.update(user)
                            } else {
                                user.failSoundExecution()
                            }

                        }
                    }
                }

            }

        }

        val menuTitlesCanvas: Canvas = buildCanvas(menuCanvas.key().subKey("titles", KeyingStrategy.SQUASH), CanvasSize.ofLines(
            ceilToInt(Titles.values().size.toDouble() / 7) + 2
        )) {
            label = text("<#ff7f50>Titel")

            disablePlayerItemGrabbing()

            border(Material.BLACK_STAINED_GLASS_PANE.item.blankLabel().hideItemData())
            this[0] = backButton
            setInner(innerSlots, skull("MHF_Question", false).putLabel(text("<red>???</red>")).hideItemData())

            onOpen {
                val player = it.player
                val cringe = player.toCringeUser()
                val titles = cringe.ownedTitles.takeIf { value -> value.isNotEmpty() } ?: listOf(Titles.NoTITLE)

                titles.forEachIndexed { index, title ->
                    val rarity = title.rarity

                    it.inventory[innerSlots[index]] = Material.NAME_TAG.item {
                        label = text("<${rarity.color}>${title.display}")
                        itemIdentity = menuCanvas.key().subKey("title.${title.ordinal}", KeyingStrategy.SQUASH).asString()
                        lore = buildList {
                            addAll(title.description.lines().map { line -> "<gray>$line" })
                            add(" ")
                            add("<#aaaaaa>Rarity: <${rarity.color}>${rarity.name}")
                        }.asStyledComponents
                        onClick { click ->
                            val user = click.player
                            val userCringe = user.toCringeUser()
                            PlayerCache.updateCringeUser(userCringe.copy(title = title))
                            this@buildCanvas.update(user)
                            user.soundExecution()
                            user.sendMessage(text("<gold><b>CringePvP</b></gold> <dark_gray>×</dark_gray> <gray>Du hast den Titel <gold>${title.display}</gold> ausgewählt.</gray>"))
                        }
                    }

                }

            }

        }

    }

    data class MenuSkill(
        val displayName: String,
        val icon: Material,
        val currentValue: KMutableProperty1<CringeUser, Double>,
        val currentLevel: KProperty1<CringeUser, Int>,
        val priceForNext: (CringeUser) -> Long,
        val nextBenefit: (CringeUser) -> Double,
        val currency: Currency,
        val upgradeCall: (CringeUser) -> Unit,
    )

}