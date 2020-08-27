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

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;
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

import static io.netty.channel.unix.Socket.isIPv6Preferred;


final class Native {
   private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);
    private static final int DEFAULT_RING_SIZE = SystemPropertyUtil.getInt("io.netty.uring.ringSize", 32);

     static {
        Selector selector = null;
        try {
            // We call Selector.open() as this will under the hood cause IOUtil to be loaded.
            // This is a workaround for a possible classloader deadlock that could happen otherwise:
            //
            // See https://github.com/netty/netty/issues/10187
            selector = Selector.open();
        } catch (IOException ignore) {
            // Just ignore
        }
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            Native.createFile();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException ignore) {
                // Just ignore
            }
        }
        Socket.initialize();
    }



    public static RingBuffer createRingBuffer(int ringSize) {
        //Todo throw Exception if it's null
        return ioUringSetup(ringSize);
    }

    public static RingBuffer createRingBuffer() {
        //Todo throw Exception if it's null
        return ioUringSetup(DEFAULT_RING_SIZE);
    }

    private static native RingBuffer ioUringSetup(int entries);

    public static native int ioUringEnter(int ringFd, int toSubmit, int minComplete, int flags);

    public static native int ioUringRegisterEventFd(int ringFd, int eventFd);

    public static native int ioUringUnregisterEventFd(int ringFd);

    public static native void eventFdWrite(int fd, long value);

    public static native void eventFdRead(int fd);

    public static FileDescriptor newEventFd() {
        return new FileDescriptor(eventFd());
    }

    public static native void ioUringExit(RingBuffer ringBuffer);

    public static native int initAddress(int fd, boolean ipv6, byte[] address, int scopeId, int port, long remoteMemoryAddress);

    private static native int eventFd();

    // for testing(it is only temporary)
    public static native int createFile();

    public static Socket newSocketStream() {
        return Socket.newSocketStream();
    }

    private Native() {
        // utility
    }

    // From io_uring native library
    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs().toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        String staticLibName = "netty_transport_native_io_uring";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.info("Failed to load io_uring");
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }
}
