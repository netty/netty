/*
 * Copyright 2015 The Netty Project
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
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <jni.h>
#include "netty_unix_errors.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"
#include "netty_jni_util.h"

#define ERRORS_CLASSNAME "io/netty/channel/unix/ErrorsStaticallyReferencedJniMethods"

static jclass oomErrorClass = NULL;
static jclass runtimeExceptionClass = NULL;
static jclass channelExceptionClass = NULL;
static jclass ioExceptionClass = NULL;
static jclass portUnreachableExceptionClass = NULL;
static jclass closedChannelExceptionClass = NULL;
static jmethodID closedChannelExceptionMethodId = NULL;

/**
 Our `strerror_r` wrapper makes sure that the function is XSI compliant,
 even on platforms where the GNU variant is exposed.
 Note: `strerrbuf` must be initialized to all zeros prior to calling this function.
 XSI or GNU functions do not have such a requirement, but our wrappers do.
 */
#if (_POSIX_C_SOURCE >= 200112L || _XOPEN_SOURCE >= 600 || __APPLE__) && ! _GNU_SOURCE
    static inline int strerror_r_xsi(int errnum, char *strerrbuf, size_t buflen) {
        return strerror_r(errnum, strerrbuf, buflen);
    }
#else
    static inline int strerror_r_xsi(int errnum, char *strerrbuf, size_t buflen) {
        char* tmp = strerror_r(errnum, strerrbuf, buflen);
        if (strerrbuf[0] == '\0') {
            // Our output buffer was not used. Copy from tmp.
            strncpy(strerrbuf, tmp, buflen - 1); // Use (buflen - 1) to avoid overwriting terminating \0.
        }
        if (errno != 0) {
            return -1;
        }
        return 0;
    }
#endif

/** Notice: every usage of exceptionMessage needs to release the allocated memory for the sequence of char */
static char* exceptionMessage(char* msg, int error) {
    if (error < 0) {
        // Error may be negative because some functions return negative values. We should make sure it is always
        // positive when passing to standard library functions.
        error = -error;
    }

    int buflen = 32;
    char* strerrbuf = NULL;
    int result = 0;
    do {
        buflen = buflen * 2;
        if (buflen >= 2048) {
            break; // Limit buffer growth.
        }
        if (strerrbuf != NULL) {
            free(strerrbuf);
        }
        strerrbuf = calloc(buflen, sizeof(char));
        result = strerror_r_xsi(error, strerrbuf, buflen);
        if (result == -1) {
            result = errno;
        }
    } while (result == ERANGE);

    char* combined = netty_jni_util_prepend(msg, strerrbuf);
    free(strerrbuf);
    return combined;
}

// Exported C methods
void netty_unix_errors_throwRuntimeException(JNIEnv* env, char* message) {
    (*env)->ThrowNew(env, runtimeExceptionClass, message);
}

