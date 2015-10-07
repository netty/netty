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
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include "netty_unix_errors.h"
#include "io_netty_channel_unix_Errors.h"

static jclass runtimeExceptionClass = NULL;
static jclass channelExceptionClass = NULL;
static jclass ioExceptionClass = NULL;
static jclass closedChannelExceptionClass = NULL;
static jmethodID closedChannelExceptionMethodId = NULL;

/** Notice: every usage of exceptionMessage needs to release the allocated memory for the sequence of char */
static char* exceptionMessage(char* msg, int error) {
    if (error < 0) {
        // some functions return negative values
        // and it's hard to keep track of when to send -error and when not
        // this will just take care when things are forgotten
        // what would generate a proper error
        error = error * -1;
    }
    //strerror is returning a constant, so no need to free anything coming from strerror
    char* err = strerror(error);
    char* result = malloc(strlen(msg) + strlen(err) + 1);
    strcpy(result, msg);
    strcat(result, err);
    return result;
}

// Exported C methods
void netty_unix_errors_throwRuntimeException(JNIEnv* env, char* message) {
    (*env)->ThrowNew(env, runtimeExceptionClass, message);
}

void netty_unix_errors_throwRuntimeExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    (*env)->ThrowNew(env, runtimeExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwChannelExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    (*env)->ThrowNew(env, channelExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwIOException(JNIEnv* env, char* message) {
    (*env)->ThrowNew(env, ioExceptionClass, message);
}

void netty_unix_errors_throwIOExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    (*env)->ThrowNew(env, ioExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwClosedChannelException(JNIEnv* env) {
    jobject exception = (*env)->NewObject(env, closedChannelExceptionClass, closedChannelExceptionMethodId);
    (*env)->Throw(env, exception);
}

void netty_unix_errors_throwOutOfMemoryError(JNIEnv* env) {
    jclass exceptionClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
    (*env)->ThrowNew(env, exceptionClass, "");
}

jint netty_unix_errors_JNI_OnLoad(JNIEnv* env) {
    jclass localRuntimeExceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (localRuntimeExceptionClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    runtimeExceptionClass = (jclass) (*env)->NewGlobalRef(env, localRuntimeExceptionClass);
    if (runtimeExceptionClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }

    jclass localChannelExceptionClass = (*env)->FindClass(env, "io/netty/channel/ChannelException");
    if (localChannelExceptionClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    channelExceptionClass = (jclass) (*env)->NewGlobalRef(env, localChannelExceptionClass);
    if (channelExceptionClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }

    // cache classes that are used within other jni methods for performance reasons
    jclass localClosedChannelExceptionClass = (*env)->FindClass(env, "java/nio/channels/ClosedChannelException");
    if (localClosedChannelExceptionClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    closedChannelExceptionClass = (jclass) (*env)->NewGlobalRef(env, localClosedChannelExceptionClass);
    if (closedChannelExceptionClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    closedChannelExceptionMethodId = (*env)->GetMethodID(env, closedChannelExceptionClass, "<init>", "()V");
    if (closedChannelExceptionMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: ClosedChannelException.<init>()");
        return JNI_ERR;
    }

    jclass localIoExceptionClass = (*env)->FindClass(env, "java/io/IOException");
    if (localIoExceptionClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    ioExceptionClass = (jclass) (*env)->NewGlobalRef(env, localIoExceptionClass);
    if (ioExceptionClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

void netty_unix_errors_JNI_OnUnLoad(JNIEnv* env) {
    // delete global references so the GC can collect them
    if (runtimeExceptionClass != NULL) {
        (*env)->DeleteGlobalRef(env, runtimeExceptionClass);
        runtimeExceptionClass = NULL;
    }
    if (channelExceptionClass != NULL) {
        (*env)->DeleteGlobalRef(env, channelExceptionClass);
        channelExceptionClass = NULL;
    }
    if (ioExceptionClass != NULL) {
        (*env)->DeleteGlobalRef(env, ioExceptionClass);
        ioExceptionClass = NULL;
    }
    if (closedChannelExceptionClass != NULL) {
        (*env)->DeleteGlobalRef(env, closedChannelExceptionClass);
        closedChannelExceptionClass = NULL;
    }
}

// Exported JNI Methods
JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoENOTCONN(JNIEnv* env, jclass clazz) {
    return ENOTCONN;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoEBADF(JNIEnv* env, jclass clazz) {
    return EBADF;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoEPIPE(JNIEnv* env, jclass clazz) {
    return EPIPE;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoECONNRESET(JNIEnv* env, jclass clazz) {
    return ECONNRESET;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoEAGAIN(JNIEnv* env, jclass clazz) {
    return EAGAIN;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoEWOULDBLOCK(JNIEnv* env, jclass clazz) {
    return EWOULDBLOCK;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Errors_errnoEINPROGRESS(JNIEnv* env, jclass clazz) {
    return EINPROGRESS;
}

JNIEXPORT jstring JNICALL Java_io_netty_channel_unix_Errors_strError(JNIEnv* env, jclass clazz, jint error) {
    return (*env)->NewStringUTF(env, strerror(error));
}
