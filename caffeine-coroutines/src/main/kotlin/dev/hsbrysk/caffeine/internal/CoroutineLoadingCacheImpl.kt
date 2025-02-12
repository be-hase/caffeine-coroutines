package dev.hsbrysk.caffeine.internal

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.LoadingCache
import dev.hsbrysk.caffeine.CoroutineCache
import dev.hsbrysk.caffeine.CoroutineCacheBulkLoader
import dev.hsbrysk.caffeine.CoroutineCacheLoader
import dev.hsbrysk.caffeine.CoroutineLoadingCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class CoroutineLoadingCacheImpl<K : Any, V>(
    private val cache: CoroutineCache<K, V>,
    private val loader: CoroutineCacheLoader<K, V>,
) : CoroutineLoadingCache<K, V>,
    CoroutineCache<K, V> by cache {
    private val bulkLoader = loader as? CoroutineCacheBulkLoader<K, V>

    override suspend fun get(key: K): V = cache.get(key) { loader.load(it) }

    @Suppress("UNCHECKED_CAST")
    override suspend fun getAll(keys: Iterable<K>): Map<K, V & Any> = if (bulkLoader == null) {
        coroutineScope {
            keys.map { async { it to get(it) } }
                .awaitAll()
                .toMap()
                .filterValues { it != null } as Map<K, V & Any>
        }
    } else {
        cache.getAll(keys) { bulkLoader.loadAll(keys.toSet()) }
    }

    override fun synchronous(): LoadingCache<K, V> = asynchronous().synchronous()

    override fun asynchronous(): AsyncLoadingCache<K, V> = cache.asynchronous() as AsyncLoadingCache<K, V>
}
