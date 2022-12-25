# Opia

## Features
### End-to-End encrypted chat
- Using [Nikea](https://github.com/julius-b/nikea-kt) handshakes.

## App Design
- Kotlin Multiplatform: Android & Desktop support
- MVI Pattern using [MVIKotlin](https://arkivanov.github.io/MVIKotlin/)

## TODO
- DI
- determine Retrofit host config using Build config (DEBUG/PROD)
- logging library
- `stringResource(id)` for UI
- use [kotlinx.serialization](https://www.jonker.co.nz/posts/switching-to-kotlinx-serialization/) with [Ktorfit](https://foso.github.io/Ktorfit/responseconverter/)

## package
`./gradlew packageAppImage`

`./gradlew :desktop:packageDistributionForCurrentOS`
