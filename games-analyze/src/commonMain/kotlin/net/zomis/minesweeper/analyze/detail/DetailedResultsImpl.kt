package net.zomis.minesweeper.analyze.detail

import net.zomis.minesweeper.analyze.AnalyzeResult

class DetailedResultsImpl<T>(
    override val analyze: AnalyzeResult<T>,
    proxies: Map<T, FieldProxy<T>>,
    override val proxyCount: Int
) : DetailedResults<T> {
    private val proxies: Map<T, ProbabilityKnowledge<T>> = proxies

    override fun allProxies(): Collection<ProbabilityKnowledge<T>> {
        return proxies.values
    }

    override fun getProxyFor(field: T): ProbabilityKnowledge<T> {
        return proxies[field]!!
    }

    override val proxyMap: Map<T, ProbabilityKnowledge<T>>
        get() = proxies
}