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
final class NativeStaticallyReferencedJniMethods {

    private NativeStaticallyReferencedJniMethods() { }

    static native int sockNonblock();
    static native int sockCloexec();
    static native int afInet();
    static native int afInet6();
    static native int sizeofSockaddrIn();
    static native int sizeofSockaddrIn6();

    static native int etime();
    static native int ecanceled();
    static native int pollin();
    static native int pollout();
    static native int pollrdhup();

    static native int ioringOpWritev();
    static native int ioringOpPollAdd();
    static native int ioringOpPollRemove();
    static native int ioringOpTimeout();
    static native int ioringOpAccept();
    static native int ioringOpRead();
    static native int ioringOpWrite();
    static native int ioringOpConnect();
    static native int ioringOpClose();
    static native int ioringEnterGetevents();
    static native int iosqeAsync();
}
