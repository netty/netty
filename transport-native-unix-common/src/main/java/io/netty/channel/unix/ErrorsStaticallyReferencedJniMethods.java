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
package io.netty.channel.unix;

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
final class ErrorsStaticallyReferencedJniMethods {

    private ErrorsStaticallyReferencedJniMethods() { }

    static native int errnoENOENT();
    static native int errnoEBADF();
    static native int errnoEPIPE();
    static native int errnoECONNRESET();
    static native int errnoENOTCONN();
    static native int errnoEAGAIN();
    static native int errnoEWOULDBLOCK();
    static native int errnoEINPROGRESS();
    static native int errorECONNREFUSED();
    static native int errorEISCONN();
    static native int errorEALREADY();
    static native int errorENETUNREACH();
    static native int errorEHOSTUNREACH();
    static native String strError(int err);
}
