package net.zomis.games.context

class EntityList<T : Entity>(ctx: Context) : Entity(ctx) {

    private val list = mutableListOf<T>()

    fun add(producer: (Context) -> T) {
        val delegateFactory = DelegateFactory(ctx, { c -> ComponentDelegate(producer.invoke(c)) }, { it.value }, { d, v -> d.value = v })
            .also { it.name = list.size }
        val newContext by delegateFactory
        list.add(newContext)
    }

    fun isEmpty(): Boolean = list.isEmpty()

}