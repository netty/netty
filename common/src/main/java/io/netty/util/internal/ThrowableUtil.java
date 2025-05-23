/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public final class ThrowableUtil {

    private ThrowableUtil() { }

    /**
     * Set the {@link StackTraceElement} for the given {@link Throwable}, using the {@link Class} and method name.
     */
    public static <T extends Throwable> T unknownStackTrace(T cause, Class<?> clazz, String method) {
        cause.setStackTrace(new StackTraceElement[] { new StackTraceElement(clazz.getName(), method, null, -1)});
        return cause;
    }

    /**
     * Gets the stack trace from a Throwable as a String.
     *
     * @param cause the {@link Throwable} to be examined
     * @return the stack trace as generated by {@link Throwable#printStackTrace(java.io.PrintWriter)} method.
     */
    @Deprecated
    public static String stackTraceToString(Throwable cause) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(out);
        cause.printStackTrace(pout);
        pout.flush();
        try {
            return new String(out.toByteArray());
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {
                // ignore as should never happen
            }
        }
    }

    @Deprecated
    public static boolean haveSuppressed() {
        return true;
    }

    public static void addSuppressed(Throwable target, Throwable suppressed) {
        target.addSuppressed(suppressed);
    }

    public static void addSuppressedAndClear(Throwable target, List<Throwable> suppressed) {
        addSuppressed(target, suppressed);
        suppressed.clear();
    }

    public static void addSuppressed(Throwable target, List<Throwable> suppressed) {
        for (Throwable t : suppressed) {
            addSuppressed(target, t);
        }
    }

    public static Throwable[] getSuppressed(Throwable source) {
        return source.getSuppressed();
    }
}
