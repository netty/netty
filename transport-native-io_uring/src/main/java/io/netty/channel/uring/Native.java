/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Locale;

public final class Native {

    static {
        loadNativeLibrary();
    }

    public static native long ioUringSetup(int entries);

    public static native int ioUringRead(long io_uring, long fd, long eventId, long bufferAddress, int pos, int limit);

    public static native int ioUringWrite(long io_uring, long fd, long eventId, long bufferAddress, int pos, int limit);

    public static native int ioUringAccept(long io_uring, long fd, byte[] addr);

    // return id
    public static native long ioUringWaitCqe(long io_uring);

    public static native long ioUringDeleteCqe(long io_uring, long cqeAddress);

    public static native long ioUringGetEventId(long cqeAddress);

    public static native int ioUringGetRes(long cqeAddress);

    public static native long ioUringClose(long io_uring);

    public static native long ioUringSubmit(long io_uring);

    public static native long ioUringGetSQE(long io_uring);

    public static native long ioUringGetQC(long io_uring);

    // for testing(it is only temporary)
    public static native long createFile();

    private Native() {
        // utility
    }

    // From epoll native library
    private static void loadNativeLibrary() {
        String name = SystemPropertyUtil.get("os.name").toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        String staticLibName = "netty_transport_native_io_uring";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            // try {
            // NativeLibraryLoader.load(staticLibName, cl);
            // System.out.println("Failed to load io_uring");
            // } catch (UnsatisfiedLinkError e2) {
            // ThrowableUtil.addSuppressed(e1, e2);
            // throw e1;
            // }
        }
    }
}
