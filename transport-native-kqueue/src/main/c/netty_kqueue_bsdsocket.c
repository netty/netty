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
#include <assert.h>
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

#define BSDSOCKET_CLASSNAME "io/netty/channel/kqueue/BsdSocket"

// Those are initialized in the init(...) method and cached for performance reasons
static jclass stringClass = NULL;
static jweak peerCredentialsClassWeak = NULL;
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

static jint netty_kqueue_bsdsocket_connectx(JNIEnv* env, jclass clazz,
        jint socketFd,
        jint socketInterface,
        jboolean sourceIPv6, jbyteArray sourceAddress, jint sourceScopeId, jint sourcePort,
        jboolean destinationIPv6, jbyteArray destinationAddress, jint destinationScopeId, jint destinationPort,
        jint flags,
        jlong iovAddress, jint iovCount, jint iovDataLength) {
#ifdef __APPLE__ // connectx(2) is only defined on Darwin.
    sa_endpoints_t endpoints;
    endpoints.sae_srcif = (unsigned int) socketInterface;
    endpoints.sae_srcaddr = NULL;
    endpoints.sae_srcaddrlen = 0;
    endpoints.sae_dstaddr = NULL;
    endpoints.sae_dstaddrlen = 0;

    struct sockaddr_storage srcaddr;
    socklen_t srcaddrlen;
    struct sockaddr_storage dstaddr;
    socklen_t dstaddrlen;

    if (NULL != sourceAddress) {
        if (-1 == netty_unix_socket_initSockaddr(env,
                sourceIPv6, sourceAddress, sourceScopeId, sourcePort, &srcaddr, &srcaddrlen)) {
            netty_unix_errors_throwIOException(env,
                "Source address specified, but could not be converted to sockaddr.");
            return -EINVAL;
        }
        endpoints.sae_srcaddr = (const struct sockaddr*) &srcaddr;
        endpoints.sae_srcaddrlen = srcaddrlen;
    }

    assert(destinationAddress != NULL); // Java side will ensure destination is never null.
    if (-1 == netty_unix_socket_initSockaddr(env,
            destinationIPv6, destinationAddress, destinationScopeId, destinationPort, &dstaddr, &dstaddrlen)) {
        netty_unix_errors_throwIOException(env, "Destination address could not be converted to sockaddr.");
        return -EINVAL;
    }
    endpoints.sae_dstaddr = (const struct sockaddr*) &dstaddr;
    endpoints.sae_dstaddrlen = dstaddrlen;

    int socket = (int) socketFd;
    const struct iovec* iov = (const struct iovec*) iovAddress;
    unsigned int iovcnt = (unsigned int) iovCount;
    size_t len = (size_t) iovDataLength;
    int result = connectx(socket, &endpoints, SAE_ASSOCID_ANY, flags, iov, iovcnt, &len, NULL);
    if (result == -1) {
        return -errno;
    }
    return (jint) len;
#else
    return -ENOSYS;
#endif
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
    jobjectArray resultArray = (*env)->NewObjectArray(env, 2, stringClass, NULL);
    if (resultArray == NULL) {
        return NULL;
    }
    jstring name = (*env)->NewStringUTF(env, &af.af_name[0]);
    if (name == NULL) {
        return NULL;
    }
    jstring arg = (*env)->NewStringUTF(env, &af.af_arg[0]);
    if (arg == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, resultArray, 0, name);
    (*env)->SetObjectArrayElement(env, resultArray, 1, arg);
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

static void netty_kqueue_bsdsocket_setTcpFastOpen(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_FASTOPEN, &optval, sizeof(optval));
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

static jint netty_kqueue_bsdsocket_isTcpFastOpen(JNIEnv* env, jclass clazz, jint fd) {
    int optval = 0;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_FASTOPEN, &optval, sizeof(optval)) == -1) {
        netty_unix_socket_getOptionHandleError(env, errno);
        return 0;
    }
    return optval;
}

