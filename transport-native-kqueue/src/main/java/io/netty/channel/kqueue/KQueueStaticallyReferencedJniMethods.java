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

/**
 * This class is necessary to break the following cyclic dependency:
 * <ol>
 * <li>JNI_OnLoad</li>
 * <li>JNI Calls FindClass because RegisterNatives (used to register JNI methods) requires a class</li>
 * <li>FindClass loads the class, but static members variables of that class attempt to call a JNI method which has not
 * yet been registered.</li>
 * <li>java.lang.UnsatisfiedLinkError is thrown because native method has not yet been registered.</li>
 * </ol>
 * Static members which call JNI methods must not be declared in this class!
 */
final class KQueueStaticallyReferencedJniMethods {
    private KQueueStaticallyReferencedJniMethods() { }

    static native short evAdd();
    static native short evEnable();
    static native short evDisable();
    static native short evDelete();
    static native short evClear();
    static native short evEOF();
    static native short evError();

    // data/hint fflags for EVFILT_SOCK, shared with userspace.
    static native short noteReadClosed();
    static native short noteConnReset();
    static native short noteDisconnected();

    static native short evfiltRead();
    static native short evfiltWrite();
    static native short evfiltUser();
    static native short evfiltSock();

    // Flags for connectx(2)
    static native int connectResumeOnReadWrite();
    static native int connectDataIdempotent();

    // Sysctl values.
    static native int fastOpenClient();
    static native int fastOpenServer();
}
