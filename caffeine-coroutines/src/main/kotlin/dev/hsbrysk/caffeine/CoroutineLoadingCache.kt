package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

interface CoroutineLoadingCache<K : Any, V : Any> : CoroutineCache<K, V> {
    /**
     * The coroutines implementation of [LoadingCache.get]
     */
    suspend fun get(key: K): V?

    /**
     * The coroutines implementation of [LoadingCache.getAll]
     * Attention: It is executed for each key using [CoroutineScope.async].
     *   There is a cooperative cancel, so if an error occurs on any key, the other async executions are canceled.
     */
    suspend fun getAll(keys: Iterable<K>): Map<K, V>

    /**
     * Returns the [LoadingCache]
     */
    override fun synchronous(): LoadingCache<K, V?>

    /**
     * Returns the [AsyncLoadingCache]
     */
    override fun asynchronous(): AsyncLoadingCache<K, V?>
}
