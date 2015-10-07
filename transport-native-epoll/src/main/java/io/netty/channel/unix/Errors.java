/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.EmptyArrays;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;

/**
 * <strong>Internal usage only!</strong>
 */
public final class Errors {
    // As all our JNI methods return -errno on error we need to compare with the negative errno codes.
    public static final int ERRNO_ENOTCONN_NEGATIVE = -errnoENOTCONN();
    public static final int ERRNO_EBADF_NEGATIVE = -errnoEBADF();
    public static final int ERRNO_EPIPE_NEGATIVE = -errnoEPIPE();
    public static final int ERRNO_ECONNRESET_NEGATIVE = -errnoECONNRESET();
    public static final int ERRNO_EAGAIN_NEGATIVE = -errnoEAGAIN();
    public static final int ERRNO_EWOULDBLOCK_NEGATIVE = -errnoEWOULDBLOCK();
    public static final int ERRNO_EINPROGRESS_NEGATIVE = -errnoEINPROGRESS();

    /**
     * Holds the mappings for errno codes to String messages.
     * This eliminates the need to call back into JNI to get the right String message on an exception
     * and thus is faster.
     *
     * The array length of 512 should be more then enough because errno.h only holds < 200 codes.
     */
    private static final String[] ERRORS = new String[512];

    // Pre-instantiated exceptions which does not need any stacktrace and
    // can be thrown multiple times for performance reasons.
    static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION;
    static final NativeIoException CONNECTION_NOT_CONNECTED_SHUTDOWN_EXCEPTION;
    static final NativeIoException CONNECTION_RESET_EXCEPTION_WRITE;
    static final NativeIoException CONNECTION_RESET_EXCEPTION_WRITEV;
    static final NativeIoException CONNECTION_RESET_EXCEPTION_READ;
    static final NativeIoException CONNECTION_RESET_EXCEPTION_SENDTO;
    static final NativeIoException CONNECTION_RESET_EXCEPTION_SENDMSG;

    /**
     * <strong>Internal usage only!</strong>
     */
    public static final class NativeIoException extends IOException {
        private static final long serialVersionUID = 8222160204268655526L;
        private final int expectedErr;
        public NativeIoException(String method, int expectedErr) {
            super(method);
            this.expectedErr = expectedErr;
        }

        public int expectedErr() {
            return expectedErr;
        }
    }

    static {
        for (int i = 0; i < ERRORS.length; i++) {
            // This is ok as strerror returns 'Unknown error i' when the message is not known.
            ERRORS[i] = strError(i);
        }

        CONNECTION_RESET_EXCEPTION_READ = newConnectionResetException("syscall:read(...)",
                ERRNO_ECONNRESET_NEGATIVE);
        CONNECTION_RESET_EXCEPTION_WRITE = newConnectionResetException("syscall:write(...)",
                ERRNO_EPIPE_NEGATIVE);
        CONNECTION_RESET_EXCEPTION_WRITEV = newConnectionResetException("syscall:writev(...)",
                ERRNO_EPIPE_NEGATIVE);
        CONNECTION_RESET_EXCEPTION_SENDTO = newConnectionResetException("syscall:sendto(...)",
                ERRNO_EPIPE_NEGATIVE);
        CONNECTION_RESET_EXCEPTION_SENDMSG = newConnectionResetException("syscall:sendmsg(...)",
                ERRNO_EPIPE_NEGATIVE);
        CONNECTION_NOT_CONNECTED_SHUTDOWN_EXCEPTION = newConnectionResetException("syscall:shutdown(...)",
                ERRNO_ENOTCONN_NEGATIVE);
        CLOSED_CHANNEL_EXCEPTION = new ClosedChannelException();
        CLOSED_CHANNEL_EXCEPTION.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    static ConnectException newConnectException(String method, int err) {
        return new ConnectException(method + "() failed: " + ERRORS[-err]);
    }

    public static NativeIoException newConnectionResetException(String method, int errnoNegative) {
        NativeIoException exception = newIOException(method, errnoNegative);
        exception.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        return exception;
    }

    public static NativeIoException newIOException(String method, int err) {
        return new NativeIoException(method + "() failed: " + ERRORS[-err], err);
    }

    public static int ioResult(String method, int err, NativeIoException resetCause) throws IOException {
        // network stack saturated... try again later
        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
            return 0;
        }
        if (err == resetCause.expectedErr()) {
            throw resetCause;
        }
        if (err == ERRNO_EBADF_NEGATIVE || err == ERRNO_ENOTCONN_NEGATIVE) {
            throw CLOSED_CHANNEL_EXCEPTION;
        }
        // TODO: We could even go further and use a pre-instantiated IOException for the other error codes, but for
        //       all other errors it may be better to just include a stack trace.
        throw newIOException(method, err);
    }

    private static native int errnoEBADF();
    private static native int errnoEPIPE();
    private static native int errnoECONNRESET();
    private static native int errnoENOTCONN();
    private static native int errnoEAGAIN();
    private static native int errnoEWOULDBLOCK();
    private static native int errnoEINPROGRESS();
    private static native String strError(int err);

    private Errors() { }
}
