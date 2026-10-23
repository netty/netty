# Concurrency and reentrancy

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

## Handler Reentrancy

This is a single-threaded problem and no synchronization fixes it. When a `ChannelHandlerContext` or `Channel` method is called from the channel's event loop thread, the message or event is processed immediately, in the caller's stack frame, by the next handler in the pipeline. That handler may in turn call back toward the handler that is still executing further up the stack. The outer call has not returned yet, so the handler can be re-entered while it is in the middle of an operation, with its state only partly updated.

**Call-outs that can run other handlers synchronously:**
- `ctx.fire*` and `ctx.pipeline().fire*` (`fireChannelRead`, `fireChannelReadComplete`, `fireChannelActive`, `fireChannelInactive`, `fireExceptionCaught`, `fireUserEventTriggered`, `fireChannelWritabilityChanged`)
- `ctx.write`, `ctx.writeAndFlush`, `ctx.flush`, `ctx.read`, `ctx.close`, `ctx.disconnect`, `ctx.connect`, `ctx.bind`, `ctx.deregister`, and the equivalent `Channel` methods
- Completing a `Promise` (`setSuccess`, `trySuccess`, `setFailure`, `tryFailure`, `cancel`), which runs its listeners inline
- Pipeline changes (`addLast`, `remove`, `replace`, ...), which invoke `handlerAdded` / `handlerRemoved` inline
- Any user-supplied callback, listener or handler method

**How control comes back:**
- A write issued from `channelRead` is processed by outbound handlers, which may fire inbound events or fail a promise that completes a listener, which writes again
- A downstream handler closes the channel, so `channelInactive` and `handlerRemoved` reach the upstream handler before the `fireChannelRead` call that triggered them has returned
- A downstream handler removes or replaces an upstream handler while it is still on the stack
- A listener on a write promise, or an exception handler, calls `ctx.close()` or `ctx.flush()` from inside a flush

**What to check for each call-out in changed code:**
- Can control come back into this same handler instance (any of its methods, not just the one currently executing) before the call returns?
- Is the handler's state consistent at the point of the call? State should be fully updated before calling out, or re-read afterwards.
- Does code use values read before the call after it returns? Cached fields, indices, sizes, local copies of collections, `cumulation` buffers and state flags may all have changed.
- Is a collection iterated while a call-out in the loop body can modify it?
- After the call returns, does the code assume that the handler is still in the pipeline, the channel is still open or active, or a buffer it owns has not been released or reset by a re-entrant `channelInactive` / `handlerRemoved`?
- Does a re-entrant call observe the intermediate state of a state machine, e.g. a "decoding" or "flushing" flag that is set before a call-out and only cleared after it, or not cleared on the exception path (use `try`/`finally`)?
- If re-entry cannot be tolerated, is it handled explicitly (a re-entrancy guard, deferring the work to the outermost frame, or a state check after the call-out)? Look at how existing code does this, e.g. how `ByteToMessageDecoder` copes with its handler being removed while it is decoding, and how `AbstractChannel.AbstractUnsafe` guards `flush0` against re-entry.
- Does a re-entrant write or event reorder output or events relative to the outer call that is still in progress?
- Can the recursion depth be influenced by the peer, e.g. each received frame causes a response whose processing receives the next frame? Unbounded recursion on attacker-controlled input is a stack-overflow denial of service.

**Tests:** new or changed stateful handlers that call out should be tested with a neighbouring handler that re-enters during the call (closes the channel, removes the handler, writes, or fires an event from inside the callback), for example using `EmbeddedChannel`. The absence of such a test is a MEDIUM or LOW finding, not a defect in itself.

**Before reporting:** trace a concrete re-entry chain (`Handler A.method` -> `ctx.call` -> `Handler B.method` -> `ctx.call` -> back into `Handler A.method`) and show what state is inconsistent when it arrives. Don't report reentrancy as a purely theoretical possibility; if nothing in any realistic pipeline can call back, or the call-out is the last action and nothing touches state after it, it is not a finding.

## java.util.concurrent Misuse
- Using a `ConcurrentHashMap` or `CopyOnWriteArrayList` but wrapping it in external synchronization (defeating scalability) or assuming compound operations are atomic
- Executor/thread-pool misconfiguration: unbounded queues masking backpressure, rejected execution not handled
- `CompletableFuture` composition that silently swallows exceptions or runs on the wrong executor
- `CountDownLatch` / `CyclicBarrier` with incorrect counts
- `ReentrantLock` not released in a `finally` block

## Safe Construction
- Leaking `this` from a constructor (registering listeners, starting threads, passing `this` to other objects before construction completes)
- Non-final fields in objects intended to be safely published via `final` field semantics
