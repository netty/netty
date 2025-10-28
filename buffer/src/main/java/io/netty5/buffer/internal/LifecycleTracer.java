/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.buffer.internal;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.LeakInfo;
import io.netty5.buffer.LeakInfo.TracePoint;
import io.netty5.buffer.Owned;
import io.netty5.util.Resource;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.Serial;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Instances of this class record life cycle events of resources, to help debug life-cycle errors.
 */
@UnstableApi
public abstract class LifecycleTracer {
    static final boolean lifecycleTracingEnabled =
            SystemPropertyUtil.getBoolean("io.netty5.buffer.lifecycleTracingEnabled", false);

    /**
     * Get a tracer for a newly allocated resource.
     * <p>
     * <strong>Note:</strong> this call is itself not traced.
     * Instead, it is customary to immediately call {@link #allocate()} on the returned tracer.
     *
     * @return A new tracer for a resource.
     */
    public static LifecycleTracer get() {
        if (!lifecycleTracingEnabled && LeakDetection.leakDetectionEnabled == 0) {
            return NoOpTracer.INSTANCE;
        }
        return new StackTracer();
    }

    /**
     * Add to the trace log that the object has been allocated.
     */
    public abstract void allocate();

    /**
     * Add to the trace log that the object has been acquired, in other words the reference count has been incremented.
     *
     * @param acquires The new current number of acquires on the traced object.
     */
    public abstract void acquire(int acquires);

    /**
     * Add to the trace log that the object has been dropped.
     *
     * @param acquires The new current number of acquires on the traced object.
     */
    public abstract void drop(int acquires);

    /**
     * Add to the trace log that the object has been closed, in other words, the reference count has been decremented.
     *
     * @param acquires The new current number of acquires on the traced object.
     */
    public abstract void close(int acquires);

    /**
     * Add the hint object to the trace log.
     * The hint objects can be inspected later if a lifecycle related exception is thrown, or if the object leaks.
     *
     * @param hint The hint object to attach to the trace log.
     */
    public abstract void touch(Object hint);

    /**
     * Add to the trace log that the object is being sent.
     *
     * @param <I> The resource interface for the object.
     * @param <T> The concrete type of the object.
     * @param instance The owned instance being sent.
     * @return An {@link Owned} instance that may trace the reception of the object.
     */
    public abstract <I extends Resource<I>, T extends ResourceSupport<I, T>> Owned<T> send(Owned<T> instance);

    /**
     * Attach a trace to both life-cycles, that a single life-cycle has been split into two.
     * <p>
     * Such branches happen when two views are created to share a single underlying resource.
     * The most prominent example of this is the {@link Buffer#split()} method, where a buffer is broken into two that
     * each covers a non-overlapping region of the original memory.
     * <p>
     * This method is called on the originating, or "parent" tracer, while the newly allocated "child" is given as an
     * argument.
     *
     * @param tracer The tracer for the life-cycle that was branched from the life-cycle represented by this tracer.
     */
    public abstract void splitTo(LifecycleTracer tracer);

    /**
     * Attach a trace to both life-cycles, that the life-cycle of an underlying resource has moved from one trace to
     * another.
     * <p>
     * Such events happen when {@link Buffer#moveAndClose()} is called on a buffer, and the life-cycle of its memory
     * moves to the returned instance, while the given instance is closed.
     * @param tracer The tracer for the life-cycle that the traced resources moved to.
     */
    public abstract void moveTo(LifecycleTracer tracer);

    /**
     * Attach a life cycle trace log to the given exception.
     *
     * @param throwable The exception that will receive the trace log in the form of
     * {@linkplain Throwable#addSuppressed(Throwable) suppressed exceptions}.
     * @param <E> The concrete exception type.
     * @return The same exception instance, that can then be thrown.
     */
    public abstract <E extends Throwable> E attachTrace(E throwable);

    /**
     * Return the life-cycle trace as an ordered {@link Collection} of {@link TracePoint}s.
     * The trace points are ordered chronologically in the collection, with earlier events before later ones.
     * The returned collection is not modifiable.
     *
     * @return A collection of trace points.
     */
    public abstract Collection<TracePoint> collectTraces();

    private static final class NoOpTracer extends LifecycleTracer {
        private static final NoOpTracer INSTANCE = new NoOpTracer();

        @Override
        public void allocate() {
        }

        @Override
        public void acquire(int acquires) {
        }

        @Override
        public void drop(int acquires) {
        }

        @Override
        public void close(int acquires) {
        }

        @Override
        public void touch(Object hint) {
        }

