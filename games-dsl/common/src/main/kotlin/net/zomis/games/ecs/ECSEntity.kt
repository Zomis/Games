package net.zomis.games.ecs

import net.zomis.games.components.grids.Grid
import net.zomis.games.dsl.ViewScope

// Cool POC: Make a game that shuffles between some two-player games when you start it, and start one of those games

//class SimpleEntity: ECSEntity

/*
Cost component
Requirements component
Rules component
OnEvent component
PrivateView component
PublicView component

View propagates from parent to children. Provide reasonable defaults.
Can default views be provided for Actions? How much can be informed? `cost { XYZ } paidBy { entity }` can be resolved and entity referred to by its path.

random ideas:
firing event propagates it to all children first, and then to parent and all its other children (except self)

rules can be enabled disabled added and removed by changing the RulesComponent
if an entity has a rule, it may override/modify stuff inherited from parents. Like Magic: "Can attack as though it didn't have defender" (Disables DefenderRule for entity)
*/
interface ECSEntityContainer {
    // CardZones, Grids...
    // maybe also spaces (like Pandemic? or Alchemists?) hard to tell difference between a component and entity sometimes

    fun entities(): List<ECSEntity>
    fun pathFor(entity: ECSEntity): String
    fun get(path: String): ECSEntity
    // add, remove methods?
}
class ECSGrid(val grid: Grid<ECSEntity>): Grid<ECSEntity> by grid, ECSEntityContainer {
    override fun entities(): List<ECSEntity> = grid.all().map { it.value }
    override fun pathFor(entity: ECSEntity): String {
        return grid.all().find { it.value == entity }?.let {
            "${it.y},${it.x}"
        } ?: throw IllegalStateException("Unable to find $entity in $this")
    }

    override fun get(path: String): ECSEntity {
        val coordinates = path.split(",").map { it.toInt() }
        return grid.get(coordinates[1], coordinates[0])
    }
}

interface ECSEntityHolder {
    var entity: ECSEntity
}
interface ECSTag {
    val name: String
}
interface ECSComponent<E> {
    val owner: ECSEntity
    val name: String
    val tags: List<ECSTag>
    var component: E?
    fun view(viewScope: ECSViewScope): Any?
}
fun ECSComponent<Int>.nextPlayer() {
    val eliminations = owner.root[ECSEliminations]
    if (eliminations.isGameOver()) return
    this.component = eliminations.nextPlayer(this.component!!)
}

class ECSComponentImpl<E>(override val owner: ECSEntity, override val name: String): ECSComponent<E> {
    lateinit var publicView: ECSViewFunction<E>
    lateinit var privateViews: MutableMap<Int, ECSViewFunction<E>>

    override var component: E? = null
    override fun view(viewScope: ECSViewScope): Any? {
        val value = component ?: return null
        val viewFunction = privateViews[viewScope.viewer] ?: publicView
        return viewFunction.invoke(viewScope, value)
    }

    override val tags: List<ECSTag> get() = TODO("Not yet implemented")
}

interface ECSEntity {
    val tags: List<ECSTag>
    val components: Map<ECSAccessor<out Any>, ECSComponent<Any>>
    val container: ECSComponent<out ECSEntityContainer>?
    val parent: ECSEntity? // or ECSComponent?
    val root: ECSEntity
    fun path(): String // things like `/players/0/hand/6` or `/grid/3/5/`. A unique ID/path for where this entity exists.
    operator fun <T: Any> get(accessor: ECSAccessor<T>): T {
        if (!has(accessor.key)) {
            throw IllegalArgumentException("Key ${accessor.key.name} is missing in component list for entity $this")
        }
        return components.getValue(accessor.key).component!! as T
    }

    operator fun <T: Any> set(accessor: ECSAccessor<T>, value: T) {
        components[accessor.key as ECSAccessor<Any>]!!.component = value
    }

    fun <T: Any> component(accessor: ECSAccessor<T>): ECSComponent<T> = components.getValue(accessor.key) as ECSComponent<T>

