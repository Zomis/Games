package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.components.Point
import net.zomis.games.dsl.Action
import net.zomis.games.impl.minesweeper.*
import net.zomis.games.scorers.ScorerScope
import net.zomis.minesweeper.analyze.detail.ProbabilityKnowledge

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

fun ScorerScope<Flags.Model, WeaponUse>.weaponIsClick(): Boolean {
    return action.parameter.weapon is Weapons.Default
}
fun ScorerScope<Flags.Model, WeaponUse>.weaponIsBomb(): Boolean {
    return action.parameter.weapon is SizedBombWeapon
}

fun ScorerScope<Flags.Model, WeaponUse>.scoreFor(
    func: (Flags.Field, ProbabilityKnowledge<Flags.Field>) -> Double,
    provider: MfeProbabilityProvider
): Double {
    val analysis = this.require(provider)!!
    TODO("Not yet implemented")
}

fun ScorerScope<Flags.Model, WeaponUse>.scoreFor(func: (Flags.Field) -> Double): Double {
    return func.invoke(this.action.game.fieldAt(this.action.parameter.position))
}
