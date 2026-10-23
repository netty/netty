# Concurrency

## Data Races & Atomicity
- Shared mutable state accessed without synchronization
- Check-then-act sequences (TOCTOU) that aren't atomic
- Read-modify-write on non-atomic fields (e.g., `count++` on a plain int shared between threads)
- Compound operations on concurrent collections that assume atomicity (e.g., `if (!map.containsKey(k)) map.put(k, v)` instead of `putIfAbsent`)
- Non-atomic lazy initialization of shared fields

## Visibility & Ordering
- Fields written by one thread and read by another without `volatile`, synchronization, or a happens-before edge
- Unsafe publication: constructing an object and making it visible to other threads without a memory barrier (e.g., assigning to a plain field that other threads read)
- Reliance on instruction ordering that the JMM does not guarantee
- Missing `volatile` on flags or state fields used for cross-thread signaling

## Locking
- Inconsistent lock protection: a field guarded by a lock in one place but accessed without it elsewhere
- Lock ordering inversions that could deadlock (acquiring locks in different orders across code paths)
- Holding locks while performing blocking I/O, calling into unknown callbacks, or doing expensive computation
- Synchronizing on inappropriate objects (mutable fields, boxed types, `this` when subclassing is possible, string literals)
- Missing `synchronized` on methods that override a synchronized superclass method

## Thread Confinement Violations
- Accessing a non-thread-safe object from multiple threads (e.g., `SimpleDateFormat`, `HashMap` without external synchronization)
- Passing a thread-confined object to another thread without safe handoff

## Netty-Specific Concerns
- Accessing a `Channel`, `ChannelHandlerContext`, or `ChannelPipeline` from outside its `EventLoop` thread without checking `inEventLoop()` / scheduling via `execute()`
- Calling `ByteBuf` retain/release or read/write from a thread that does not own the buffer
- Sharing a `ChannelHandler` instance across pipelines without `@Sharable`, or marking a handler `@Sharable` when it holds mutable state
- Completing a `Promise`/`Future` from the wrong `EventLoop`, creating ordering issues
- Modifying pipeline from a thread that isn't the channel's `EventLoop`
- Using blocking calls inside an `EventLoop` thread (blocking the I/O thread)

## java.util.concurrent Misuse
- Using a `ConcurrentHashMap` or `CopyOnWriteArrayList` but wrapping it in external synchronization (defeating scalability) or assuming compound operations are atomic
- Executor/thread-pool misconfiguration: unbounded queues masking backpressure, rejected execution not handled
- `CompletableFuture` composition that silently swallows exceptions or runs on the wrong executor
- `CountDownLatch` / `CyclicBarrier` with incorrect counts
- `ReentrantLock` not released in a `finally` block

## Safe Construction
- Leaking `this` from a constructor (registering listeners, starting threads, passing `this` to other objects before construction completes)
- Non-final fields in objects intended to be safely published via `final` field semantics
