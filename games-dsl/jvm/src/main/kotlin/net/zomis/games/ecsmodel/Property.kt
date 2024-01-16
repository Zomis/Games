package net.zomis.games.ecsmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zomis.games.cards.CardZone
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
* Properties should be:
* - copyable
* - viewable (one view per player, "no player" knows if and only if everyone knows)
*/
interface PropertyDelegate<T> : PropertyDelegateProvider<GameModelEntity, ReadWriteProperty<GameModelEntity, T>>, ReadWriteProperty<GameModelEntity, T>
interface PropertyProvider<T> : PropertyDelegateProvider<GameModelEntity, GameModelProperty<T>> {
    fun hidden(): PropertyProvider<T>
}
fun <T> PropertyProvider<CardZone<T>>.privateWithPublicSize(): PropertyProvider<CardZone<T>> {
    TODO("Not yet implemented")
}

interface ContainerProperty {
    fun resolve(path: String): GameModelEntity? // should these also be able to resolve to GameModelProperty?
    fun getAllChildren(): List<GameModelEntity>
}

class GameModelProperty<ModelType> : ReadWriteProperty<GameModelEntity, ModelType> {
    private val known: KnownMap = KnownMapList()
    val value: ModelType get() = TODO()

    // owner: Entity ? <-- an entity should know its properties, but probably not the other way around
    override fun getValue(thisRef: GameModelEntity, property: KProperty<*>): ModelType {
        TODO("Not yet implemented")
    }

    override fun setValue(thisRef: GameModelEntity, property: KProperty<*>, value: ModelType) {
        TODO("Not yet implemented")
    }

}
class GameViewableProperty<ModelType, ViewType> {
    // one view per player + one view for "no player"
    val views: MutableList<StateFlow<ViewType>> = mutableListOf()
}

class InitializedProperty<T>(val property: KProperty<T>, val value: T)
infix fun <T> KProperty<T>.value(value: T): InitializedProperty<T> = InitializedProperty(this, value)


//context(Game)
class GamePropertyDelegate<ModelType>(
    private val entity: GameModelEntity,
    private val function: () -> ModelType,
) : PropertyDelegate<ModelType> {
    private val knownMap = KnownMap.Public
    private var stateFlow: MutableStateFlow<ModelType>? = null

    override fun provideDelegate(thisRef: GameModelEntity, property: KProperty<*>): GamePropertyDelegate<ModelType> {
        this.stateFlow = MutableStateFlow(function.invoke())
        return this
    }

    override fun getValue(thisRef: GameModelEntity, property: KProperty<*>): ModelType {
        return stateFlow!!.value!!
    }

    override fun setValue(thisRef: GameModelEntity, property: KProperty<*>, value: ModelType) {
        this.stateFlow!!.value = value
    }

    fun hidden(): GamePropertyDelegate<ModelType> = TODO()
    fun stateFlow(): StateFlow<ModelType>? = stateFlow?.asStateFlow()

}

internal class EcsModelCardZoneDelegate<T>(entity: GameModelEntity) : PropertyDelegate<CardZone<T>> {
    private val knownMap = KnownMap.Public
    private val cardZone = CardZone<T>()

    override fun provideDelegate(thisRef: GameModelEntity, property: KProperty<*>): EcsModelCardZoneDelegate<T> {
        return this
    }

    override fun getValue(thisRef: GameModelEntity, property: KProperty<*>): CardZone<T> {
        return cardZone
    }

    override fun setValue(thisRef: GameModelEntity, property: KProperty<*>, value: CardZone<T>) {
        TODO("Not yet implemented")
    }

}

internal class EcsModelPropertyDelegate()

class Property {



}