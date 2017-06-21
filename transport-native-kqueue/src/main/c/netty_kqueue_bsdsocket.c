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
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/malloc.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <sys/ucred.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include "netty_kqueue_bsdsocket.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"

// Those are initialized in the init(...) method and cached for performance reasons
static jclass stringCls = NULL;
static jclass peerCredentialsClass = NULL;
static jfieldID fileChannelFieldId = NULL;
static jfieldID transferredFieldId = NULL;
static jfieldID fdFieldId = NULL;
static jfieldID fileDescriptorFieldId = NULL;
static jmethodID peerCredentialsMethodId = NULL;

// JNI Registered Methods Begin
static jlong netty_kqueue_bsdsocket_sendFile(JNIEnv* env, jclass clazz, jint socketFd, jobject fileRegion, jlong base_off, jlong off, jlong len) {
    jobject fileChannel = (*env)->GetObjectField(env, fileRegion, fileChannelFieldId);
    if (fileChannel == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get DefaultFileRegion.file");
        return -1;
    }
    jobject fileDescriptor = (*env)->GetObjectField(env, fileChannel, fileDescriptorFieldId);
    if (fileDescriptor == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get FileChannelImpl.fd");
        return -1;
    }
    jint srcFd = (*env)->GetIntField(env, fileDescriptor, fdFieldId);
    if (srcFd == -1) {
        netty_unix_errors_throwRuntimeException(env, "failed to get FileDescriptor.fd");
        return -1;
    }
    const jlong lenBefore = len;
    off_t sbytes;
    int res, err;
    do {
#ifdef __APPLE__
      // sbytes is an input (how many to write) and output (how many were written) parameter.
      sbytes = len;
      res = sendfile(srcFd, socketFd, base_off + off, &sbytes, NULL, 0);
#else
      sbytes = 0;
      res = sendfile(srcFd, socketFd, base_off + off, len, NULL, &sbytes, 0);
#endif
      len -= sbytes;
    } while (res < 0 && ((err = errno) == EINTR));
    sbytes = lenBefore - len;
    if (sbytes > 0) {
        // update the transferred field in DefaultFileRegion
        (*env)->SetLongField(env, fileRegion, transferredFieldId, off + sbytes);
        return sbytes;
    }
    return res < 0 ? -err : 0;
}

static void netty_kqueue_bsdsocket_setAcceptFilter(JNIEnv* env, jclass clazz, jint fd, jstring afName, jstring afArg) {
#ifdef SO_ACCEPTFILTER
    struct accept_filter_arg af;
    const char* tmpString = NULL;
    af.af_name[0] = af.af_arg[0] ='\0';

    tmpString = (*env)->GetStringUTFChars(env, afName, NULL);
    strncat(af.af_name, tmpString, sizeof(af.af_name) / sizeof(af.af_name[0]));
    (*env)->ReleaseStringUTFChars(env, afName, tmpString);

    tmpString = (*env)->GetStringUTFChars(env, afArg, NULL);
    strncat(af.af_arg, tmpString, sizeof(af.af_arg) / sizeof(af.af_arg[0]));
    (*env)->ReleaseStringUTFChars(env, afArg, tmpString);

    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_ACCEPTFILTER, &af, sizeof(af));
#else // No know replacement on MacOS
    netty_unix_errors_throwChannelExceptionErrorNo(env, "setsockopt() failed: ", EINVAL);
#endif
}

static jobjectArray netty_kqueue_bsdsocket_getAcceptFilter(JNIEnv* env, jclass clazz, jint fd) {
#ifdef SO_ACCEPTFILTER
    struct accept_filter_arg af;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_ACCEPTFILTER, &af, sizeof(af)) == -1) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "getsockopt() failed: ", errno);
        return NULL;
    }
    jobjectArray resultArray = (*env)->NewObjectArray(env, 2, stringCls, NULL);
    (*env)->SetObjectArrayElement(env, resultArray, 0, (*env)->NewStringUTF(env, &af.af_name[0]));
    (*env)->SetObjectArrayElement(env, resultArray, 1, (*env)->NewStringUTF(env, &af.af_arg[0]));
    return resultArray;
#else // No know replacement on MacOS
    // Don't throw here because this is used when getting a list of all options.
    return NULL;
#endif
}

static void netty_kqueue_bsdsocket_setTcpNoPush(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_NOPUSH, &optval, sizeof(optval));
}

static void netty_kqueue_bsdsocket_setSndLowAt(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_SNDLOWAT, &optval, sizeof(optval));
}

static jint netty_kqueue_bsdsocket_getTcpNoPush(JNIEnv* env, jclass clazz, jint fd) {
  int optval;
  if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_NOPUSH, &optval, sizeof(optval)) == -1) {
    return -1;
  }
  return optval;
}

static jint netty_kqueue_bsdsocket_getSndLowAt(JNIEnv* env, jclass clazz, jint fd) {
  int optval;
  if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_SNDLOWAT, &optval, sizeof(optval)) == -1) {
    return -1;
  }
  return optval;
}

