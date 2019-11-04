/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Exception thrown when an HTTP/2 error was encountered.
 */
@UnstableApi
public class Http2Exception extends Exception {
    private static final long serialVersionUID = -6941186345430164209L;
    private final Http2Error error;
    private final ShutdownHint shutdownHint;

    public Http2Exception(Http2Error error) {
        this(error, ShutdownHint.HARD_SHUTDOWN);
    }

    public Http2Exception(Http2Error error, ShutdownHint shutdownHint) {
        this.error = checkNotNull(error, "error");
        this.shutdownHint = checkNotNull(shutdownHint, "shutdownHint");
    }

    public Http2Exception(Http2Error error, String message) {
        this(error, message, ShutdownHint.HARD_SHUTDOWN);
    }

    public Http2Exception(Http2Error error, String message, ShutdownHint shutdownHint) {
        super(message);
        this.error = checkNotNull(error, "error");
        this.shutdownHint = checkNotNull(shutdownHint, "shutdownHint");
    }

    public Http2Exception(Http2Error error, String message, Throwable cause) {
        this(error, message, cause, ShutdownHint.HARD_SHUTDOWN);
    }

    public Http2Exception(Http2Error error, String message, Throwable cause, ShutdownHint shutdownHint) {
        super(message, cause);
        this.error = checkNotNull(error, "error");
        this.shutdownHint = checkNotNull(shutdownHint, "shutdownHint");
    }

    static Http2Exception newStatic(Http2Error error, String message, ShutdownHint shutdownHint) {
        if (PlatformDependent.javaVersion() >= 7) {
            return new Http2Exception(error, message, shutdownHint, true);
        }
        return new Http2Exception(error, message, shutdownHint);
    }

    @SuppressJava6Requirement(reason = "uses Java 7+ Exception.<init>(String, Throwable, boolean, boolean)" +
            " but is guarded by version checks")
    private Http2Exception(Http2Error error, String message, ShutdownHint shutdownHint, boolean shared) {
        super(message, null, false, true);
        assert shared;
        this.error = checkNotNull(error, "error");
        this.shutdownHint = checkNotNull(shutdownHint, "shutdownHint");
    }

    public Http2Error error() {
        return error;
    }

    /**
     * Provide a hint as to what type of shutdown should be executed. Note this hint may be ignored.
     */
    public ShutdownHint shutdownHint() {
        return shutdownHint;
    }

    /**
     * Use if an error has occurred which can not be isolated to a single stream, but instead applies
     * to the entire connection.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return An exception which can be translated into an HTTP/2 error.
     */
    public static Http2Exception connectionError(Http2Error error, String fmt, Object... args) {
        return new Http2Exception(error, String.format(fmt, args));
    }

    /**
     * Use if an error has occurred which can not be isolated to a single stream, but instead applies
     * to the entire connection.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param cause The object which caused the error.
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return An exception which can be translated into an HTTP/2 error.
     */
    public static Http2Exception connectionError(Http2Error error, Throwable cause,
            String fmt, Object... args) {
        return new Http2Exception(error, String.format(fmt, args), cause);
    }

    /**
     * Use if an error has occurred which can not be isolated to a single stream, but instead applies
     * to the entire connection.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return An exception which can be translated into an HTTP/2 error.
     */
    public static Http2Exception closedStreamError(Http2Error error, String fmt, Object... args) {
        return new ClosedStreamCreationException(error, String.format(fmt, args));
    }

    /**
     * Use if an error which can be isolated to a single stream has occurred.  If the {@code id} is not
     * {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a {@link Http2Exception.StreamException} will be returned.
     * Otherwise the error is considered a connection error and a {@link Http2Exception} is returned.
     * @param id The stream id for which the error is isolated to.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return If the {@code id} is not
     * {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a {@link Http2Exception.StreamException} will be returned.
     * Otherwise the error is considered a connection error and a {@link Http2Exception} is returned.
     */
    public static Http2Exception streamError(int id, Http2Error error, String fmt, Object... args) {
        return CONNECTION_STREAM_ID == id ?
                Http2Exception.connectionError(error, fmt, args) :
                    new StreamException(id, error, String.format(fmt, args));
    }

    /**
     * Use if an error which can be isolated to a single stream has occurred.  If the {@code id} is not
     * {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a {@link Http2Exception.StreamException} will be returned.
     * Otherwise the error is considered a connection error and a {@link Http2Exception} is returned.
     * @param id The stream id for which the error is isolated to.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param cause The object which caused the error.
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return If the {@code id} is not
     * {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a {@link Http2Exception.StreamException} will be returned.
     * Otherwise the error is considered a connection error and a {@link Http2Exception} is returned.
     */
    public static Http2Exception streamError(int id, Http2Error error, Throwable cause,
            String fmt, Object... args) {
        return CONNECTION_STREAM_ID == id ?
                Http2Exception.connectionError(error, cause, fmt, args) :
                    new StreamException(id, error, String.format(fmt, args), cause);
    }

