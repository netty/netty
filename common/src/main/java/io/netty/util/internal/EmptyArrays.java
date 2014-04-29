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

package io.netty.util.internal;

import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;

public final class EmptyArrays {

    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final boolean[] EMPTY_BOOLEANS = new boolean[0];
    public static final double[] EMPTY_DOUBLES = new double[0];
    public static final float[] EMPTY_FLOATS = new float[0];
    public static final int[] EMPTY_INTS = new int[0];
    public static final short[] EMPTY_SHORTS = new short[0];
    public static final long[] EMPTY_LONGS = new long[0];
    public static final Object[] EMPTY_OBJECTS = new Object[0];
    public static final String[] EMPTY_STRINGS = new String[0];
    public static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];
    public static final ByteBuffer[] EMPTY_BYTE_BUFFERS = new ByteBuffer[0];
    public static final X509Certificate[] EMPTY_X509_CERTIFICATES = new X509Certificate[0];

    private EmptyArrays() { }
}
