package dev.hsbrysk.caffeine

import com.github.benmanes.caffeine.cache.Caffeine
import dev.hsbrysk.caffeine.internal.CoroutineCacheImpl
import dev.hsbrysk.caffeine.internal.CoroutineLoadingCacheImpl

/**
 * Build [CoroutineCache]
 */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildCoroutine(): CoroutineCache<K, V> {
    return CoroutineCacheImpl(this.buildAsync())
}

/**
 * Build [CoroutineLoadingCache]
 */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildCoroutine(loader: suspend (K) -> V?): CoroutineLoadingCache<K, V> {
    return CoroutineLoadingCacheImpl(
        this.buildCoroutine(),
        loader,
    )
}
