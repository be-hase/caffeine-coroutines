# caffeine-coroutines

## Motivation

There is a library called [Caffeine](https://github.com/ben-manes/caffeine),
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

There is no difference from Caffeine.
The only thing you need to know is that by using *`buildCoroutine`*,
you can obtain a coroutine-compatible Cache instance.

```kotlin
// CoroutineCache
Caffeine.newBuilder()
    // ...
    .buildCoroutine<...>() // HERE

// CoroutineLoadingCache
Caffeine.newBuilder()
    // ...
    .buildCoroutine<...> { // HERE
        ...
    }
```

## Contributing

If there are any issues, please feel free to send a pull request.
