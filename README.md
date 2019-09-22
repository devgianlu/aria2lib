# aria2lib
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1ea29f828e684589896164e105e1a66b)](https://www.codacy.com/manual/devgianlu/aria2lib?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=devgianlu/aria2lib&amp;utm_campaign=Badge_Grade)
[![Translate - with Stringlate](https://img.shields.io/badge/translate%20with-stringlate-green.svg)](https://lonamiwebs.github.io/stringlate/translate?git=https%3A%2F%2Fgithub.com%2Fdevgianlu%2Faria2lib)

This is a small module (Android library only) that allows to download and manage an [aria2](https://github.com/aria2/aria2) executable. It is used in [Aria2Android](https://github.com/devgianlu/Aria2Android) and [Aria2App](https://github.com/devgianlu/Aria2App).

> This also depends on [CommonUtils](https://github.com/devgianlu/CommonUtils) and [MaterialPreferences](https://github.com/devgianlu/MaterialPreferences). You can find how to include it there, but it is pretty similar to what comes below.

## How to

- Add as Git submodule in your project (`git submodule add https://github.com/devgianlu/aria2lib`)
- Add the Gradle module to your `settings.gradle`:
```
include ':aria2lib', ':MaterialPreferences', ':LovelyMaterialPreferences', ...
project(':MaterialPreferences').projectDir = new File('./MaterialPreferences/library')
project(':LovelyMaterialPreferences').projectDir = new File('./MaterialPreferences/lovelyinput')
project(':aria2lib').projectDir = new File('./aria2lib')
```
- Add it as a dependency:
```
dependencies {
    api project(':aria2lib')
    ...
}
```