    /**
     * A specific stream error resulting from failing to decode headers that exceeds the max header size list.
     * If the {@code id} is not {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a
     * {@link Http2Exception.StreamException} will be returned. Otherwise the error is considered a
     * connection error and a {@link Http2Exception} is returned.
     * @param id The stream id for which the error is isolated to.
     * @param error The type of error as defined by the HTTP/2 specification.
     * @param onDecode Whether this error was caught while decoding headers
     * @param fmt String with the content and format for the additional debug data.
     * @param args Objects which fit into the format defined by {@code fmt}.
     * @return If the {@code id} is not
     * {@link Http2CodecUtil#CONNECTION_STREAM_ID} then a {@link HeaderListSizeException}
     * will be returned. Otherwise the error is considered a connection error and a {@link Http2Exception} is
     * returned.
     */
    public static Http2Exception headerListSizeError(int id, Http2Error error, boolean onDecode,
            String fmt, Object... args) {
        return CONNECTION_STREAM_ID == id ?
                Http2Exception.connectionError(error, fmt, args) :
                    new HeaderListSizeException(id, error, String.format(fmt, args), onDecode);
    }

    /**
     * Check if an exception is isolated to a single stream or the entire connection.
     * @param e The exception to check.
     * @return {@code true} if {@code e} is an instance of {@link Http2Exception.StreamException}.
     * {@code false} otherwise.
     */
    public static boolean isStreamError(Http2Exception e) {
        return e instanceof StreamException;
    }

    /**
     * Get the stream id associated with an exception.
     * @param e The exception to get the stream id for.
     * @return {@link Http2CodecUtil#CONNECTION_STREAM_ID} if {@code e} is a connection error.
     * Otherwise the stream id associated with the stream error.
     */
    public static int streamId(Http2Exception e) {
        return isStreamError(e) ? ((StreamException) e).streamId() : CONNECTION_STREAM_ID;
    }

    /**
     * Provides a hint as to if shutdown is justified, what type of shutdown should be executed.
     */
    public enum ShutdownHint {
        /**
         * Do not shutdown the underlying channel.
         */
        NO_SHUTDOWN,
        /**
         * Attempt to execute a "graceful" shutdown. The definition of "graceful" is left to the implementation.
         * An example of "graceful" would be wait for some amount of time until all active streams are closed.
         */
        GRACEFUL_SHUTDOWN,
        /**
         * Close the channel immediately after a {@code GOAWAY} is sent.
         */
        HARD_SHUTDOWN
    }

    /**
     * Used when a stream creation attempt fails but may be because the stream was previously closed.
     */
    public static final class ClosedStreamCreationException extends Http2Exception {
        private static final long serialVersionUID = -6746542974372246206L;

        public ClosedStreamCreationException(Http2Error error) {
            super(error);
        }

        public ClosedStreamCreationException(Http2Error error, String message) {
            super(error, message);
        }

        public ClosedStreamCreationException(Http2Error error, String message, Throwable cause) {
            super(error, message, cause);
        }
    }

    /**
     * Represents an exception that can be isolated to a single stream (as opposed to the entire connection).
     */
    public static class StreamException extends Http2Exception {
        private static final long serialVersionUID = 602472544416984384L;
        private final int streamId;

        StreamException(int streamId, Http2Error error, String message) {
            super(error, message, ShutdownHint.NO_SHUTDOWN);
            this.streamId = streamId;
        }

        StreamException(int streamId, Http2Error error, String message, Throwable cause) {
            super(error, message, cause, ShutdownHint.NO_SHUTDOWN);
            this.streamId = streamId;
        }

        public int streamId() {
            return streamId;
        }
    }

    public static final class HeaderListSizeException extends StreamException {
        private static final long serialVersionUID = -8807603212183882637L;

        private final boolean decode;

        HeaderListSizeException(int streamId, Http2Error error, String message, boolean decode) {
            super(streamId, error, message);
            this.decode = decode;
        }

        public boolean duringDecode() {
            return decode;
        }
    }

    /**
     * Provides the ability to handle multiple stream exceptions with one throw statement.
     */
    public static final class CompositeStreamException extends Http2Exception implements Iterable<StreamException> {
        private static final long serialVersionUID = 7091134858213711015L;
        private final List<StreamException> exceptions;

        public CompositeStreamException(Http2Error error, int initialCapacity) {
            super(error, ShutdownHint.NO_SHUTDOWN);
            exceptions = new ArrayList<StreamException>(initialCapacity);
        }

        public void add(StreamException e) {
            exceptions.add(e);
        }

        @Override
        public Iterator<StreamException> iterator() {
            return exceptions.iterator();
        }
    }
}
