# Native C code (JNI)

## Undefined Behavior
- Signed integer overflow (use unsigned arithmetic or check before operating)
- Null pointer dereference: check return values of `malloc`, `calloc`, `realloc`, JNI functions like `GetByteArrayElements`, `GetStringUTFChars`, `GetObjectClass`, `GetMethodID`, `GetFieldID`
- Out-of-bounds array access, including off-by-one in pointer arithmetic
- Use-after-free and double-free
- Strict aliasing violations: casting between incompatible pointer types (e.g., `char*` to `int*`) and dereferencing. Use `memcpy` to type-pun safely
- Uninitialized variable reads (especially in error paths where initialization is skipped)
- Modifying a `const`-qualified object through a cast
- Shifting by negative amount or by >= width of the type (e.g., `1 << 32` on a 32-bit int)
- Signed left shift into or past the sign bit (before C23)
- Division or modulo by zero
- Sequence point violations: multiple unsequenced modifications to the same variable (e.g., `a[i] = i++`)
- Accessing a union member other than the last one written (in C99+ this is implementation-defined, not UB, but avoid relying on it for portability)
- Pointer arithmetic that goes more than one past the end of an array
- `memcpy` / `memmove` with null source or destination, even if size is zero (UB per the standard)
- Calling `free()` on a pointer not obtained from `malloc`/`calloc`/`realloc`

## Thread Safety
- Functions that are NOT thread-safe and must not be called from concurrent JNI calls without external synchronization:
  - `strerror()` — the project wraps this with `strerror_r_xsi()` to handle both GNU and POSIX variants portably. New code should use `netty_unix_errors` helper functions rather than calling `strerror` or `strerror_r` directly
  - `getenv()` / `setenv()` / `putenv()` — `getenv` is not thread-safe if any thread calls `setenv`
  - `localtime()` / `gmtime()` / `asctime()` / `ctime()` — use `_r` variants
  - `inet_ntoa()` — use `inet_ntop()` instead
  - `gethostbyname()` / `gethostbyaddr()` — use `getaddrinfo()` / `getnameinfo()`
  - `setlocale()` — not safe if called concurrently or if other threads call locale-dependent functions
- Static/global mutable state: the project's convention is that static field IDs, method IDs, and class references are write-once during `JNI_OnLoad` and read-only thereafter — this is safe. Flag any new static mutable state that is written after initialization
- `errno` must be captured into a local variable immediately after a failing syscall, before any other function call (including JNI calls). The pattern is: `int err = errno;` right after checking the return value. Never pass `errno` directly to a function that may itself call other functions before using the value
- Signal safety: if installing signal handlers, ensure only async-signal-safe functions are called inside them

## JNI-Specific Concerns

