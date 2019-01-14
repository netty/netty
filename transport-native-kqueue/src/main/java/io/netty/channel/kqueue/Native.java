/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.Locale;

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
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            sizeofKEvent();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }
        Socket.initialize();
    }

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
        String name = SystemPropertyUtil.get("os.name").toLowerCase(Locale.UK).trim();
        if (!name.startsWith("mac") && !name.contains("bsd") && !name.startsWith("darwin")) {
            throw new IllegalStateException("Only supported on BSD");
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

    private Native() {
        // utility
    }
}
