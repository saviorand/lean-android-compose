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
- [x] That tree computed on-device: `Lean.screenJson(count)` returns a whole layout
      for the current state, so the UI is a function of runtime state

## Getting Started

The Lean runtime has to be cross-compiled for Android first, which is
[lean4-android](https://github.com/saviorand/lean4-android). Its
`scripts/build-stage1.sh` produces the `libleanshared.so` this application links
against.

You also need the Android NDK, the SDK with platform 34 and build-tools 34, and
JDK 17 or later.

The application ships a single native library, built by linking the Lean runtime, the
parts of the standard library it reaches, `lean-compose` and the JNI bridge together
statically:

```bash
NDK=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64
LEAN=<lean4-android build>/stage/stage1

# lean-compose, from the C Lean already emitted under .lake/build/ir
for f in <lean-compose>/.lake/build/ir/Compose/*.c; do
  $NDK/bin/aarch64-linux-android34-clang -c -fPIC -Oz \
    -ffunction-sections -fdata-sections -fvisibility=hidden \
    "$f" -I $LEAN/include -o "obj/$(basename $f .c).o"
done

$NDK/bin/aarch64-linux-android34-clang -shared -fPIC -Oz \
  -ffunction-sections -fdata-sections \
  -o app/src/main/jniLibs/arm64-v8a/libleanbridge.so \
  app/src/main/cpp/leanbridge.c obj/*.o -I $LEAN/include \
  -Wl,--gc-sections \
  $LEAN/lib/lean/libInit.a $LEAN/lib/lean/libStd.a \
  $LEAN/runtime/libleanrt_initial-exec.a $LEAN/lib/lean/libleancpp.a \
  <lean4-android build>/libuv-build/libuv.a \
  <lean4-android build>/openssl-install/lib/libcrypto.a \
  <lean4-android build>/openssl-install/lib/libssl.a \
  -lm -ldl

$NDK/bin/llvm-strip app/src/main/jniLibs/arm64-v8a/libleanbridge.so
cp $NDK/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   app/src/main/jniLibs/arm64-v8a/

ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleDebug
```

The result is a 12 MB APK. Linking against the archives with `--gc-sections` instead
of shipping `libleanshared.so` takes the runtime from 161 MB to 10 MB: the
application reaches 42 of the runtime's 223,267 exported symbols and none of them
belong to the elaborator or compiler, so `libLean.a` is left out entirely.

`libleanrt_initial-exec.a` rather than `libleanrt.a`: the latter uses local-exec TLS
and fails to link into a shared library with `R_AARCH64_TLSLE_ADD_TPREL_HI12 cannot
be used with -shared`.

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

`lean-compose` does not need a full Lean rebuild to reach the device. Lean emits C
for it under `.lake/build/ir/`, and that C cross-compiles against the existing
`libleanshared.so` into a library of about 100 KB:

```bash
for f in <lean-compose>/.lake/build/ir/Compose/*.c; do
  $NDK/bin/aarch64-linux-android34-clang -c -fPIC -O2 "$f" -I $LEAN/include \
    -o "obj/$(basename $f .c).o"
done
$NDK/bin/aarch64-linux-android34-clang -shared -fPIC \
  -o app/src/main/jniLibs/arm64-v8a/libleancompose.so \
  obj/*.o -L $LEAN/lib/lean -lleanshared -Wl,--no-undefined
```

Two details matter. Module initialisers take only `builtin`, not an IO world token,
and `lean-compose`'s initialiser must run after the core runtime is up but before
`lean_io_mark_end_initialization`. And `LeanView.kt` draws a `scaffold` node as a bar
above its body rather than as a Material `Scaffold`, since `Scaffold` fills its
constraints and throws when composed inside a vertical scroller.

## Roadmap

- [ ] More node kinds in `LeanView.kt`

## Contributing

Contributions are welcome, particularly more node kinds and anything that reduces
the size of the shipped runtime.

## License

Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
[language-shield]: https://img.shields.io/badge/language-kotlin%20%2B%20lean4-blueviolet
[license-shield]: https://img.shields.io/github/license/saviorand/lean-android-compose?logo=github
[license-url]: https://github.com/saviorand/lean-android-compose/blob/main/LICENSE
[contributors-shield]: https://img.shields.io/badge/contributors-welcome!-blue
[contributors-url]: https://github.com/saviorand/lean-android-compose#contributing
