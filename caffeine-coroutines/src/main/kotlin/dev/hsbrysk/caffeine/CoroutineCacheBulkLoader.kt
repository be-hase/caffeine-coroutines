package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.CacheLoader

interface CoroutineCacheBulkLoader<K : Any, V : Any> : CoroutineCacheLoader<K, V> {
    /**
     * The coroutines implementation of [CacheLoader.loadAll]
     */
    suspend fun loadAll(keys: Set<K>): Map<K, V>
}
