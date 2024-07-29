package dev.hsbrysk.caffeine.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import dev.hsbrysk.caffeine.CoroutineCacheBulkLoader
import dev.hsbrysk.caffeine.CoroutineCacheLoader
import dev.hsbrysk.caffeine.buildCoroutine
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.Duration
import java.util.Collections
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class CoroutineLoadingCacheImplTest {
    private val target = Caffeine.newBuilder().buildCoroutine<String, String> {
        if (it == "error") {
            error("error")
        } else {
            delay(1000)
            invokedKeys.add(it)
            "value-$it"
        }
    }
    private val invokedKeys = Collections.synchronizedList(mutableListOf<String>())

    // Simply testing if the cache is working
    @Nested
    inner class Simple {
        @Test
        fun get() = runTest {
            assertThat(target.get("1")).isEqualTo("value-1")
            assertThat(target.get("1")).isEqualTo("value-1")
            assertThat(target.get("2")).isEqualTo("value-2")
            assertThat(invokedKeys).containsExactly("1", "2")
        }

        @Test
        fun getAll() = runTest {
            assertThat(target.getAll(listOf("1", "2"))).containsAtLeast("1" to "value-1", "2" to "value-2")
            assertThat(target.getAll(listOf("1", "3"))).containsAtLeast("1" to "value-1", "3" to "value-3")
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("2")).isEqualTo("value-2")
            assertThat(target.getIfPresent("3")).isEqualTo("value-3")
            assertThat(invokedKeys).containsExactly("1", "2", "3")
        }

        @Test
        fun synchronous() {
            assertThat(target.synchronous()).isInstanceOf(LoadingCache::class.java)
            assertThat(target.synchronous().get("1")).isEqualTo("value-1")
            runTest {
                assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            }
        }

        @Test
        fun asynchronous() {
            assertThat(target.asynchronous()).isInstanceOf(AsyncLoadingCache::class.java)
            assertThat(target.asynchronous().get("1").get()).isEqualTo("value-1")
            runTest {
                assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            }
        }
    }

    // Testing the behavior of Coroutines just to be sure.
    // In this test, I want to use the actual scheduler as much as possible, so I will not use runTest.
    @Suppress("OPT_IN_USAGE")
    @Nested
    inner class Complex {
        @Test
        fun `once executed`() {
            // Even if executed almost simultaneously, it should only run once.

            val result1 = GlobalScope.async {
                target.get("1")
            }
            val result2 = GlobalScope.async {
                target.get("1")
            }

            runBlocking {
                assertThat(listOf(result1, result2).awaitAll()).containsExactly("value-1", "value-1")
                assertThat(invokedKeys).containsExactly("1")
            }
        }

        @Test
        fun `check coroutines context`() {
            // I will confirm that the coroutine context is being carried over.

            val names = Collections.synchronizedList(mutableListOf<String?>())
            val mdcs = Collections.synchronizedList(mutableListOf<Map<String, String>>())
            val target = Caffeine.newBuilder().buildCoroutine<String, String> {
                if (it == "error") {
                    error("error")
                } else {
                    delay(1000)
                    names.add(coroutineContext[CoroutineName]?.name)
                    mdcs.add(MDC.getCopyOfContextMap())
                    "value-$it"
                }
            }

            val scope = CoroutineScope(CoroutineName("test") + MDCContext(mapOf("hoge" to "hoge")))
            val result1 = scope.async {
                target.get("1")
            }
            val result2 = GlobalScope.async {
                target.get("2")
            }
            runBlocking {
                assertThat(listOf(result1, result2).awaitAll()).containsExactly("value-1", "value-2")
            }
            assertThat(names).containsExactlyInAnyOrder("test", null)
            assertThat(mdcs).containsExactlyInAnyOrder(mapOf("hoge" to "hoge"), null)
        }

        @Test
        fun `cooperative cancel`() {
            // Cancellation within the same scope propagates.

            val scope = CoroutineScope(EmptyCoroutineContext)
            val result = scope.async {
                target.get("1")
            }
            scope.launch {
                throw IllegalArgumentException()
            }

            runBlocking {
                assertFailure { result.await() }
                    .isInstanceOf(CancellationException::class)
            }
        }

        @Test
        fun `with supervisorJob cancel`() {
            // Cancellation within the same scope propagates.

            val scope = CoroutineScope(EmptyCoroutineContext + SupervisorJob())
            val result = scope.async {
                target.get("1")
            }
            scope.launch {
                throw IllegalArgumentException()
            }

            runBlocking {
                assertThat(result.await()).isEqualTo("value-1")
            }
        }

        @Test
        fun `isolated cancel`() {
            // I will confirm that cancellation in a different scope does not propagate.

            val scope1 = CoroutineScope(EmptyCoroutineContext)
            val result1 = scope1.async {
                target.get("1")
            }
            val scope2 = CoroutineScope(EmptyCoroutineContext)
            scope2.launch {
                throw IllegalArgumentException()
            }

            runBlocking {
                assertThat(result1.await()).isEqualTo("value-1")
            }
        }

        @Test
        fun `get - check exception and cancel behavior`() {
            val scope = CoroutineScope(EmptyCoroutineContext)
            val job = scope.async {
                try {
                    target.get("error")
                } catch (expected: IllegalStateException) {
                    println(expected)
                }
                "Result"
            }
            val result = runBlocking { job.await() }
            assertThat(result).isEqualTo("Result")
        }

        @Test
        fun `getAll - check exception and cancel behavior`() {
            val scope = CoroutineScope(EmptyCoroutineContext)
            val job = scope.async {
                try {
                    target.getAll(listOf("1", "2", "error"))
                } catch (expected: IllegalStateException) {
                    println(expected)
                }
                "Result"
            }
            val result = runBlocking { job.await() }
            assertThat(result).isEqualTo("Result")
        }

        @Test
        fun `getAll - runs parallel`() {
            val time = measureTime {
                runBlocking {
                    assertThat(target.getAll((1..10).map { "$it" }))
                        .isEqualTo((1..10).associate { "$it" to "value-$it" })
                }
            }
            // Since it runs in parallel, it should finish in about one second.
            assertThat(time).isLessThan(3.seconds)
        }

        @Test
        fun `getAll error 1`() {
            assertFailure {
                runBlocking { target.getAll(listOf("1", "2", "error")) }
            }.isInstanceOf(IllegalStateException::class)
            runBlocking {
                // Due to cooperative cancellation.
                // Although cooperative cancellation is not necessary, I will keep this behavior for now.
                assertThat(target.getIfPresent("1")).isNull()
                assertThat(target.getIfPresent("2")).isNull()
            }
        }

        @Test
        fun `getAll error 2`() {
            val target = Caffeine.newBuilder().buildCoroutine<String, String> {
                if (it == "error") {
                    delay(1000)
                    error("error")
                } else {
                    invokedKeys.add(it)
                    "value-$it"
                }
            }

            assertFailure {
                runBlocking { target.getAll(listOf("1", "2", "error")) }
            }.isInstanceOf(IllegalStateException::class)
            runBlocking {
                // Cooperative cancellation occurs, but if the call has already completed, it will be cached.
                assertThat(target.getIfPresent("1")).isEqualTo("value-1")
                assertThat(target.getIfPresent("2")).isEqualTo("value-2")
            }
        }
    }

    @Nested
    inner class Bulk {
        private val loader = object : CoroutineCacheBulkLoader<String, String> {
            override suspend fun load(key: String): String {
                invokedKeys.add(key)
                return "value-$key"
            }

            override suspend fun loadAll(keys: Set<String>): Map<String, String> {
                bulkInvokedKeys.addAll(keys)
                return keys.associateWith { "value-$it" }
            }
        }
        private val target = Caffeine.newBuilder().buildCoroutine(loader)
        private val invokedKeys = Collections.synchronizedList(mutableListOf<String>())
        private val bulkInvokedKeys = Collections.synchronizedList(mutableListOf<String>())

        @Test
        fun test() = runBlocking {
            assertThat(target.getAll(listOf("1", "2"))).containsAtLeast("1" to "value-1", "2" to "value-2")
            assertThat(target.get("3")).isEqualTo("value-3")
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("2")).isEqualTo("value-2")
            assertThat(target.getIfPresent("3")).isEqualTo("value-3")
            assertThat(invokedKeys).containsExactly("3")
            assertThat(bulkInvokedKeys).containsExactly("1", "2")
        }
    }

    @Nested
    inner class Reload {
        private val loader = object : CoroutineCacheLoader<String, String> {
            override suspend fun load(key: String): String {
                invokedKeys.add(key)
                return "value-$key"
            }

            override suspend fun reload(
                key: String,
                oldValue: String,
            ): String {
                reloadedKeys.add(key)
                return "value-$key-reloaded"
            }
        }

        private val target = Caffeine.newBuilder().refreshAfterWrite(Duration.ofSeconds(1)).buildCoroutine(loader)
        private val invokedKeys = Collections.synchronizedList(mutableListOf<String>())
        private val reloadedKeys = Collections.synchronizedList(mutableListOf<String>())

        @Test
        fun test() = runBlocking {
            assertThat(target.get("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(invokedKeys).containsExactly("1")
            assertThat(reloadedKeys).isEmpty()

            Thread.sleep(2000)
            // The key is not reloaded unless it is accessed.
            target.getIfPresent("1")
            Thread.sleep(2000)

            assertThat(target.getIfPresent("1")).isEqualTo("value-1-reloaded")
            assertThat(reloadedKeys).containsExactly("1")
        }
    }
}
