# caffeine-coroutines

## Motivation

There is a caching library called [Caffeine](https://github.com/ben-manes/caffeine),
which is used as a de facto standard in Java.

I will make this library usable with Kotlin Coroutines.

## Install

### Gradle

```kotlin
implementation("dev.hsbrysk:caffeine-coroutines:{{version}}")
```

### Maven

```xml

<dependency>
    <groupId>dev.hsbrysk</groupId>
    <artifactId>caffeine-coroutines</artifactId>
    <version>{{version}}</version>
</dependency>
```

## How to use

There is almost no difference from Caffeine.
The only thing you need to know is that by using *`buildCoroutine`*,
you can obtain a coroutine-compatible Cache instance.

```kotlin
suspend fun main() {
    val cache: CoroutineCache<String, String> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .buildCoroutine() // Use buildCoroutine

    val value = cache.get("key") {
        delay(1000) // You can use suspend functions.
        "value"
    }
    println(value)
}
```

Of course, it also supports the Loading Cache style.

```kotlin
suspend fun main() {
    val cache: CoroutineLoadingCache<String, String> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .buildCoroutine { // Use buildCoroutine
            delay(1000) // You can use suspend functions.
            "value"
        }

    val value = cache.get("key")
    println(value)
}
```

## Philosophy

### We will primarily focus on coroutine support

We respect the widely used Caffeine API.

Introducing our own API would confuse users.
It would also make adoption more difficult and would be troublesome when discontinuing it after adoption.

## Contributing

If there are any issues, please feel free to send a pull request.
