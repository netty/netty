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
import java.net.NoRouteToHostException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;

import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.*;

/**
 * <strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link ErrorsStaticallyReferencedJniMethods}.
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
    public static final int ERROR_ECONNREFUSED_NEGATIVE = -errorECONNREFUSED();
    public static final int ERROR_EISCONN_NEGATIVE = -errorEISCONN();
    public static final int ERROR_EALREADY_NEGATIVE = -errorEALREADY();
    public static final int ERROR_ENETUNREACH_NEGATIVE = -errorENETUNREACH();

    /**
     * Holds the mappings for errno codes to String messages.
     * This eliminates the need to call back into JNI to get the right String message on an exception
     * and thus is faster.
     *
     * The array length of 512 should be more then enough because errno.h only holds < 200 codes.
     */
    private static final String[] ERRORS = new String[512];

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

    static final class NativeConnectException extends ConnectException {
        private static final long serialVersionUID = -5532328671712318161L;
        private final int expectedErr;
        NativeConnectException(String method, int expectedErr) {
            super(method);
            this.expectedErr = expectedErr;
        }

        int expectedErr() {
            return expectedErr;
        }
    }

    static {
        for (int i = 0; i < ERRORS.length; i++) {
            // This is ok as strerror returns 'Unknown error i' when the message is not known.
            ERRORS[i] = strError(i);
        }
    }

    static void throwConnectException(String method, NativeConnectException refusedCause, int err)
            throws IOException {
        if (err == refusedCause.expectedErr()) {
            throw refusedCause;
        }
        if (err == ERROR_EALREADY_NEGATIVE) {
            throw new ConnectionPendingException();
        }
        if (err == ERROR_ENETUNREACH_NEGATIVE) {
            throw new NoRouteToHostException();
        }
        if (err == ERROR_EISCONN_NEGATIVE) {
            throw new AlreadyConnectedException();
        }
        throw new ConnectException(method + "() failed: " + ERRORS[-err]);
    }

    public static NativeIoException newConnectionResetException(String method, int errnoNegative) {
        NativeIoException exception = newIOException(method, errnoNegative);
        exception.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        return exception;
    }

    public static NativeIoException newIOException(String method, int err) {
        return new NativeIoException(method + "() failed: " + ERRORS[-err], err);
    }

    public static int ioResult(String method, int err, NativeIoException resetCause,
                               ClosedChannelException closedCause) throws IOException {
        // network stack saturated... try again later
        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
            return 0;
        }
        if (err == resetCause.expectedErr()) {
            throw resetCause;
        }
        if (err == ERRNO_EBADF_NEGATIVE) {
            throw closedCause;
        }
        if (err == ERRNO_ENOTCONN_NEGATIVE) {
            throw new NotYetConnectedException();
        }

        // TODO: We could even go further and use a pre-instantiated IOException for the other error codes, but for
        //       all other errors it may be better to just include a stack trace.
        throw newIOException(method, err);
    }

    private Errors() { }
}
