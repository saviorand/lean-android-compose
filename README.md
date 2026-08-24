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

the UI is ordinary Jetpack Compose. everything below the JNI boundary is Lean, using
its allocator, its object model and its arbitrary-precision `Nat`.

tested on an Android 14 arm64-v8a device.

- [x] `Lean.factorial(n)` multiplies through Lean's `Nat`, so past 20! it's Lean's
      bignum path and not 64-bit arithmetic
- [x] `Lean.sumTo(n)` builds a real Lean linked list with `lean_alloc_ctor` and walks it
- [x] `LeanView.kt` draws a tree authored with
      [lean-compose](https://github.com/saviorand/lean-compose)
- [ ] that tree computed on-device instead of at build time

## Getting Started

the Lean runtime has to be cross-compiled for Android first, which is
[lean4-android](https://github.com/saviorand/lean4-android). its
`scripts/build-stage1.sh` produces the `libleanshared.so` this links against.

you also need the Android NDK, the SDK (platform 34, build-tools 34) and JDK 17+.

the native libraries are build outputs, not in the repo:

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

64 MB APK out of 162 MB of raw libraries, Gradle compresses them.

## The flag that makes it work

```xml
<application android:allowNativeHeapPointerTagging="false">
```

without it the process aborts with `Pointer tag ... was truncated`. Android 11+ puts
a tag in the top byte of every heap pointer, and Lean's `lean_box` is `(n << 1) | 1`,
which shifts values into that byte, so Bionic's `free()` sees a stripped tag and
aborts. this flag is the only supported way to turn it off and it's APK-only, so the
same libraries can't be made to work from `adb shell`.

llama.cpp and Kotlin hit the same abort under Termux, so it's a known class of
failure and not something specific to Lean.

## Notes

**`Lean.init()` must not run on the main thread.** mapping ~160 MB and running Lean's
module initialisers takes minutes on first launch, so the UI shows a progress
indicator and does the work on `Dispatchers.Default`.

two things that cost me time: `@style/Theme.Material3.DayNight.NoActionBar` isn't an
XML resource, since Compose themes in code. and `Nat` to `String` is `l_Nat_reprFast`,
not `lean_nat_to_string`, which doesn't exist.

> [!NOTE]
> the Lean-authored screen is rendered from JSON generated at build time. computing
> it on-device crashed during Lean's initialisation once `libleancompose.so` was
> loaded, and working that out needs a logcat i couldn't get. the export
> (`lean_demo_screen_json`) is in place; see the revert commit for what's known.

## Roadmap

- [ ] on-device view trees so layouts can depend on runtime state
- [ ] trim the runtime, 161 MB is most of the APK
- [ ] more node kinds in `LeanView.kt`

## Contributing

contributions welcome. if you can get the on-device path working that's the most
useful thing here.

## License

Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
[language-shield]: https://img.shields.io/badge/language-kotlin%20%2B%20lean4-blueviolet
[license-shield]: https://img.shields.io/github/license/saviorand/lean-android-compose?logo=github
[license-url]: https://github.com/saviorand/lean-android-compose/blob/main/LICENSE
[contributors-shield]: https://img.shields.io/badge/contributors-welcome!-blue
[contributors-url]: https://github.com/saviorand/lean-android-compose#contributing
