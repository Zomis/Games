package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.components.Point
import net.zomis.games.dsl.Action
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.SizedBombWeapon
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.games.impl.minesweeper.Weapons

val Flags.Field.point: Point get() = Point(x, y)

fun Flags.Player.clickWeaponUse(field: Flags.Field): WeaponUse {
    return WeaponUse(this.weapons.first { it is Weapons.Default }, field.point)
}

fun Flags.Player.bombWeaponUse(field: Flags.Field): WeaponUse {
    return WeaponUse(this.weapons.first { it is SizedBombWeapon }, field.point)
}

fun Flags.Player.canUseBomb(game: Flags.Model): Boolean {
    return this.weapons.find { it is SizedBombWeapon }?.usableForPlayer(game, playerIndex) ?: false
}

fun WeaponUse.toAction(game: Flags.Model, playerIndex: Int) = Action(game, playerIndex, Flags.useWeapon.name, this)
