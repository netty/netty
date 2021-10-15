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
package io.netty.channel.kqueue;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Unix;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;

import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.connectDataIdempotent;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.connectResumeOnReadWrite;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evAdd;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evClear;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evDelete;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evDisable;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evEOF;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evEnable;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evError;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evfiltRead;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evfiltSock;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evfiltUser;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.evfiltWrite;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.fastOpenClient;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.fastOpenServer;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.noteConnReset;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.noteDisconnected;
import static io.netty.channel.kqueue.KQueueStaticallyReferencedJniMethods.noteReadClosed;
import static io.netty.channel.unix.Errors.newIOException;

/**
 * Native helper methods
 * <p><strong>Internal usage only!</strong>
 */
final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);

    static {
        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possiblity of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(Native.class,
                // netty_kqueue_bsdsocket
                PeerCredentials.class, DefaultFileRegion.class, FileChannel.class, java.io.FileDescriptor.class
        );

        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            sizeofKEvent();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }
        Unix.registerInternal(new Runnable() {
            @Override
            public void run() {
                registerUnix();
            }
        });
    }

    private static native int registerUnix();

    static final short EV_ADD = evAdd();
    static final short EV_ENABLE = evEnable();
    static final short EV_DISABLE = evDisable();
    static final short EV_DELETE = evDelete();
    static final short EV_CLEAR = evClear();
    static final short EV_ERROR = evError();
    static final short EV_EOF = evEOF();

    static final int NOTE_READCLOSED = noteReadClosed();
    static final int NOTE_CONNRESET = noteConnReset();
    static final int NOTE_DISCONNECTED = noteDisconnected();

    static final int NOTE_RDHUP = NOTE_READCLOSED | NOTE_CONNRESET | NOTE_DISCONNECTED;

    // Commonly used combinations of EV defines
    static final short EV_ADD_CLEAR_ENABLE = (short) (EV_ADD | EV_CLEAR | EV_ENABLE);
    static final short EV_DELETE_DISABLE = (short) (EV_DELETE | EV_DISABLE);

    static final short EVFILT_READ = evfiltRead();
    static final short EVFILT_WRITE = evfiltWrite();
    static final short EVFILT_USER = evfiltUser();
    static final short EVFILT_SOCK = evfiltSock();

    // Flags for connectx(2)
    private static final int CONNECT_RESUME_ON_READ_WRITE = connectResumeOnReadWrite();
    private static final int CONNECT_DATA_IDEMPOTENT = connectDataIdempotent();
    static final int CONNECT_TCP_FASTOPEN = CONNECT_RESUME_ON_READ_WRITE | CONNECT_DATA_IDEMPOTENT;
    static final boolean IS_SUPPORTING_TCP_FASTOPEN_CLIENT = isSupportingFastOpenClient();
    static final boolean IS_SUPPORTING_TCP_FASTOPEN_SERVER = isSupportingFastOpenServer();

    static FileDescriptor newKQueue() {
        return new FileDescriptor(kqueueCreate());
    }

    static int keventWait(int kqueueFd, KQueueEventArray changeList, KQueueEventArray eventList,
                          int tvSec, int tvNsec) throws IOException {
        int ready = keventWait(kqueueFd, changeList.memoryAddress(), changeList.size(),
                               eventList.memoryAddress(), eventList.capacity(), tvSec, tvNsec);
        if (ready < 0) {
            throw newIOException("kevent", ready);
        }
        return ready;
    }

    private static native int kqueueCreate();
    private static native int keventWait(int kqueueFd, long changeListAddress, int changeListLength,
                                         long eventListAddress, int eventListLength, int tvSec, int tvNsec);
    static native int keventTriggerUserEvent(int kqueueFd, int ident);
    static native int keventAddUserEvent(int kqueueFd, int ident);

    // kevent related
    static native int sizeofKEvent();
    static native int offsetofKEventIdent();
    static native int offsetofKEventFlags();
    static native int offsetofKEventFFlags();
    static native int offsetofKEventFilter();
    static native int offsetofKeventData();

    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs();
        if (!"osx".equals(name) && !name.contains("bsd")) {
            throw new IllegalStateException("Only supported on OSX/BSD");
        }
        String staticLibName = "netty_transport_native_kqueue";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }

    private static boolean isSupportingFastOpenClient() {
        try {
            return fastOpenClient() == 1;
        } catch (Exception e) {
            logger.debug("Failed to probe fastOpenClient sysctl, assuming client-side TCP FastOpen cannot be used.", e);
        }
        return false;
    }

    private static boolean isSupportingFastOpenServer() {
        try {
            return fastOpenServer() == 1;
        } catch (Exception e) {
            logger.debug("Failed to probe fastOpenServer sysctl, assuming server-side TCP FastOpen cannot be used.", e);
        }
        return false;
    }

    private Native() {
        // utility
    }
}