static jobject netty_kqueue_bsdsocket_getPeerCredentials(JNIEnv *env, jclass clazz, jint fd) {
    struct xucred credentials;
    // It has been observed on MacOS that this method can complete successfully but not set all fields of xucred.
    credentials.cr_ngroups = 0;
    if(netty_unix_socket_getOption(env,fd, SOL_SOCKET, LOCAL_PEERCRED, &credentials, sizeof (credentials)) == -1) {
        return NULL;
    }
    jintArray gids = NULL;
    if (credentials.cr_ngroups > 1) {
        gids = (*env)->NewIntArray(env, credentials.cr_ngroups);
        (*env)->SetIntArrayRegion(env, gids, 0, credentials.cr_ngroups, (jint*) credentials.cr_groups);
    } else {
        // It has been observed on MacOS that cr_ngroups may not be set, but the cr_gid field is set.
        gids = (*env)->NewIntArray(env, 1);
        (*env)->SetIntArrayRegion(env, gids, 0, 1, (jint*) &credentials.cr_gid);
    }

    // TODO: getting the PID may require reading/sending "ancillary data" via SCM_CREDENTIALS which is not desirable.
    return (*env)->NewObject(env, peerCredentialsClass, peerCredentialsMethodId, 0, credentials.cr_uid, gids);
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "setAcceptFilter", "(ILjava/lang/String;Ljava/lang/String;)V", (void *) netty_kqueue_bsdsocket_setAcceptFilter },
  { "setTcpNoPush", "(II)V", (void *) netty_kqueue_bsdsocket_setTcpNoPush },
  { "setSndLowAt", "(II)V", (void *) netty_kqueue_bsdsocket_setSndLowAt },
  { "getAcceptFilter", "(I)[Ljava/lang/String;", (void *) netty_kqueue_bsdsocket_getAcceptFilter },
  { "getTcpNoPush", "(I)I", (void *) netty_kqueue_bsdsocket_getTcpNoPush },
  { "getSndLowAt", "(I)I", (void *) netty_kqueue_bsdsocket_getSndLowAt }
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * dynamicMethodsTableSize());
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/DefaultFileRegion;JJJ)J");
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    dynamicMethod->name = "sendFile";
    dynamicMethod->signature = netty_unix_util_prepend("(IL", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_kqueue_bsdsocket_sendFile;
    free(dynamicTypeName);

    ++dynamicMethod;
    dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/PeerCredentials;");
    dynamicMethod->name = "getPeerCredentials";
    dynamicMethod->signature = netty_unix_util_prepend("(I)L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_kqueue_bsdsocket_getPeerCredentials;
    free(dynamicTypeName);

    return dynamicMethods;
}

static void freeDynamicMethodsTable(JNINativeMethod* dynamicMethods) {
    jint fullMethodTableSize = dynamicMethodsTableSize();
    jint i = fixed_method_table_size;
    for (; i < fullMethodTableSize; ++i) {
        free(dynamicMethods[i].signature);
    }
    free(dynamicMethods);
}
// JNI Method Registration Table End

jint netty_kqueue_bsdsocket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/kqueue/BsdSocket",
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;

    // Initialize this module
    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/DefaultFileRegion");
    jclass fileRegionCls = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (fileRegionCls == NULL) {
        return JNI_ERR;
    }
    fileChannelFieldId = (*env)->GetFieldID(env, fileRegionCls, "file", "Ljava/nio/channels/FileChannel;");
    if (fileChannelFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: DefaultFileRegion.file");
        return JNI_ERR;
    }
    transferredFieldId = (*env)->GetFieldID(env, fileRegionCls, "transferred", "J");
    if (transferredFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: DefaultFileRegion.transferred");
        return JNI_ERR;
    }

    jclass fileChannelCls = (*env)->FindClass(env, "sun/nio/ch/FileChannelImpl");
    if (fileChannelCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    fileDescriptorFieldId = (*env)->GetFieldID(env, fileChannelCls, "fd", "Ljava/io/FileDescriptor;");
    if (fileDescriptorFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: FileChannelImpl.fd");
        return JNI_ERR;
    }

    jclass fileDescriptorCls = (*env)->FindClass(env, "java/io/FileDescriptor");
    if (fileDescriptorCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    fdFieldId = (*env)->GetFieldID(env, fileDescriptorCls, "fd", "I");
    if (fdFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: FileDescriptor.fd");
        return JNI_ERR;
    }
    stringCls = (*env)->FindClass(env, "java/lang/String");
    if (stringCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }

    nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/PeerCredentials");
    jclass localPeerCredsClass = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (localPeerCredsClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    peerCredentialsClass = (jclass) (*env)->NewGlobalRef(env, localPeerCredsClass);
    if (peerCredentialsClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    peerCredentialsMethodId = (*env)->GetMethodID(env, peerCredentialsClass, "<init>", "(II[I)V");
    if (peerCredentialsMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: PeerCredentials.<init>(int, int, int[])");
        return JNI_ERR;
    }

    return NETTY_JNI_VERSION;
}

void netty_kqueue_bsdsocket_JNI_OnUnLoad(JNIEnv* env) {
    if (peerCredentialsClass != NULL) {
        (*env)->DeleteGlobalRef(env, peerCredentialsClass);
        peerCredentialsClass = NULL;
    }
}
