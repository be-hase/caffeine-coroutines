package dev.hsbrysk.caffeine.internal

import dev.hsbrysk.caffeine.CoroutineCache
import dev.hsbrysk.caffeine.CoroutineLoadingCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class CoroutineLoadingCacheImpl<K : Any, V : Any>(
    private val cache: CoroutineCache<K, V>,
    private val loader: suspend (K) -> V?,
) : CoroutineLoadingCache<K, V>,
    CoroutineCache<K, V> by cache {
    override suspend fun get(key: K): V? = cache.get(key, loader)

    @Suppress("UNCHECKED_CAST")
    override suspend fun getAll(keys: Iterable<K>): Map<K, V> = coroutineScope {
        keys.map { async { it to get(it) } }
            .awaitAll()
            .toMap()
            .filterValues { it != null } as Map<K, V>
    }
}