        @Override
        public <I extends Resource<I>, T extends ResourceSupport<I, T>> Owned<T> send(Owned<T> instance) {
            return instance;
        }

        @Override
        public void splitTo(LifecycleTracer tracer) {
        }

        @Override
        public void moveTo(LifecycleTracer tracer) {
        }

        @Override
        public <E extends Throwable> E attachTrace(E throwable) {
            return throwable;
        }

        @Override
        public Collection<TracePoint> collectTraces() {
            return Collections.emptyList();
        }
    }

    private static final class StackTracer extends LifecycleTracer {
        private static final int MAX_TRACE_POINTS = Math.min(SystemPropertyUtil.getInt(
                "io.netty5.buffer.internal.LifecycleTracer.MAX_TRACE_POINTS", 50), 1000);
        private static final StackWalker WALKER;
        static {
            int depth = Trace.TRACE_LIFECYCLE_DEPTH;
            WALKER = depth > 0 && lifecycleTracingEnabled ? StackWalker.getInstance(Set.of(), depth + 2) : null;
        }

        private final ArrayDeque<Trace> traces = new ArrayDeque<>();
        private boolean dropped;

        @Override
        public void allocate() {
            addTrace(walk(new Trace(TraceType.ALLOCATE, 0)));
        }

        @Override
        public void acquire(int acquires) {
            addTrace(walk(new Trace(TraceType.ACQUIRE, acquires)));
        }

        @Override
        public void drop(int acquires) {
            dropped = true;
            addTrace(walk(new Trace(TraceType.DROP, acquires)));
        }

        @Override
        public void close(int acquires) {
            if (!dropped) {
                addTrace(walk(new Trace(TraceType.CLOSE, acquires)));
            }
        }

        @Override
        public void touch(Object hint) {
            Trace trace = new Trace(TraceType.TOUCH);
            trace.attachmentType = AttachmentType.HINT;
            trace.attachment = hint;
            addTrace(walk(trace));
        }

        @Override
        public <I extends Resource<I>, T extends ResourceSupport<I, T>> Owned<T> send(Owned<T> instance) {
            Trace sendTrace = new Trace(TraceType.SEND);
            sendTrace.attachmentType = AttachmentType.RECEIVED_AT;
            addTrace(walk(sendTrace));
            return drop -> {
                sendTrace.attachment = walk(new Trace(TraceType.RECEIVE));
                return instance.transferOwnership(drop);
            };
        }

        @Override
        public void splitTo(LifecycleTracer splitTracer) {
            Trace splitParent = new Trace(TraceType.SPLIT);
            Trace splitChild = new Trace(TraceType.SPLIT);
            splitParent.attachmentType = AttachmentType.SPLIT_TO;
            splitParent.attachment = splitChild;
            splitChild.attachmentType = AttachmentType.SPLIT_FROM;
            splitChild.attachment = splitParent;
            addTrace(walk(splitParent));
            if (splitTracer instanceof StackTracer tracer) {
                tracer.addTrace(walk(splitChild));
            }
        }

        @Override
        public void moveTo(LifecycleTracer moveTracer) {
            Trace moveParent = new Trace(TraceType.MOVE);
            Trace moveChild = new Trace(TraceType.MOVE);
            moveParent.attachmentType = AttachmentType.MOVE_TO;
            moveParent.attachment = moveChild;
            moveChild.attachmentType = AttachmentType.MOVE_FROM;
            moveChild.attachment = moveParent;
            addTrace(walk(moveParent));
            if (moveTracer instanceof StackTracer tracer) {
                tracer.addTrace(walk(moveChild));
            }
        }

        @Override
        public <E extends Throwable> E attachTrace(E throwable) {
            synchronized (traces) {
                long timestamp = System.nanoTime();
                for (Trace trace : traces) {
                    trace.attach(throwable, timestamp);
                }
            }
            return throwable;
        }

        @Override
        public Collection<TracePoint> collectTraces() {
            return Collections.unmodifiableCollection(Collections.synchronizedCollection(traces));
        }

        Trace walk(Trace trace) {
            if (WALKER != null) {
                WALKER.walk(trace);
            }
            return trace;
        }

        void addTrace(Trace trace) {
            synchronized (traces) {
                if (traces.size() == MAX_TRACE_POINTS) {
                    traces.pollFirst();
                }
                traces.addLast(trace);
            }
        }
    }

    static final class Trace implements Function<Stream<StackWalker.StackFrame>, Trace>, LeakInfo.TracePoint {
        private static final int TRACE_LIFECYCLE_DEPTH;
        public static final StackTraceElement[] EMPTY_TRACE = new StackTraceElement[0];

