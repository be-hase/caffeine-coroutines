package dev.hsbrysk.caffeine.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.github.benmanes.caffeine.cache.Caffeine
import dev.hsbrysk.caffeine.buildCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Collections

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
        assertThat(invokedKeys).containsExactly("1", "2", "3")
    }

    @Test
    fun `getAll with runBlocking`() {
        runBlocking {
            assertThat(target.getAll(listOf("1", "2", "3"))).containsAtLeast("1" to "value-1", "2" to "value-2")
        }
        // assertionは書かないが、CoroutineScope.asyncなので並列で動いています
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
