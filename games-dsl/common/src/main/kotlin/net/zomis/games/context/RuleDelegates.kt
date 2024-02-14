package net.zomis.games.context

import net.zomis.games.rules.Rule
import net.zomis.games.rules.RuleSpec
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class RuleSpecDelegateProvider<Model : Any, Owner>(
    private val ctx: Context,
    private val dsl: RuleSpec<Model, Owner>,
) : PropertyDelegateProvider<Any?, RuleSpecDelegate<Model, Owner>> {
    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): RuleSpecDelegate<Model, Owner> {
        return RuleSpecDelegate<Model, Owner>(property.name, dsl)
    }
}

class RuleSpecDelegate<Model : Any, Owner>(
    private val name: String,
    private val dsl: RuleSpec<Model, Owner>
) : ReadOnlyProperty<Any?, RuleSpec<Model, Owner>> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): RuleSpec<Model, Owner> {
        return dsl
    }
}

class RuleDelegateProvider<Model : Any, Owner>(
    private val ctx: Context,
    private val owner: Owner,
    private val dsl: RuleSpec<Model, Owner>,
) : PropertyDelegateProvider<Any?, RuleDelegateProvider<Model, Owner>>, ReadOnlyProperty<Any?, Rule<Model, Owner>> {
    private lateinit var name: String

    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): RuleDelegateProvider<Model, Owner> {
        this.name = property.name
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Rule<Model, Owner> {
        return Rule(owner, dsl)
    }
}
