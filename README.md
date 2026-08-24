# lean-android-compose

A Jetpack Compose app whose computation, and optionally whose layout, runs in Lean 4.

Verified on a HiBreak (Android 14, arm64-v8a).

The UI is ordinary Compose. Everything below the JNI boundary is Lean, using its
allocator, its object model and its arbitrary-precision `Nat`.

## What it does

- `Lean.version()` reports the runtime's own version and platform target.
- `Lean.factorial(n)` multiplies through Lean's `Nat`, so past 20! it is running
  Lean's bignum path rather than 64-bit arithmetic. The result comes back as a
  string via `Nat.reprFast`.
- `Lean.sumTo(n)` builds a genuine Lean linked list with `lean_alloc_ctor` and walks
  it, exercising the allocator and object layout rather than a C loop.
- `LeanView.kt` interprets a view tree authored with
  [lean-compose](https://github.com/saviorand/lean-compose) into real Composables.

## Prerequisites

The Lean runtime has to be cross-compiled for Android first; that is
[lean4-android](https://github.com/saviorand/lean4-android). Its
`scripts/build-stage1.sh` produces the `libleanshared.so` this app links against.

Then:

- Android NDK r29 (or set `ANDROID_NDK_HOME`)
- Android SDK, platform 34 and build-tools 34
- JDK 17+

## Building

The native libraries are not in the repository; they are ~161 MB and are build
outputs of lean4-android.

```
NDK=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64
LEAN=<lean4-android build>/stage/stage1

cp $LEAN/lib/lean/libleanshared.so          app/src/main/jniLibs/arm64-v8a/
cp $NDK/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
                                            app/src/main/jniLibs/arm64-v8a/
cp <lean4-android build>/libuv-build/libuv.so app/src/main/jniLibs/arm64-v8a/

$NDK/bin/aarch64-linux-android34-clang -shared -fPIC \
  -o app/src/main/jniLibs/arm64-v8a/libleanbridge.so \
  app/src/main/cpp/leanbridge.c \
  -I $LEAN/include -L $LEAN/lib/lean -lleanshared -Wl,--no-undefined

ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleDebug
```

64 MB APK, from 162 MB of raw libraries; Gradle compresses them.

## The manifest flag that makes it work

```xml
<application android:allowNativeHeapPointerTagging="false">
```

Without it the process aborts:

```
Pointer tag for 0x773f2394b0 was truncated
Fatal signal 6 (SIGABRT)
```

Android 11+ stores a tag in the top byte of every heap pointer. Lean's `lean_box` is
`(n << 1) | 1`, which shifts values into that byte, so Bionic's `free()` sees a
stripped tag and aborts. This flag is the only supported way to turn it off, and it
is APK-only: the same libraries cannot be made to work from `adb shell`.

llama.cpp and Kotlin hit the same abort under Termux, so it is a known class of
failure rather than anything specific to Lean. Google documents the flag as a
temporary escape hatch; the durable fix is upstream, in Lean not using the top byte.

## Notes

**`Lean.init()` must not run on the main thread.** Mapping ~160 MB and running
Lean's module initialisers takes minutes on first launch, so the UI shows a progress
indicator and does the work on `Dispatchers.Default`.

**Two things that cost time:**

- `@style/Theme.Material3.DayNight.NoActionBar` is not an XML resource. Compose
  themes in code, so the platform theme only has to stay out of the way.
- `lean_nat_to_string` does not exist. `Nat` to `String` is `l_Nat_reprFast`,
  declared `extern`; it consumes its argument.

**Lean computes the view tree on the device.** `Lean.screenJson(count)` calls into
`lean-compose` with the current state and gets back a whole tree, so the layout is a
function of runtime state rather than something fixed at build time. That is what
makes this usable for server-driven UI: the Lean side decides what the screen is,
and `LeanView.kt` only knows how to draw nodes.

`lean-compose` does not need a full Lean rebuild to get onto the device. Lean emits
C for it under `.lake/build/ir/`, and that C cross-compiles against the existing
`libleanshared.so`:

```
for f in <lean-compose>/.lake/build/ir/Compose/*.c; do
  $NDK/bin/aarch64-linux-android34-clang -c -fPIC -O2 "$f" -I $LEAN/include -o "obj/$(basename $f .c).o"
done
$NDK/bin/aarch64-linux-android34-clang -shared -fPIC -o app/src/main/jniLibs/arm64-v8a/libleancompose.so \
  obj/*.o -L $LEAN/lib/lean -lleanshared -Wl,--no-undefined
```

The result is ~100 KB. Its module initialiser
(`initialize_lean_x2dcompose_Compose_Demo`) has to run after the core runtime is up
and **before** `lean_io_mark_end_initialization`, or the constants it allocates are
not registered as GC roots.