static jobject netty_kqueue_bsdsocket_getPeerCredentials(JNIEnv *env, jclass clazz, jint fd) {
    struct xucred credentials;
    jclass peerCredentialsClass = NULL;

    // It has been observed on MacOS that this method can complete successfully but not set all fields of xucred.
    credentials.cr_ngroups = 0;
    if(netty_unix_socket_getOption(env,fd, SOL_SOCKET, LOCAL_PEERCRED, &credentials, sizeof (credentials)) == -1) {
        return NULL;
    }
    jintArray gids = NULL;
    if (credentials.cr_ngroups > 1) {
        if ((gids = (*env)->NewIntArray(env, credentials.cr_ngroups)) == NULL) {
            return NULL;
        }
        (*env)->SetIntArrayRegion(env, gids, 0, credentials.cr_ngroups, (jint*) credentials.cr_groups);
    } else {
        // It has been observed on MacOS that cr_ngroups may not be set, but the cr_gid field is set.
        if ((gids = (*env)->NewIntArray(env, 1)) == NULL) {
            return NULL;
        }
        (*env)->SetIntArrayRegion(env, gids, 0, 1, (jint*) &credentials.cr_gid);
    }

    pid_t pid = 0;
#ifdef LOCAL_PEERPID
    socklen_t len = sizeof(pid);
    // Getting the LOCAL_PEERPID is expected to return error in some cases (e.g. server socket FDs) - just return 0.
    if (netty_unix_socket_getOption0(fd, SOCK_STREAM, LOCAL_PEERPID, &pid, len) < 0) {
        pid = 0;
    }
#endif

    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, peerCredentialsClass, peerCredentialsClassWeak, error);

    jobject creds = (*env)->NewObject(env, peerCredentialsClass, peerCredentialsMethodId, pid, credentials.cr_uid, gids);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, peerCredentialsClass);
    return creds;
 error:
    return NULL;
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "setAcceptFilter", "(ILjava/lang/String;Ljava/lang/String;)V", (void *) netty_kqueue_bsdsocket_setAcceptFilter },
  { "setTcpNoPush", "(II)V", (void *) netty_kqueue_bsdsocket_setTcpNoPush },
  { "setSndLowAt", "(II)V", (void *) netty_kqueue_bsdsocket_setSndLowAt },
  { "setTcpFastOpen", "(II)V", (void *) netty_kqueue_bsdsocket_setTcpFastOpen },
  { "getAcceptFilter", "(I)[Ljava/lang/String;", (void *) netty_kqueue_bsdsocket_getAcceptFilter },
  { "getTcpNoPush", "(I)I", (void *) netty_kqueue_bsdsocket_getTcpNoPush },
  { "getSndLowAt", "(I)I", (void *) netty_kqueue_bsdsocket_getSndLowAt },
  { "isTcpFastOpen", "(I)I", (void *) netty_kqueue_bsdsocket_isTcpFastOpen },
  { "connectx", "(IIZ[BIIZ[BIIIJII)I", (void *) netty_kqueue_bsdsocket_connectx }
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    char* dynamicTypeName = NULL;
    size_t size = sizeof(JNINativeMethod) * dynamicMethodsTableSize();
    JNINativeMethod* dynamicMethods = malloc(size);
    if (dynamicMethods == NULL) {
        return NULL;
    }
    memset(dynamicMethods, 0, size);
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));

    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/DefaultFileRegion;JJJ)J", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IL", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "sendFile";
    dynamicMethod->fnPtr = (void *) netty_kqueue_bsdsocket_sendFile;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/PeerCredentials;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(I)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "getPeerCredentials";
    dynamicMethod->fnPtr = (void *) netty_kqueue_bsdsocket_getPeerCredentials;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    return dynamicMethods;
error:
    free(dynamicTypeName);
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    return NULL;
}

// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Native to reflect that.
jint netty_kqueue_bsdsocket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    char* nettyClassName = NULL;
    jclass fileRegionCls = NULL;
    jclass fileChannelCls = NULL;
    jclass fileDescriptorCls = NULL;
    jclass peerCredentialsClass = NULL;
    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            BSDSOCKET_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }

    // Initialize this module
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/DefaultFileRegion", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, fileRegionCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_FIELD(env, fileRegionCls, fileChannelFieldId, "file", "Ljava/nio/channels/FileChannel;", done);
    NETTY_JNI_UTIL_GET_FIELD(env, fileRegionCls, transferredFieldId, "transferred", "J", done);

    NETTY_JNI_UTIL_FIND_CLASS(env, fileChannelCls, "sun/nio/ch/FileChannelImpl", done);
    NETTY_JNI_UTIL_GET_FIELD(env, fileChannelCls, fileDescriptorFieldId, "fd", "Ljava/io/FileDescriptor;", done);

    NETTY_JNI_UTIL_FIND_CLASS(env, fileDescriptorCls, "java/io/FileDescriptor", done);
    NETTY_JNI_UTIL_GET_FIELD(env, fileDescriptorCls, fdFieldId, "fd", "I", done);
  
    NETTY_JNI_UTIL_LOAD_CLASS(env, stringClass, "java/lang/String", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/PeerCredentials", nettyClassName, done);

    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, peerCredentialsClassWeak, nettyClassName, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, peerCredentialsClass, peerCredentialsClassWeak, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);
  
    NETTY_JNI_UTIL_GET_METHOD(env, peerCredentialsClass, peerCredentialsMethodId, "<init>", "(II[I)V", done);
    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(nettyClassName);

    NETTY_JNI_UTIL_DELETE_LOCAL(env, peerCredentialsClass);

    return ret;
}

void netty_kqueue_bsdsocket_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, peerCredentialsClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, stringClass);

    netty_jni_util_unregister_natives(env, packagePrefix, BSDSOCKET_CLASSNAME);
}
