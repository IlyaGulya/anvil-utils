# Anvil Utils

Anvil Utils is a library that provides a set of annotations to simplify the development of modular applications with
Dagger and Square Anvil.

## Features

- `@ContributesAssistedFactory` - automatically generates assisted factories for annotated classes and contributes them
  to the specified scope as bindings of the provided factory type.

## Compatibility Map

| Anvil Utils Version    | Anvil Version   | Plugin ID             |
|------------------------|-----------------|----------------------|
| 0.1.0                  | 2.4.2           | com.squareup.anvil   |
| 0.2.0-beta01           | 2.5.0-beta09    | com.squareup.anvil   |
| 0.3.0-beta02 and later | 0.3.3 and later | dev.zacsweers.anvil  |

## Getting Started

### KSP

1. Follow Zac Sweer's [guide](https://www.zacsweers.dev/preparing-for-k2/#anvil) on how to prepare your project for
   Anvil with KSP and K2.
2. Ensure you have at least `0.3.3` version of Zac Sweer's Anvil in your project.
3. Ensure you have `ksp` plugin applied to your project:
    ```kotlin
    plugins {
        id("com.google.devtools.ksp")
    }
    ```

4. Add the following dependencies to your project:
    ```kotlin
    dependencies {
        implementation("me.gulya.anvil:annotations:0.3.0-beta02")
        ksp("me.gulya.anvil:compiler:0.3.0-beta02")
    }
    ```

5. Enable Anvil in your project by applying the Anvil Gradle plugin:
    ```kotlin
    plugins {
        id("dev.zacsweers.anvil") version "0.3.3"
    }
    ```

6. Enable KSP and Dagger factory generation in `Anvil`:
    ```kotlin
    anvil {
        useKsp(
            contributesAndFactoryGeneration = true,
        )
        generateDaggerFactories = true
    }
    ```

### Without KSP

1. Add the following dependencies to your project:
    ```kotlin
    dependencies {
        implementation("me.gulya.anvil:annotations:0.1.0")
        anvil("me.gulya.anvil:compiler:0.1.0")
    }
    ```
2. Enable Anvil in your project by applying the Anvil Gradle plugin:
    ```kotlin
    plugins {
        id("dev.zacsweers.anvil") version "0.3.3"
    }
    ```

3. Enable Dagger factory generation in `Anvil`:
    ```kotlin
    anvil {
        generateDaggerFactories = true
    }
    ```

## @ContributesAssistedFactory

`@ContributesAssistedFactory` is an annotation that helps to automatically generate
assisted factories for annotated classes and contribute them to the specified scope
as bindings of the provided factory type.

### Motivation

When building modular applications with Dagger, it's common to define an API module with public interfaces
and a separate implementation module with concrete classes. Assisted injection is a useful pattern for creating
instances of classes with a mix of dependencies provided by Dagger and runtime parameters.
However, using Dagger's @AssistedFactory requires the factory interface and the implementation
class to be in the same module, which breaks the separation between API and implementation.

`@ContributesAssistedFactory` solves this problem by allowing to declare the bound type (factory interface) in the
API module and generate the actual factory implementation in the implementation module.

### Usage

1. Define your bound type (factory interface) in the API module:

```kotlin
interface MyClass

interface MyClassFactory {
    fun create(param1: String, param2: Int): MyClass
}
```

2. Annotate your implementation class with `@ContributesAssistedFactory` in the implementation module:

```kotlin
@ContributesAssistedFactory(AppScope::class, MyClassFactory::class)
class DefaultMyClass @AssistedInject constructor(
    @Assisted param1: String,
    @Assisted param2: Int
) : MyClass
```

3. The following factory will be generated, implementing MyClassFactory:

```kotlin
@ContributesBinding(AppScope::class, MyClassFactory::class)
@AssistedFactory
interface DefaultMyClass_AssistedFactory : MyClassFactory {
    override fun create(param1: String, param2: Int): DefaultMyClass
}
```

### Module structure in the project

- `:compiler` - contains code generators
    - Package `me.gulya.anvil.utils.ksp` - KSP code generators
    - Package `me.gulya.anvil.utils.embedded` - non-KSP code generators
- `:annotations` - contains annotations supported by this code generator
- `:samples` - sample project with examples of usage
    - `:samples:entrypoint` - entrypoint modules showcasing usage of KSP and non-KSP code generators.
        - `:samples:embedded` - entrypoint module where component merging is done. Depends on non-KSP implementation
          module.
        - `:samples:ksp` - entrypoint module where component merging is done. Depends on KSP implementation module.
    - `:samples:library` - library modules using this code generator.
        - `:samples:library:api` - API module with factory interfaces.
        - `:samples:library:impl:ksp` - Implementation module using KSP code generator.
        - `:samples:library:impl:embedded` - Implementation module using non-KSP code generator.

### Important notes

- The factory interface method parameters should be annotated with @AssistedKey instead of Dagger's @Assisted because
  Dagger disallow such usage of this annotation.