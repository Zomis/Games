package net.zomis.minesweeper.analyze.detail

/**
 * Interface strategy for performing a [DetailAnalyze]
 *
 * @author Simon Forsberg
 *
 * @param <T> The field type
</T> */
interface NeighborFind<T> {
    /**
     * Retrieve the neighbors for a specific field.
     *
     * @param field Field to retrieve the neighbors for
     *
     * @return A [Collection] of the neighbors that the specified field has
     */
    fun getNeighborsFor(field: T): Collection<T>?

    /**
     * Determine if a field is a found mine or not
     *
     * @param field Field to check
     *
     * @return True if the field is a found mine, false otherwise
     */
    fun isFoundAndisMine(field: T): Boolean
}