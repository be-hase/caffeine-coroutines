package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache

interface CoroutineCache<K : Any, V : Any> {
    /**
     * The coroutines implementation of [Cache.getIfPresent]
     */
    suspend fun getIfPresent(key: K): V?

    /**
     * The coroutines implementation of [Cache.get]
     */
    suspend fun get(
        key: K,
        mappingFunction: suspend (K) -> V?,
    ): V?

    /**
     * The coroutines implementation of [Cache.getAll]
     */
    suspend fun getAll(
        keys: Iterable<K>,
        mappingFunction: suspend (Iterable<K>) -> Map<K, V>,
    ): Map<K, V>

    /**
     * The coroutines implementation of [Cache.put]
     */
    fun put(
        key: K,
        value: V,
    )

    /**
     * The coroutines implementation of [Cache.putAll]
     */
    fun putAll(map: Map<K, V>)

    /**
     * Returns the [Cache]
     */
    fun synchronous(): Cache<K, V?>

    /**
     * Returns the [AsyncCache]
     */
    fun asynchronous(): AsyncCache<K, V?>
}
