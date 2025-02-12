package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.CacheLoader

fun interface CoroutineCacheLoader<K : Any, V> {
    /**
     * The coroutines implementation of [CacheLoader.load]
     */
    suspend fun load(key: K): V

    /**
     * The coroutines implementation of [CacheLoader.reload]
     */
    suspend fun reload(
        key: K,
        oldValue: V & Any,
    ): V = load(key)
}
