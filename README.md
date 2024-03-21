# Anvil Utils

Anvil Utils is a library that provides a set of annotations to simplify the development of modular applications with Dagger and Square Anvil. 

## Features

- `@ContributesAssistedFactory` - automatically generates assisted factories for annotated classes and contributes them to the specified scope as bindings of the provided factory type.

## Getting Started

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
    id("com.squareup.anvil") version "2.4.2"
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

### Important notes
- The factory interface method parameters should be annotated with @AssistedKey instead of Dagger's @Assisted because Dagger disallow such usage of this annotation.