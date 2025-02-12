package dev.hsbrysk.caffeine.internal

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import dev.hsbrysk.caffeine.CoroutineCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

internal class CoroutineCacheImpl<K : Any, V>(private val cache: AsyncCache<K, V>) : CoroutineCache<K, V> {
    override suspend fun getIfPresent(key: K): V? = cache.getIfPresent(key)?.await()

    override suspend fun get(
        key: K,
        mappingFunction: suspend (K) -> V,
    ): V = coroutineScope {
        cache.get(key) { k, _ -> future { mappingFunction(k) } }.await()
    }

    override suspend fun getAll(
        keys: Iterable<K>,
        mappingFunction: suspend (Iterable<K>) -> Map<K, V & Any>,
    ): Map<K, V & Any> = coroutineScope {
        cache.getAll(keys) { k, _ -> future { mappingFunction(k) } }.await()
    }

    override fun put(
        key: K,
        value: V & Any,
    ) {
        cache.put(key, CompletableFuture.completedFuture(value))
    }

    override fun putAll(map: Map<K, V & Any>) {
        map.entries.forEach { cache.put(it.key, CompletableFuture.completedFuture(it.value)) }
    }

    override fun synchronous(): Cache<K, V> = cache.synchronous()

    override fun asynchronous(): AsyncCache<K, V> = cache
}
