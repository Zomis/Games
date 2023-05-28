package net.zomis.minesweeper.analyze

/**
 * A group of fields that have common rules
 *
 * @author Simon Forsberg
 * @param <T> The field type
</T> */
data class FieldGroup<T>(private val _fields: ArrayList<T>) : Iterable<T> {
    var probability = 0.0
        private set
    var solutionsKnown = 0
        private set

    constructor(copyOf: FieldGroup<T>) : this(ArrayList(copyOf.fields))
    constructor(fields: Collection<T>) : this(ArrayList(fields))
    internal constructor(size: Int) : this(ArrayList(size))
    val fields: List<T> get() = _fields

    fun informAboutSolution(minesForGroup: Int, solution: Solution<T>, total: Double) {
        if (minesForGroup == 0) {
            return
        }
        probability = probability + solution.nCr() / total * minesForGroup / this.size
        solutionsKnown++
    }

    val size get() = fields.size
    override fun iterator(): Iterator<T> = fields.iterator()

    override fun toString(): String {
        if (this.size > 8) {
            return "(" + this.size + " FIELDS)"
        }
        val str = StringBuilder()
        for (field in this) {
            if (str.isNotEmpty()) {
                str.append(" + ")
            }
            str.append(field)
        }
        return "($str)"
    }

    fun isNotEmpty(): Boolean = fields.isNotEmpty()

    internal fun retainAll(fields: FieldGroup<T>) {
        this._fields.retainAll(fields._fields)
    }
    internal fun removeAll(fields: FieldGroup<T>) {
        this._fields.removeAll(fields._fields)
    }
    internal fun add(element: T) {
        this._fields.add(element)
    }

    val isEmpty: Boolean get() = fields.isEmpty()

    companion object {
        // TODO: Use composition over inheritance. Perhaps switch to using `HashSet` (even though it will be less flexible)
        private const val serialVersionUID = 4172065050118874050L
    }
}