package net.zomis.games.common

class Node<T>(val value: T)
class Vertex<T, E>(val data: E, val from: Node<T>, val to: Node<T>)
class Graph<T, E> {

    val nodes = mutableListOf<Node<T>>()
    val vertices = mutableMapOf<Node<T>, MutableList<Vertex<T, E>>>()

    fun node(value: T): Node<T> {
        val node = findNode(value)
        if (node != null) return node
        val newNode = Node(value)
        nodes.add(newNode)
        return newNode
    }

    fun createVertex(edgeValue: E, from: Node<T>, to: Node<T>, bidirectional: Boolean) {
        // TODO: Check if vertex exists already
        vertices.getOrPut(from) { mutableListOf() }.add(Vertex(edgeValue, from, to))
        if (bidirectional) {
            vertices.getOrPut(to) { mutableListOf() }.add(Vertex(edgeValue, to, from))
        }
    }

    fun findNode(value: T): Node<T>? = nodes.find { it.value == value }
    fun <R, V> transform(nodeMapper: (T) -> R, edgeMapper: (E) -> V): Graph<R, V> {
        val newGraph = Graph<R, V>()
        val nodeMap = this.nodes.associateWith { Node(nodeMapper(it.value)) }
        newGraph.nodes.addAll(nodeMap.values)

        val vertexMap = this.vertices.mapValues { e ->
            e.value.map { Vertex(edgeMapper(it.data), nodeMap.getValue(it.from), nodeMap.getValue(it.to)) }.toMutableList()
        }.mapKeys { nodeMap.getValue(it.key) }
        newGraph.vertices.putAll(vertexMap)
        return newGraph
    }

}

class Path<T: Any>(start: T) {
    val pos = mutableListOf(start)

    fun add(next: T) {
        pos.add(next)
    }


    // one-directional. No branches. Sort of like an array.
    // Useful for UR, Backgammon, Snakes and Ladders(?)
}

object Paths {
    class GraphBuilder<T: Any, E: Any> {
        var edgeValuesMapper: ((T, T) -> E)? = null
        val graph = Graph<T, E>()

        fun edgeValues(mapper: (T, T) -> E): GraphBuilder<T, E> {
            this.edgeValuesMapper = mapper
            return this
        }
        fun connectOneWay(pair: Pair<T, T>, vararg other: T): GraphBuilder<T, E>
            = connect(pair.first, pair.second.toSingleList() + other.toList(), false)
        fun connectBothWays(pair: Pair<T, T>, vararg other: T): GraphBuilder<T, E>
            = connect(pair.first, pair.second.toSingleList() + other.toList(), true)

        private fun connect(origin: T, destinations: List<T>, bidirectional: Boolean): GraphBuilder<T, E> {
            if (edgeValuesMapper == null) throw IllegalStateException("No edgeValuesMapper set")
            val originNode = graph.node(origin)
            destinations.map { graph.node(it) }.forEach {
                graph.createVertex(edgeValuesMapper!!(originNode.value, it.value), originNode, it, bidirectional)
            }
            return this
        }

        fun chain(bidirectional: Boolean, nodes: List<T>): GraphBuilder<T, E> {
            nodes.windowed(2).forEach { window ->
                this.connect(window[0], window[1].toSingleList(), bidirectional)
            }
            return this
        }

        fun build(): Graph<T, E> = graph
    }
    class PathBuilder<T: Any>(start: T) {
        val path = Path(start)

        fun through(values: Iterable<T>): PathBuilder<T> {
            for (value in values) {
                path.add(value)
            }
            return this
        }
        fun then(value: T): PathBuilder<T> {
            path.add(value)
            return this
        }
        fun build(): Path<T> = path
    }
    fun <T: Any> from(start: T): PathBuilder<T> = PathBuilder(start)
    fun <T: Any, E: Any> graph(): GraphBuilder<T, E> = GraphBuilder()

}