### Class/Method/Field ID Caching
- Class references, method IDs, and field IDs must be cached as static globals, initialized during `JNI_OnLoad` (or the module's `_JNI_OnLoad` equivalent). They must not be looked up on every native call
- Use `NETTY_JNI_UTIL_LOAD_CLASS` / `NETTY_JNI_UTIL_LOAD_CLASS_WEAK` macros to load classes into global or weak global references. Never cache a raw local reference in a static variable
- Use `NewWeakGlobalRef` (via `NETTY_JNI_UTIL_LOAD_CLASS_WEAK`) for Netty's own classes (which can be unloaded), and `NewGlobalRef` (via `NETTY_JNI_UTIL_LOAD_CLASS`) for JDK classes that will never be unloaded
- When using a weak global reference later, always materialize it into a local reference first with `NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK` and check that the referent has not been collected (`IsSameObject(env, ref, NULL)`)
- All cached IDs must be set to NULL in the corresponding `_JNI_OnUnLoad` function, and global/weak references must be freed

### Null Checks and Exception Checking
- After `FindClass`, `GetMethodID`, `GetFieldID`, `NewGlobalRef`, `NewWeakGlobalRef`: check for NULL and `goto` the error label. Call `ExceptionClear` before the `goto` if a pending exception must not propagate (as done in `NETTY_JNI_UTIL_LOAD_CLASS`)
- After `NewObject`, `Call*Method`, `Get*ArrayElements`, `GetStringUTFChars`: check both NULL return and `(*env)->ExceptionCheck(env) == JNI_TRUE`, then `goto` cleanup
- Never continue calling JNI functions after a NULL return or pending exception — this leads to crashes or UB

### Local Reference Management
- Delete local references explicitly with `NETTY_JNI_UTIL_DELETE_LOCAL(env, ref)` once they are no longer needed, especially before returning from functions that create temporaries
- In loops that create local references, delete them each iteration or use `PushLocalFrame`/`PopLocalFrame` (the default local reference table is limited to 16 entries on some VMs)

### Array and String Access
- Prefer `GetByteArrayRegion`/`SetByteArrayRegion` with stack-allocated buffers over `GetPrimitiveArrayCritical`. The project deliberately avoids `GetPrimitiveArrayCritical` due to unresolved GCLocker bugs in the JVM (JDK-8048556, JDK-8057573, JDK-8057586)
- For `GetStringUTFChars`: always null-check the return, and always call `ReleaseStringUTFChars` on all code paths including error paths
- Be aware that JNI's "modified UTF-8" differs from standard UTF-8 (null encoded as 0xC0 0x80, supplementary characters differ)

### JNIEnv Thread Safety
- `JNIEnv*` is per-thread and must never be cached in a static variable or shared across threads
- Threads not created by the JVM must call `AttachCurrentThread` before using JNI and `DetachCurrentThread` when done

### JNI_OnLoad / OnUnLoad Structure
- Follow the existing pattern: track initialization progress with integer flags (e.g., `staticallyRegistered`, `nativeRegistered`, `linuxsocketOnLoadCalled`), so that the `done:` cleanup label can selectively undo only the steps that succeeded
- On error, unregister natives, call sub-module `_OnUnLoad`, free allocated strings, and null out all cached static IDs
- Return `JNI_ERR` on failure, `NETTY_JNI_UTIL_JNI_VERSION` on success

## Portability

### Integer and Pointer Sizes
- Don't assume `int` is 32 bits, `long` is 64 bits, or that pointers fit in `int`/`long`. Use `<stdint.h>` fixed-width types (`int32_t`, `uint64_t`) for wire formats and serialization. Use `intptr_t`/`uintptr_t` for pointer-to-integer conversions. Use `size_t` for sizes and array indices
- Never truncate pointers by storing in `int` or `jint`. Cast `jlong` addresses to pointers via `(intptr_t)`: e.g., `(struct epoll_event*)(intptr_t) address`
- `sizeof(long)`: 4 bytes on 32-bit systems and 64-bit Windows (LLP64), 8 bytes on 64-bit Unix (LP64). Use fixed-width types where size matters

### Endianness and Alignment
- Don't assume little-endian. Use `htonl`/`ntohl`/manual shifting for network protocols. ARM and RISC-V can be either endianness
- Don't cast arbitrary `char*` buffers to wider types and dereference. Some architectures (ARM, RISC-V) fault on unaligned access. Use `memcpy` for unaligned loads/stores
- `char` is signed on x86 Linux but unsigned on ARM Linux by default. Use `unsigned char` or `uint8_t` when doing arithmetic or comparison on byte values

### Feature Test Macros
- `_GNU_SOURCE` must be defined at the very top of the file, before any `#include` — this is the project's standard pattern. Verify it is not accidentally omitted from new files or placed after includes
- Be aware that `_GNU_SOURCE` affects `strerror_r` behavior (GNU variant returns `char*` vs XSI variant returns `int`). The project handles this with a `strerror_r_xsi` wrapper — new code should use `netty_unix_errors` functions rather than calling `strerror_r` directly

### Preprocessor Guards and Missing Symbols
- The project defines missing kernel/OS constants with `#ifndef` guards to allow compilation on older kernels. When adding new syscall wrappers or socket options, provide a `#ifndef` definition with the numeric value so older toolchains can still compile:
  ```c
  #ifndef UDP_GRO
  #define UDP_GRO 104
  #endif
  ```
- For architecture-specific syscall numbers (e.g., `SYS_recvmmsg`, `SYS_sendmmsg`), define per-architecture values guarded by `#if defined(__x86_64__)`, `#elif defined(__i386__)`, `#elif defined(__loongarch64)`, etc. Set the value to `-1` for unsupported architectures and check at runtime
- For platform-variant APIs (e.g., `sendfile` on macOS vs FreeBSD vs Linux), use `#ifdef __APPLE__` / `#else` blocks at the call site. Ensure the `#else` path works on all other supported BSDs

### Weak Symbols for Optional Functions
- Use `__attribute__((weak))` (and `__attribute__((weak_import))` for macOS) to declare functions that may not be available at link time (`accept4`, `pipe2`, `epoll_create1`, `epoll_pwait2`). Check the function pointer for NULL at runtime before calling:
  ```c
  extern int epoll_create1(int flags) __attribute__((weak));
  // ...
  if (epoll_create1) {
      efd = epoll_create1(EPOLL_CLOEXEC);
  } else {
      efd = epoll_create(126);
  }
  ```
- Pair weak symbols with runtime feature-detection functions (e.g., `isSupportingSendmmsg`) that probe with a dummy syscall and check for `ENOSYS`

### System Call Differences
- `epoll` (Linux only) vs `kqueue` (macOS, BSDs) vs `io_uring` (Linux 5.1+)
- `sendfile` has different signatures on Linux (`sendfile(out, in, offset, count)`) vs macOS (`sendfile(in, out, offset, &len, ...)`) vs FreeBSD (similar to macOS but with a different `sbytes` position)
- `splice`, `tee`, `vmsplice` are Linux-only
- Socket options may have different names or semantics (`SO_REUSEPORT` behavior varies)
- `MSG_NOSIGNAL` (Linux) vs `SO_NOSIGPIPE` (macOS/BSD) for suppressing SIGPIPE
- `accept4`, `pipe2` are Linux-only at link time; use weak symbols and fall back to `accept`/`pipe` + `fcntl`
- `clock_gettime` clock IDs vary: `CLOCK_MONOTONIC_COARSE` is Linux-only. macOS may not have `clock_gettime` at all on older versions (project uses `NETTY_USE_MACH_INSTEAD_OF_CLOCK` with Mach absolute time)
- `off_t` can be 32-bit on 32-bit systems without `_FILE_OFFSET_BITS=64`. Use explicit 64-bit types or ensure the correct define is set

## File Descriptors and Socket Events

- **`recvmsg` with `SCM_RIGHTS`:** Size the control buffer for what a peer can send, handle `MSG_CTRUNC`, validate `cmsg_len` and the number of descriptors, and close every received descriptor on every path, including when the message is rejected. A control buffer sized for a single `int` can still receive a message carrying several descriptors, all of which are installed and must be closed.
- **Half-closed connections and resets:** Error and hang-up events that arrive after a half-close (for example an RST) must lead to closing the channel or removing the interest; otherwise channels go stale or the event loop busy-loops.
- **Native SSL input:** Validate the length and structure of packets before handing them to the native `SSLEngine`; a crafted packet that reaches native code unchecked can crash the process.

## Memory Management
- Every `malloc`/`calloc`/`realloc` must have a corresponding `free` on all code paths including the `goto error`/`goto done` cleanup path
- Always null-check `malloc`/`calloc` return values. The project typically returns NULL or `goto`s the error label on allocation failure
- `realloc` pitfall: `p = realloc(p, new_size)` leaks the original buffer on failure. Use a temp pointer
- Prefer `calloc(count, size)` over `malloc(count * size)` — it zeros memory and avoids multiplication overflow
- For small, fixed-size buffers (e.g., IPv6 addresses at 16 bytes), use stack-allocated arrays rather than `malloc`. The project convention is `jbyte addressBytes[16]` on the stack
- For dynamically-sized allocations in hot paths (e.g., `struct mmsghdr msg[len]`), the project uses VLAs. Ensure the length comes from a bounded Java value (e.g., an array length parameter) and cannot be attacker-controlled to cause stack overflow
- `free(NULL)` is safe and the project relies on this — conditional null-checks before `free` are not required
- When JNI returns strings (`GetStringUTFChars`), always call `ReleaseStringUTFChars` on all paths. For byte arrays, prefer `GetByteArrayRegion` into a stack buffer (no release needed) over `GetByteArrayElements` (requires `ReleaseByteArrayElements`)

## Error Handling
- The project uses **goto-based cleanup** with labels like `done:` or `error:`. All resource acquisitions that need cleanup must have corresponding releases in the cleanup block. Track initialization progress with integer flags so cleanup can selectively undo only completed steps
- Check return values of all system calls. The project convention is to return negated errno (`return -err`) to the Java layer, which then maps it to the appropriate exception
- Handle `EINTR` with a retry loop: `do { res = syscall(...); } while (res == -1 && ((err = errno) == EINTR))`. Note how `err` is assigned inside the while condition so it's available after the loop
- `close()` must NOT be retried on `EINTR` — the file descriptor is already closed (or in an unspecified state) on Linux. The project explicitly ignores `EINTR` from close:
  ```c
  if (close(fd) < 0) {
      if (errno != EINTR) {
          return -errno;
      }
  }
  ```
- `mmap` returns `MAP_FAILED` (not NULL); `open`/`socket`/`accept` return -1 on failure (not NULL)
- For `read`/`write`/`send`/`recv`: be aware these can return partial results. When the call is in a JNI downcall that returns bytes-written, return the partial count to Java and let the Java layer retry. For internal loops (e.g., `epoll_wait` with timeout recalculation), handle partial progress within the C code
- Error message formatting: use `netty_unix_errors_throwChannelExceptionErrorNo` / `netty_unix_errors_throwRuntimeExceptionErrorNo` which handle the `strerror_r` portability mess internally. Don't call `strerror` or `strerror_r` directly in new code
