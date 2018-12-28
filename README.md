# aria2lib

This is a small module (Android library only) that allows to download and manage an [aria2](https://github.com/aria2/aria2) executable. It is used in [Aria2Android](https://github.com/devgianlu/Aria2Android) and [Aria2App](https://github.com/devgianlu/Aria2App).

## How to

- Add as Git submodule in your project (`git submodule add https://github.com/devgianlu/aria2lib`)
- Add the Gradle module to your `settings.gradle`:
```
include ':aria2lib', ...
project(':aria2lib').projectDir = new File('./aria2lib')
```
- Add it as a dependency:
```
dependencies {
    api project(':aria2lib')
    ...
}
```