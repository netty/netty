/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer.stream;

import io.netty.buffer.Buf;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Random;

public abstract class AbstractStream<T extends Buf> implements Stream<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractStream.class);

    private static final Random random = new Random();

    private static final ThreadLocal<Boolean> IN_CONSUMER = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    /**
     * Generate a random string that can be used for correlating a group of log messages.
     */
    private static String nextLogKey() {
        return Long.toHexString(random.nextInt() & 0xFFFFFFFFL);
    }

    private final StreamProducerContextImpl producerCtx;
    private StreamConsumerContextImpl consumerCtx;
    private Runnable invokeStreamConsumedTask;

    /**  0 - init, 1 - accepted, 2 - discarded, 3 - rejected, 4 - closed */
    int state;
    T buffer;

    @SuppressWarnings("unchecked")
    protected AbstractStream(EventExecutor executor, StreamProducer<? super T> producer) {
        producerCtx = new StreamProducerContextImpl(executor, producer);
    }

    T buffer() {
        T buffer = this.buffer;
        if (buffer == null) {
            fail();
        }
        return buffer;
    }

    @Override
    public void accept(EventExecutor executor, StreamConsumer<? super T> consumer) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        if (consumer == null) {
            throw new NullPointerException("handler");
        }

        if (state != 0) {
            fail();
        }

        StreamConsumerContextImpl consumerCtx = new StreamConsumerContextImpl(executor, consumer);

        @SuppressWarnings("unchecked")
        StreamConsumer<T> h = (StreamConsumer<T>) consumer;
        try {
            buffer = h.newStreamBuffer(consumerCtx);
        } catch (Throwable t) {
            PlatformDependent.throwException(t);
        }

        this.consumerCtx = consumerCtx;
        state = 1;

        fireStreamAccepted();
    }

    private void fireStreamAccepted() {
        EventExecutor e = producerCtx.executor;
        if (e.inEventLoop()) {
            invokeStreamAccepted();
        } else {
            e.execute(new Runnable() {
                @Override
                public void run() {
                    invokeStreamAccepted();
                }
            });
        }
    }

    private void invokeStreamAccepted() {
        StreamProducerContextImpl producerCtx = this.producerCtx;
        try {
            producerCtx.producer.streamAccepted(producerCtx);
        } catch (Throwable t) {
            safeAbort(producerCtx, t);
            return;
        }

        if (consumerCtx.nextCalled) {
            consumerCtx.invokeStreamConsumed();
        }
    }

    void safeAbort(StreamProducerContextImpl producerCtx, Throwable cause) {
        try {
            producerCtx.abort(cause);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                String key = nextLogKey();
                logger.warn("[{}] Failed to auto-abort a stream.", key, t);
                logger.warn("[{}] .. when invoked with the following cause:", key, cause);
            }
        }
    }

    @Override
    public void discard() {
        StreamConsumerContextImpl consumerCtx = this.consumerCtx;
        if (consumerCtx == null) {
            fail();
        }
        consumerCtx.discard();
    }

    @Override
    public void reject(Throwable cause) {
        StreamConsumerContextImpl consumerCtx = this.consumerCtx;
        if (consumerCtx == null) {
            fail();
        }
        consumerCtx.reject(cause);
    }

    private void fail() {
        switch (state) {
            case 0:
                throw new IllegalStateException("stream not accepted yet");
            case 1:
                throw new IllegalStateException("stream accepted already");
            case 2:
                throw new IllegalStateException("stream discarded already");
            case 3:
                throw new IllegalStateException("stream rejected already");
            case 4:
                throw new IllegalStateException("stream closed already");
            default:
                throw new Error();
        }
    }

    private final class StreamProducerContextImpl implements StreamProducerContext<T> {

        final EventExecutor executor;
        final StreamProducer<T> producer;
        boolean invokedStreamOpen;
        Runnable invokeStreamUpdatedTask;

        @SuppressWarnings("unchecked")
        StreamProducerContextImpl(EventExecutor executor, StreamProducer<? super T> producer) {
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            if (producer == null) {
                throw new NullPointerException("producer");
            }

            this.executor = executor;
            this.producer = (StreamProducer<T>) producer;
        }

        @Override
        public EventExecutor executor() {
            return executor;
        }

        @Override
        public T buffer() {
            return AbstractStream.this.buffer();
        }

        @Override
        public StreamProducerContext<T> update() {
            if (state != 1) {
                fail();
            }

            fireStreamUpdated();
            return this;
        }

        private void fireStreamUpdated() {
            EventExecutor e = consumerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamUpdated();
            } else {
                Runnable task = invokeStreamUpdatedTask;
                if (task == null) {
                    invokeStreamUpdatedTask = task = new Runnable() {
                        @Override
                        public void run() {
                            invokeStreamUpdated();
                        }
                    };
                }
                e.execute(task);
            }
        }

        private void invokeStreamUpdated() {
            StreamConsumerContextImpl consumerCtx = AbstractStream.this.consumerCtx;
            StreamConsumer<T> consumer = consumerCtx.consumer;
            if (consumerCtx.singleThreaded) {
                IN_CONSUMER.set(Boolean.TRUE);
            }
            try {
                if (!invokedStreamOpen) {
                    invokedStreamOpen = true;
                    try {
                        consumer.streamOpen(consumerCtx);
                    } catch (Throwable t) {
                        safeAbort(producerCtx, new StreamConsumerException(t));
                        return;
                    }
                }

                try {
                    consumer.streamUpdated(consumerCtx);
                } catch (Throwable t) {
                    safeAbort(producerCtx, new StreamConsumerException(t));
                }
            } finally {
                if (consumerCtx.singleThreaded) {
                    IN_CONSUMER.set(Boolean.FALSE);
                }
            }
        }

        @Override
        public void close() {
            if (state == 1) {
                state = 4;
                fireStreamClosed();
            }
        }

        private void fireStreamClosed() {
            EventExecutor e = consumerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamClosed();
            } else {
                e.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeStreamClosed();
                    }
                });
            }
        }

        private void invokeStreamClosed() {
            StreamConsumerContextImpl consumerCtx = AbstractStream.this.consumerCtx;
            try {
                consumerCtx.consumer.streamClosed(consumerCtx);
            } catch (Throwable t) {
                logger.warn("StreamConsumer.streamClosed() raised an exception.", t);
            }
        }

        @Override
        public void abort(Throwable cause) {
            if (cause == null) {
                throw new NullPointerException("cause");
            }

            if (state != 1) {
                fail();
            }

            fireStreamAborted(cause);
        }

        private void fireStreamAborted(final Throwable cause) {
            StreamConsumerContextImpl consumerCtx = AbstractStream.this.consumerCtx;
            EventExecutor e = consumerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamAborted(cause);
            } else {
                e.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeStreamAborted(cause);
                    }
                });
            }
        }

        private void invokeStreamAborted(Throwable cause) {
            StreamConsumerContextImpl consumerCtx = AbstractStream.this.consumerCtx;
            try {
                consumerCtx.consumer.streamAborted(consumerCtx, cause);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    String key = nextLogKey();
                    logger.warn("[{}] StreamConsumer.streamAborted() raised an exception.", key, t);
                    logger.warn("[{}] .. when invoked with the following cause:", key, cause);
                }
            } finally {
                invokeStreamClosed();
            }
        }
    }

    private final class StreamConsumerContextImpl implements StreamConsumerContext<T> {

        final EventExecutor executor;
        final StreamConsumer<T> consumer;
        final boolean singleThreaded;
        boolean nextCalled;

        @SuppressWarnings("unchecked")
        StreamConsumerContextImpl(EventExecutor executor, StreamConsumer<? super T> consumer) {
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            if (consumer == null) {
                throw new NullPointerException("consumer");
            }
            this.executor = executor;
            this.consumer = (StreamConsumer<T>) consumer;
            singleThreaded = executor == producerCtx.executor;
        }

        @Override
        public EventExecutor executor() {
            return executor;
        }

        @Override
        public T buffer() {
            return AbstractStream.this.buffer();
        }

        @Override
        public void discard() {
            switch (state) {
                case 2:
                    return;
                case 3:
                case 4:
                    fail();
            }

            T buffer = AbstractStream.this.buffer;
            if (buffer != null) {
                buffer.release();
            }

            state = 2;

            fireStreamDiscarded();
        }

        private void fireStreamDiscarded() {
            EventExecutor e = producerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamDiscarded();
            } else {
                e.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeStreamDiscarded();
                    }
                });
            }
        }

        private void invokeStreamDiscarded() {
            StreamProducerContextImpl producerCtx = AbstractStream.this.producerCtx;
            try {
                producerCtx.producer.streamDiscarded(producerCtx);
            } catch (Throwable t) {
                logger.warn("StreamProducer.streamDiscarded() raised an exception.", t);
            }
        }

        @Override
        public void reject(Throwable cause) {
            if (cause == null) {
                throw new NullPointerException("cause");
            }

            if (state != 1) {
                fail();
            }

            T buffer = AbstractStream.this.buffer;
            if (buffer != null) {
                buffer.release();
            }

            state = 3;

            fireStreamRejected(cause);
        }

        private void fireStreamRejected(final Throwable cause) {
            EventExecutor e = producerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamRejected(cause);
            } else {
                e.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeStreamRejected(cause);
                    }
                });
            }
        }

        private void invokeStreamRejected(Throwable cause) {
            StreamProducerContextImpl producerCtx = AbstractStream.this.producerCtx;
            try {
                producerCtx.producer.streamRejected(producerCtx, cause);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    String key = nextLogKey();
                    logger.warn("[{}] StreamProducer.streamRejected() raised an exception.", key, t);
                    logger.warn("[{}] .. when invoked with the following cause:", key, cause);
                }
            }
        }

        @Override
        public void next() {
            if (state != 1) {
                fail();
            }

            if (singleThreaded && IN_CONSUMER.get()) {
                nextCalled = true;
            } else {
                fireStreamConsumed();
            }
        }

        private void fireStreamConsumed() {
            EventExecutor e = producerCtx.executor;
            if (e.inEventLoop()) {
                invokeStreamConsumed();
            } else {
                Runnable task = invokeStreamConsumedTask;
                if (task == null) {
                    invokeStreamConsumedTask = task = new Runnable() {
                        @Override
                        public void run() {
                            invokeStreamConsumed();
                        }
                    };
                }
                e.execute(task);
            }
        }

        private void invokeStreamConsumed() {
            StreamProducerContextImpl producerCtx = AbstractStream.this.producerCtx;
            try {
                do {
                    consumerCtx.nextCalled = false;
                    producerCtx.producer.streamConsumed(producerCtx);
                } while (consumerCtx.nextCalled);
            } catch (Throwable t) {
                reject(new StreamProducerException(t));
            }
        }
    }
}