    fun has(accessor: ECSAccessor<*>): Boolean {
        return components.containsKey(accessor.key) && components[accessor.key]?.component != null
    }
    fun doesNotHave(accessor: ECSAccessor<*>): Boolean = !has(accessor.key)
    fun allChildren(includingSelf: Boolean): Sequence<ECSEntity> = sequence {
        if (includingSelf) yield(this@ECSEntity)
        yieldAll(components.values.mapNotNull { it.component }.filterIsInstance<ECSEntityContainer>().flatMap { it.entities() })
    }

    fun <T: Any> getOrNull(accessor: ECSAccessor<T>): T? = components[accessor]?.component as T?

    fun view(viewScope: ViewScope<ECSEntity>): Any?
}
class ECSSimpleEntity(override val parent: ECSEntity?, override val container: ECSComponent<out ECSEntityContainer>?): ECSEntity, ECSEntityCreating {
    override val tags: List<ECSTag> get() = TODO("Not yet implemented")
    override val components: MutableMap<ECSAccessor<out Any>, ECSComponent<Any>> = mutableMapOf()
    override val root: ECSEntity get() = parent?.root ?: this

    override fun path(): String {
        return if (container == null) "/" else {
            container.owner.path() + container.name + "/" + container.component?.pathFor(this)
        }
    }

    fun build(builder: ECSEntityBuilder.() -> Unit): ECSEntity {
        builder.invoke(ECSEntityFactory(this))
        return this
    }

    override fun <T: Any> has(component: ECSComponentBuilder<T>) {
        components[component.key] = component.buildFor(this) as ECSComponent<Any>
    }

    override fun mayHave(component: ECSComponentBuilder<out Any>) = has(component)

    override fun toString(): String = "Entity('${path()}')"

    override fun view(viewScope: ViewScope<ECSEntity>): Any? { // Should this ever return null?
        return components.entries.associate {
            it.key.name to it.value.view(ECSViewContext(this, viewScope))
        }.filterValues { it != HiddenECSValue }
    }
}


/*
Disable entity? Disable component?
Game-in-game. Like playing rock-paper-scissors to determine who starts in Tic-Tac-Toe, or playing Tic-Tac-Toe where the winner gets to make a move in another game.
- Separate which entity is being played, if there's for example 3 TTT games going on at the same time. In which game are you playing at position (1, 2)?

Entity-Component-System vs. Entity-Component-Rules? Cardshifter's use of "Systems" was only listening for events and handling them, and executing other events.

AnimationID component, AnimationIDs like Hanabi -- which card gets which should be random and does not need to be persisted (unless there's an undo button...?)


Entity has rule that allows action, or Entity has action ?
  It feels like "Entity has action" makes a lot of sense, and that some rules somewhere define whether or not this action is allowed.
  Also, saying that Entity has action does help for frontend that can uniquely identify the entity using its path.
    Although this could be possible even if actions are not contained on entities but entities are options/choices instead, it is still possible for frontend to highlight.
  But actions...
    - in Splendor, cards could have the buy/reserve actions, while the game/board has the Bank which is a resource map, and there resources could be indicated with `/bank/red` for example.
    - in Backgammon, game could have the roll action, each point-thingy can have move actions (because it's only one move action per thingy, not one for each ball is located on the thingy)
    - in Spice Road, using the entity path will actually help the wrong things from being highlighted.
    - in Spice Road, it's really helpful to show "before" and "after", can this be displayed somehow? Some kind of helper entity?
Note that entity paths SHOULD NOT BE SERIALIZED AND STORED IN REPLAY. Risk of refactoring is way too big. The existing serialization system works very well already.
But how then to do it if you have a game-in-game situation? Say 3 TTT's, but like UTTT so depending on which column you play in, the opponent gets to make a move at another one.
 But that's exaclty how UTTT works...
 I think maybe it's okay for entity paths to be serialized anyway. They are after all a unique identifier.
 `/board/1/2:play` can be the name for an actionType.

Tricky things to solve:
"Whenever you deal combat damage to an enemy, you may discard a card. If you do, deal 2 extra damage" (still just one damage event)
"Whenever you give a clue, you may discard a card. If you do, take an extra turn"

*/
