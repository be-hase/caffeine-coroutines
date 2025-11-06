package dev.hsbrysk.caffeine.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.message
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.Collections
import java.util.function.Function
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class CoroutineCacheImplTest {
    private val target = Caffeine.newBuilder().buildCoroutine<String, String?>()
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
        fun getIfPresent() = runBlocking {
            assertThat(target.getIfPresent("1")).isNull()
            target.get("1", mappingFunction)
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(invokedKeys).containsExactly("1")
        }

        @Test
        fun get() = runBlocking {
            assertThat(target.get("1", mappingFunction)).isEqualTo("value-1")
            assertThat(target.get("1", mappingFunction)).isEqualTo("value-1")
            assertThat(invokedKeys).containsExactly("1")
        }

        @Test
        fun getAll() = runBlocking {
            val mappingFunction: suspend (Iterable<String>) -> Map<String, String> = { keys ->
                delay(1000)
                invokedKeys.addAll(keys)
                keys.associateWith { "value-$it" }
            }

            assertThat(target.getAll(listOf("1", "2"), mappingFunction)).containsAtLeast(
                "1" to "value-1",
                "2" to "value-2",
            )
            assertThat(target.getAll(listOf("1", "3"), mappingFunction)).containsAtLeast(
                "1" to "value-1",
                "3" to "value-3",
            )
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("2")).isEqualTo("value-2")
            assertThat(target.getIfPresent("3")).isEqualTo("value-3")
            assertThat(invokedKeys).containsExactly("1", "2", "3")
        }

        @Test
        fun put() = runBlocking {
            assertThat(target.getIfPresent("1")).isNull()
            target.put("1", "value-1")
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
        }

        @Test
        fun putAll() = runBlocking {
            assertThat(target.getIfPresent("1")).isNull()
            assertThat(target.getIfPresent("2")).isNull()
            target.putAll(mapOf("1" to "value-1", "2" to "value-2"))
            assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            assertThat(target.getIfPresent("2")).isEqualTo("value-2")
        }

        @Test
        fun exception() {
            // If an error occurs, it will not be cached.
            assertFailure { runBlocking { target.get("1", exceptionFunction) } }
            runBlocking {
                assertThat(target.getIfPresent("1")).isNull()
            }
        }

        @Test
        fun `null behavior`() = runBlocking {
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
            assertThat(target.synchronous().get("1") { "value-$it" }).isEqualTo("value-1")
            runBlocking {
                assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            }
        }

        @Test
        fun asynchronous() {
            assertThat(target.asynchronous()).isInstanceOf(AsyncCache::class.java)
            assertThat(target.asynchronous().get("1", Function { "value-$it" }).get()).isEqualTo("value-1")
            runBlocking {
                assertThat(target.getIfPresent("1")).isEqualTo("value-1")
            }
        }
    }

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
        fun `once executed when error`() {
            // Even if executed almost simultaneously, it should only run once when error.

            val loader: suspend (String) -> String = {
                delay(2000)
                error(currentCoroutineContext()[CoroutineName]!!.name)
            }

            val result1 = GlobalScope.async(CoroutineName("exec-1")) {
                target.get("key", loader)
            }
            val result2 = GlobalScope.async(CoroutineName("exec-2")) {
                delay(1000)
                target.get("key", loader)
            }

            runBlocking {
                assertFailure {
                    result1.await()
                }.given {
                    assertThat(it)
                        .isInstanceOf(IllegalStateException::class.java)
                        .message().isEqualTo("exec-1")
                }
                assertFailure {
                    result2.await()
                }.given {
                    assertThat(it)
                        .isInstanceOf(CancellationException::class.java)
                        .message().isNull()
                    assertThat(it.cause).isNotNull()
                        .isInstanceOf(CancellationException::class.java)
                        .message().isNull()
                    assertThat(it.cause!!.cause).isNull()
                }
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
                    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty()
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

        @Test
        fun `get - check exception and cancel behavior`() {
            val scope = CoroutineScope(EmptyCoroutineContext)
            val job = scope.async {
                try {
                    target.get("1", exceptionFunction)
                } catch (expected: IllegalArgumentException) {
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
                    target.getAll(listOf("1", "2")) {
                        throw IllegalArgumentException()
                    }
                } catch (expected: IllegalArgumentException) {
                    println(expected)
                }
                "Result"
            }
            val result = runBlocking { job.await() }
            assertThat(result).isEqualTo("Result")
        }
    }
}
