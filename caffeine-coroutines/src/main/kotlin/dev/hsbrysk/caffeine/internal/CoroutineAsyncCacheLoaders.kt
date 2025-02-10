package dev.hsbrysk.caffeine.internal

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import dev.hsbrysk.caffeine.CoroutineCacheBulkLoader
import dev.hsbrysk.caffeine.CoroutineCacheLoader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

internal fun <K : Any, V : Any> CoroutineCacheLoader<K, V>.toAsyncCacheLoader(): AsyncCacheLoader<K, V> =
    if (this is CoroutineCacheBulkLoader<K, V>) {
        CoroutineAsyncCacheBulkLoader(this)
    } else {
        CoroutineAsyncCacheLoader(this)
    }

@Suppress("OPT_IN_USAGE")
private open class CoroutineAsyncCacheLoader<K : Any, V : Any>(private val loader: CoroutineCacheLoader<K, V>) :
    AsyncCacheLoader<K, V> {

    override fun asyncLoad(
        key: K,
        executor: Executor,
    ): CompletableFuture<out V?> = GlobalScope.future(executor.asCoroutineDispatcher()) { loader.load(key) }

    override fun asyncReload(
        key: K,
        oldValue: V,
        executor: Executor,
    ): CompletableFuture<out V?> = GlobalScope.future(executor.asCoroutineDispatcher()) {
        loader.reload(key, oldValue)
    }
}

@Suppress("OPT_IN_USAGE")
private class CoroutineAsyncCacheBulkLoader<K : Any, V : Any>(private val loader: CoroutineCacheBulkLoader<K, V>) :
    CoroutineAsyncCacheLoader<K, V>(loader) {

    override fun asyncLoadAll(
        keys: Set<K>,
        executor: Executor,
    ): CompletableFuture<Map<out K, V>> = GlobalScope.future(executor.asCoroutineDispatcher()) { loader.loadAll(keys) }
}