        static {
            int traceDefault = 50;
            TRACE_LIFECYCLE_DEPTH = Math.max(Integer.getInteger(
                    "io.netty5.buffer.internal.LifecycleTracer.TRACE_LIFECYCLE_DEPTH", traceDefault), 0);
        }

        final TraceType type;
        final int acquires;
        final long timestamp;
        volatile AttachmentType attachmentType;
        volatile Object attachment;
        StackWalker.StackFrame[] frames;

        Trace(TraceType type) {
            this(type, Integer.MIN_VALUE);
        }

        Trace(TraceType type, int acquires) {
            this.type = type;
            this.acquires = acquires;
            timestamp = System.nanoTime();
        }

        @Override
        public Trace apply(Stream<StackWalker.StackFrame> frames) {
            this.frames = frames.skip(2).limit(TRACE_LIFECYCLE_DEPTH + 1).toArray(StackWalker.StackFrame[]::new);
            return this;
        }

        public <E extends Throwable> void attach(E throwable, long timestamp) {
            Traceback exception = getTraceback(timestamp, true);
            throwable.addSuppressed(exception);
        }

        @Override
        public Throwable traceback() {
            return getTraceback(System.nanoTime(), true);
        }

        @Override
        public String eventName() {
            return type.name();
        }

        @Override
        public String toString() {
            Object hint = hint();
            if (hint != null) {
                return "Trace[" + type.name() + ", " + hint + ']';
            }
            return "Trace[" + type.name() + ']';
        }

        private Traceback getTraceback(long timestamp, boolean recurse) {
            String message = type.name();
            Trace associatedTrace = getAssociatedTrace();
            message = explainAttachment(message, associatedTrace);
            if (acquires != Integer.MIN_VALUE) {
                message += " (current acquires = " + acquires + ')';
            }
            message += " T" + (this.timestamp - timestamp) / 1000 + "us.";
            Traceback exception = new Traceback(message);
            StackTraceElement[] stackTrace = framesToStackTrace();
            exception.setStackTrace(stackTrace);
            if (associatedTrace != null && recurse) {
                exception.addSuppressed(associatedTrace.getTraceback(timestamp, false));
            }
            return exception;
        }

        private Trace getAssociatedTrace() {
            Object associated = attachment;
            if (associated instanceof Trace) {
                return (Trace) associated;
            }
            return null;
        }

        private String explainAttachment(String message, Trace associatedTrace) {
            AttachmentType type = attachmentType;
            if (type == null) {
                return message;
            }
            switch (type) {
            case RECEIVED_AT:
                if (associatedTrace == null) {
                    message += " (sent but not received)";
                } else {
                    message += " (sent and received)";
                }
                break;
            case SEND_FROM:
                message += " (from a send)";
                break;
            case SPLIT_TO:
                message += " (split into two)";
                break;
            case SPLIT_FROM:
                message += " (split from other object)";
                break;
            case MOVE_TO:
                message += " (moved to)";
                break;
            case MOVE_FROM:
                message += " (moved from)";
                break;
            case HINT:
                message += " (" + attachment + ')';
                break;
            }
            return message;
        }

        private StackTraceElement[] framesToStackTrace() {
            if (frames == null) {
                return EMPTY_TRACE;
            }
            StackTraceElement[] stackTrace = new StackTraceElement[frames.length];
            for (int i = 0; i < frames.length; i++) {
                stackTrace[i] = frames[i].toStackTraceElement();
            }
            return stackTrace;
        }

        @Override
        public Object hint() {
            return attachmentType == AttachmentType.HINT? attachment : null;
        }
    }

    private static final class Traceback extends Throwable {
        @Serial
        private static final long serialVersionUID = 941453986194634605L;

        Traceback(String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private enum TraceType {
        ALLOCATE,
        ACQUIRE,
        CLOSE,
        DROP,
        SEND,
        RECEIVE,
        TOUCH,
        SPLIT,
        MOVE,
    }

    private enum AttachmentType {
        /**
         * Tracer of sending object.
         */
        SEND_FROM,
        /**
         * Tracer of object that was received, after being sent.
         */
        RECEIVED_AT,
        /**
         * Tracer of origin object of a split.
         */
        SPLIT_FROM,
        /**
         * Tracer of object split from this traced object.
         */
        SPLIT_TO,
        /**
         * Tracer of origin object of a move.
         */
        MOVE_FROM,
        /**
         * Tracer of object moved from this traced object.
         */
        MOVE_TO,
        /**
         * Object is a hint from a {@link Resource#touch(Object)} call.
         */
        HINT,
    }
}
