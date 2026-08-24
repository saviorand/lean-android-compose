<a name="readme-top"></a>

<div align="center">
  <h3 align="center">lean-android-compose</h3>

  <p align="center">
    A Compose app whose computation runs in Lean 4
    <br/>

   ![Kotlin and Lean][language-shield]
   [![Apache 2.0 License][license-shield]][license-url]
   [![Contributors Welcome][contributors-shield]][contributors-url]

  </p>
</div>

## Overview

The UI is ordinary Jetpack Compose. Everything below the JNI boundary is Lean, using
its allocator, its object model and its arbitrary-precision `Nat`.

Tested on an Android 14 arm64-v8a device.

- [x] `Lean.factorial(n)` multiplies through Lean's `Nat`, so beyond 20! it exercises
      the bignum path rather than 64-bit arithmetic
- [x] `Lean.sumTo(n)` builds a Lean linked list with `lean_alloc_ctor` and walks it
- [x] `LeanView.kt` draws a view tree authored with
      [lean-compose](https://github.com/saviorand/lean-compose)
- [ ] That tree computed on-device rather than at build time

## Getting Started

The Lean runtime has to be cross-compiled for Android first, which is
[lean4-android](https://github.com/saviorand/lean4-android). Its
`scripts/build-stage1.sh` produces the `libleanshared.so` this application links
against.

You also need the Android NDK, the SDK with platform 34 and build-tools 34, and
JDK 17 or later.

The native libraries are build outputs and are not committed:

```bash
NDK=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64
LEAN=<lean4-android build>/stage/stage1

cp $LEAN/lib/lean/libleanshared.so            app/src/main/jniLibs/arm64-v8a/
cp $NDK/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
                                              app/src/main/jniLibs/arm64-v8a/
cp <lean4-android build>/libuv-build/libuv.so app/src/main/jniLibs/arm64-v8a/

$NDK/bin/aarch64-linux-android34-clang -shared -fPIC \
  -o app/src/main/jniLibs/arm64-v8a/libleanbridge.so \
  app/src/main/cpp/leanbridge.c \
  -I $LEAN/include -L $LEAN/lib/lean -lleanshared -Wl,--no-undefined

ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleDebug
```

The result is a 64 MB APK, compressed by Gradle from 162 MB of raw libraries.

## Pointer tagging

The application manifest sets:

```xml
<application android:allowNativeHeapPointerTagging="false">
```

Without it the process aborts with `Pointer tag ... was truncated`. Android 11 and
later store a tag in the top byte of every heap pointer, and Lean's `lean_box` is
`(n << 1) | 1`, which shifts values into that byte, so Bionic's `free()` sees a
stripped tag and aborts. The flag is the only supported way to disable this, and it
applies to APKs only, which is why the same libraries cannot be run from `adb shell`.

llama.cpp and Kotlin encounter the same abort under Termux, so this is a known class
of failure rather than something specific to Lean.

## Notes

`Lean.init()` must not run on the main thread. Mapping roughly 160 MB and running
Lean's module initialisers takes minutes on first launch, so the UI shows a progress
indicator and performs the work on `Dispatchers.Default`.

Two details worth recording: `@style/Theme.Material3.DayNight.NoActionBar` is not an
XML resource, since Compose themes in code; and `Nat` to `String` is
`l_Nat_reprFast`, not `lean_nat_to_string`, which does not exist.

> [!NOTE]
> The Lean-authored screen is currently rendered from JSON generated at build time.
> Computing it on-device crashed during Lean's initialisation once
> `libleancompose.so` was loaded, and diagnosing that needs a logcat. The export
> `lean_demo_screen_json` is in place; see the revert commit for what is known.

## Roadmap

- [ ] On-device view trees, so layouts can depend on runtime state
- [ ] Reduce the runtime size, which accounts for most of the APK
- [ ] More node kinds in `LeanView.kt`

## Contributing

Contributions are welcome. Getting the on-device path working would be the most
valuable addition.

## License

Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
[language-shield]: https://img.shields.io/badge/language-kotlin%20%2B%20lean4-blueviolet
[license-shield]: https://img.shields.io/github/license/saviorand/lean-android-compose?logo=github
[license-url]: https://github.com/saviorand/lean-android-compose/blob/main/LICENSE
[contributors-shield]: https://img.shields.io/badge/contributors-welcome!-blue
[contributors-url]: https://github.com/saviorand/lean-android-compose#contributing