void netty_unix_errors_throwRuntimeExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    if (allocatedMessage == NULL) {
        return;
    }
    (*env)->ThrowNew(env, runtimeExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwChannelExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    if (allocatedMessage == NULL) {
        return;
    }
    (*env)->ThrowNew(env, channelExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwIOException(JNIEnv* env, char* message) {
    (*env)->ThrowNew(env, ioExceptionClass, message);
}

void netty_unix_errors_throwPortUnreachableException(JNIEnv* env, char* message) {
    (*env)->ThrowNew(env, portUnreachableExceptionClass, message);
}

void netty_unix_errors_throwIOExceptionErrorNo(JNIEnv* env, char* message, int errorNumber) {
    char* allocatedMessage = exceptionMessage(message, errorNumber);
    if (allocatedMessage == NULL) {
        return;
    }
    (*env)->ThrowNew(env, ioExceptionClass, allocatedMessage);
    free(allocatedMessage);
}

void netty_unix_errors_throwClosedChannelException(JNIEnv* env) {
    jobject exception = (*env)->NewObject(env, closedChannelExceptionClass, closedChannelExceptionMethodId);
    if (exception == NULL) {
        return;
    }
    (*env)->Throw(env, exception);
}

void netty_unix_errors_throwOutOfMemoryError(JNIEnv* env) {
    (*env)->ThrowNew(env, oomErrorClass, "");
}

// JNI Registered Methods Begin
static jint netty_unix_errors_errnoENOENT(JNIEnv* env, jclass clazz) {
    return ENOENT;
}

static jint netty_unix_errors_errnoENOTCONN(JNIEnv* env, jclass clazz) {
    return ENOTCONN;
}

static jint netty_unix_errors_errnoEBADF(JNIEnv* env, jclass clazz) {
    return EBADF;
}

static jint netty_unix_errors_errnoEPIPE(JNIEnv* env, jclass clazz) {
    return EPIPE;
}

static jint netty_unix_errors_errnoECONNRESET(JNIEnv* env, jclass clazz) {
    return ECONNRESET;
}

static jint netty_unix_errors_errnoEAGAIN(JNIEnv* env, jclass clazz) {
    return EAGAIN;
}

static jint netty_unix_errors_errnoEWOULDBLOCK(JNIEnv* env, jclass clazz) {
    return EWOULDBLOCK;
}

static jint netty_unix_errors_errnoEINPROGRESS(JNIEnv* env, jclass clazz) {
    return EINPROGRESS;
}

static jint netty_unix_errors_errorECONNREFUSED(JNIEnv* env, jclass clazz) {
    return ECONNREFUSED;
}

static jint netty_unix_errors_errorEISCONN(JNIEnv* env, jclass clazz) {
    return EISCONN;
}

static jint netty_unix_errors_errorEALREADY(JNIEnv* env, jclass clazz) {
    return EALREADY;
}

static jint netty_unix_errors_errorENETUNREACH(JNIEnv* env, jclass clazz) {
    return ENETUNREACH;
}

static jstring netty_unix_errors_strError(JNIEnv* env, jclass clazz, jint error) {
    return (*env)->NewStringUTF(env, strerror(error));
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "errnoENOENT", "()I", (void *) netty_unix_errors_errnoENOENT },
  { "errnoENOTCONN", "()I", (void *) netty_unix_errors_errnoENOTCONN },
  { "errnoEBADF", "()I", (void *) netty_unix_errors_errnoEBADF },
  { "errnoEPIPE", "()I", (void *) netty_unix_errors_errnoEPIPE },
  { "errnoECONNRESET", "()I", (void *) netty_unix_errors_errnoECONNRESET },
  { "errnoEAGAIN", "()I", (void *) netty_unix_errors_errnoEAGAIN },
  { "errnoEWOULDBLOCK", "()I", (void *) netty_unix_errors_errnoEWOULDBLOCK },
  { "errnoEINPROGRESS", "()I", (void *) netty_unix_errors_errnoEINPROGRESS },
  { "errorECONNREFUSED", "()I", (void *) netty_unix_errors_errorECONNREFUSED },
  { "errorEISCONN", "()I", (void *) netty_unix_errors_errorEISCONN },
  { "errorEALREADY", "()I", (void *) netty_unix_errors_errorEALREADY },
  { "errorENETUNREACH", "()I", (void *) netty_unix_errors_errorENETUNREACH },
  { "strError", "(I)Ljava/lang/String;", (void *) netty_unix_errors_strError }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Unix to reflect that.
jint netty_unix_errors_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    char* nettyClassName = NULL;
    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            ERRORS_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }

    NETTY_JNI_UTIL_LOAD_CLASS(env, oomErrorClass, "java/lang/OutOfMemoryError", error);

    NETTY_JNI_UTIL_LOAD_CLASS(env, runtimeExceptionClass, "java/lang/RuntimeException", error);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/ChannelException", nettyClassName, error);
    NETTY_JNI_UTIL_LOAD_CLASS(env, channelExceptionClass, nettyClassName, error);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_LOAD_CLASS(env, closedChannelExceptionClass, "java/nio/channels/ClosedChannelException", error);
    NETTY_JNI_UTIL_GET_METHOD(env, closedChannelExceptionClass, closedChannelExceptionMethodId, "<init>", "()V", error);

    NETTY_JNI_UTIL_LOAD_CLASS(env, ioExceptionClass, "java/io/IOException", error);

    NETTY_JNI_UTIL_LOAD_CLASS(env, portUnreachableExceptionClass, "java/net/PortUnreachableException", error);

    return NETTY_JNI_UTIL_JNI_VERSION;
error:
    free(nettyClassName);
    return JNI_ERR;
}

void netty_unix_errors_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    // delete global references so the GC can collect them
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, oomErrorClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, runtimeExceptionClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, channelExceptionClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, ioExceptionClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, portUnreachableExceptionClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, closedChannelExceptionClass);

    netty_jni_util_unregister_natives(env, packagePrefix, ERRORS_CLASSNAME);
}
