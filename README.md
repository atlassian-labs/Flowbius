# Flowbius

[![Atlassian license](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

Flowbius provides interoperability extensions for using [Kotlin Flows](https://kotlinlang.org/docs/flow.html) with [Mobius](https://github.com/spotify/mobius). They allow conversion from Flows to Mobius types and vice versa, as well as utilities to setup Mobius loops using Flows.

Flowbius is analogous to what `mobius-rx` provides for RxJava/Mobius interoperability.

## Usage

Flowbius provides converters from Flow types to Mobius types:

```kotlin
// Flow -> EventSource
val eventSource = flowOf(1, 2, 3).asEventSource()

// EventSource -> Flow
val flow = eventSource.asFlow()

// FlowTransformer -> Connectable
val connectable = { source: Flow<String> -> source.map { it.length } }.asConnectable()

// Apply a Connectable to a Flow as a flatMap which merges emissions
val transformedFlow = flow.flatMapMerge(connectable)
```

You can also create a Mobius loop with Flow-based subtype effect handler:

```kotlin
val loop = FlowMobius.loop<Model, Event, Effect>(
  update = UpdateLogic(),
  effectHandler = subtypeEffectHandler {
    addConsumer(::handleEffects)
    addFunction(::effectToEvents)
  }
).startFrom(Model())
```

## Installation

You can retrieve Flowbius from [Maven Central](https://search.maven.org/artifact/com.trello.flowbius/flowbius).

```
implementation 'com.trello.flowbius:flowbius:0.1.2'
```

## Tests

```
$ ./gradlew tests
```

## Contributions

Contributions to Flowbius are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details. 

## License

Copyright (c) 2022 Atlassian and others.
Apache 2.0 licensed, see [LICENSE](LICENSE) file.

<br/> 

[![With ❤️ from Atlassian](https://raw.githubusercontent.com/atlassian-internal/oss-assets/master/banner-cheers.png)](https://www.atlassian.com)