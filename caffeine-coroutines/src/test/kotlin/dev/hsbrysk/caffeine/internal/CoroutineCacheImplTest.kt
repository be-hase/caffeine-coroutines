package dev.hsbrysk.caffeine.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.hsbrysk.caffeine.buildCoroutine
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
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
import java.util.Collections
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class CoroutineCacheImplTest {
    private val target = Caffeine.newBuilder().buildCoroutine<String, String>()
    private val invokedKeys = Collections.synchronizedList(mutableListOf<String>())
    private val mappingFunction: suspend (String) -> String = {
        delay(2000)
        invokedKeys.add(it)
        "value-$it"
    }
    private val exceptionFunction: suspend (String) -> String = {
        delay(2000)
        throw IllegalArgumentException()
    }

    // Simply testing if the cache is working
    @Nested
    inner class Simple {
        @Test
        fun getIfPresent() = runTest {
            assertThat(target.getIfPresent("1")).isNull()
            target.get("1", mappingFunction)
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(invokedKeys).containsExactly("1")
        }

        @Test
        fun get() = runTest {
            assertThat(target.get("1", mappingFunction)).isEqualTo("value-1")
            assertThat(target.get("1", mappingFunction)).isEqualTo("value-1")
            assertThat(invokedKeys).containsExactly("1")
        }

        @Test
        fun getAll() = runTest {
            val mappingFunction: suspend (Iterable<String>) -> Map<String, String> = { keys ->
                delay(1000)
                invokedKeys.addAll(keys)
                keys.associateWith { "value-$it" }
            }

            assertThat(target.getAll(listOf("1", "2"), mappingFunction)).containsAtLeast(
                "1" to "value-1",
                "2" to "value-2",
            )
            assertThat(target.getAll(listOf("1", "2"), mappingFunction)).containsAtLeast(
                "1" to "value-1",
                "2" to "value-2",
            )
            assertThat(invokedKeys).containsExactly("1", "2")
        }

        @Test
        fun put() = runTest {
            assertThat(target.getIfPresent("1")).isNull()
            target.put("1", "value-1")
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
        }

        @Test
        fun putAll() = runTest {
            assertThat(target.getIfPresent("1")).isNull()
            assertThat(target.getIfPresent("2")).isNull()
            target.putAll(mapOf("1" to "value-1", "2" to "value-2"))
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("2")).isEqualTo("value-2")
        }

        @Test
        fun exception() {
            // If an error occurs, it will not be cached.
            assertFailure { runTest { target.get("1", exceptionFunction) } }
            runTest {
                assertThat(target.getIfPresent("1")).isNull()
            }
        }

        @Test
        fun `null behavior`() = runTest {
            // Caffeine cannot cache null values.

            // Since this is Kotlin, I've made it so that it won't compile.
            // target.put("key", null)

            // The return value of the mapping function is nullable.
            // I think this specification allows for the possibility that, after processing, the decision is made not to cache the result.
            // Therefore, I will implement the coroutines version in the same way.
            assertThat(
                target.get("1") {
                    invokedKeys.add(it)
                    null
                },
            ).isNull()
            assertThat(
                target.get("1") {
                    invokedKeys.add(it)
                    null
                },
            ).isNull()
            // Since null values are not cached, it is being called twice.
            assertThat(invokedKeys).containsExactly("1", "1")
        }

        @Test
        fun synchronous() {
            assertThat(target.synchronous()).isInstanceOf(Cache::class.java)
        }

        @Test
        fun asynchronous() {
            assertThat(target.asynchronous()).isInstanceOf(AsyncCache::class.java)
        }
    }

    // Testing the behavior of Coroutines just to be sure.
    // In this test, I want to use the actual scheduler as much as possible, so I will not use runTest.
    @DelicateCoroutinesApi
    @Nested
    inner class Complex {
        @Test
        fun `once executed`() {
            // Even if executed almost simultaneously, it should only run once.

            val result1 = GlobalScope.async {
                target.get("1", mappingFunction)
            }
            val result2 = GlobalScope.async {
                target.get("1", mappingFunction)
            }

            runBlocking {
                assertThat(listOf(result1, result2).awaitAll()).containsExactly("value-1", "value-1")
                assertThat(invokedKeys).containsExactly("1")
            }
        }

        @Test
        fun `check coroutines context`() {
            // I will confirm that the coroutine context is being carried over.

            val scope = CoroutineScope(CoroutineName("test") + MDCContext(mapOf("hoge" to "hoge")))
            val result1 = scope.async {
                target.get("1") {
                    assertThat(coroutineContext[CoroutineName]).isEqualTo(CoroutineName("test"))
                    assertThat(MDC.getCopyOfContextMap()).containsAtLeast("hoge" to "hoge")
                    "value-$it"
                }
            }
            val result2 = GlobalScope.async {
                target.get("2") {
                    assertThat(coroutineContext[CoroutineName]).isNull()
                    assertThat(MDC.getCopyOfContextMap()).containsAtLeast("hoge" to "hoge")
                    "value-$it"
                }
            }

            runBlocking {
                assertThat(listOf(result1, result2).awaitAll()).containsExactly("value-1", "value-2")
            }
        }

        @Test
        fun `cooperative cancel`() {
            // Cancellation within the same scope propagates.

            val scope = CoroutineScope(EmptyCoroutineContext)
            val result = scope.async {
                target.get("1", mappingFunction)
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
                target.get("1", mappingFunction)
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
                target.get("1", mappingFunction)
            }
            val scope2 = CoroutineScope(EmptyCoroutineContext)
            scope2.launch {
                throw IllegalArgumentException()
            }

            runBlocking {
                assertThat(result1.await()).isEqualTo("value-1")
            }
        }
    }
}
