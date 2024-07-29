package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.Caffeine
import dev.hsbrysk.caffeine.internal.CoroutineCacheImpl
import dev.hsbrysk.caffeine.internal.CoroutineLoadingCacheImpl
import dev.hsbrysk.caffeine.internal.toAsyncCacheLoader

/**
 * Build [CoroutineCache]
 */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildCoroutine(): CoroutineCache<K, V> =
    CoroutineCacheImpl(this.buildAsync())

/**
 * Build [CoroutineLoadingCache]
 */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildCoroutine(
    loader: CoroutineCacheLoader<K, V>,
): CoroutineLoadingCache<K, V> = CoroutineLoadingCacheImpl(
    CoroutineCacheImpl(this.buildAsync(loader.toAsyncCacheLoader())),
    loader,
)
