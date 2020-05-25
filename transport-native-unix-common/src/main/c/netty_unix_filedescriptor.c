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
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/uio.h>
#include <limits.h>

#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"

static jmethodID posId = NULL;
static jmethodID limitId = NULL;
static jfieldID posFieldId = NULL;
static jfieldID limitFieldId = NULL;

// Optional external methods
extern int pipe2(int pipefd[2], int flags) __attribute__((weak)) __attribute__((weak_import));

static jint _write(JNIEnv* env, jclass clazz, jint fd, void* buffer, jint pos, jint limit) {
    ssize_t res;
    int err;
    do {
       res = write(fd, buffer + pos, (size_t) (limit - pos));
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jlong _writev(JNIEnv* env, jclass clazz, jint fd, struct iovec* iov, jint length) {
    ssize_t res;
    int err;
    do {
        res = writev(fd, iov, length);
        // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jlong) res;
}

static jint _read(JNIEnv* env, jclass clazz, jint fd, void* buffer, jint pos, jint limit) {
    ssize_t res;
    int err;
    do {
        res = read(fd, buffer + pos, (size_t) (limit - pos));
        // Keep on reading if we was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

// JNI Registered Methods Begin
static jint netty_unix_filedescriptor_close(JNIEnv* env, jclass clazz, jint fd) {
   if (close(fd) < 0) {
       // There is really nothing "sane" we can do when EINTR was reported on close. So just ignore it and "assume"
       // everything is fine == we closed the file descriptor.
       //
       // For more details see:
       //     - https://bugs.chromium.org/p/chromium/issues/detail?id=269623
       //     - https://lwn.net/Articles/576478/
       if (errno != EINTR) {
           return -errno;
       }
   }
   return 0;
}

static jint netty_unix_filedescriptor_open(JNIEnv* env, jclass clazz, jstring path) {
    const char* f_path = (*env)->GetStringUTFChars(env, path, 0);

    int res = open(f_path, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    (*env)->ReleaseStringUTFChars(env, path, f_path);

    if (res < 0) {
        return -errno;
    }
    return res;
}

static jint netty_unix_filedescriptor_write(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _write(env, clazz, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit);
}

static jint netty_unix_filedescriptor_writeAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return _write(env, clazz, fd, (void*) (intptr_t) address, pos, limit);
}


static jlong netty_unix_filedescriptor_writevAddresses(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length) {
    struct iovec* iov = (struct iovec*) (intptr_t) memoryAddress;
    return _writev(env, clazz, fd, iov, length);
}

static jlong netty_unix_filedescriptor_writev(JNIEnv* env, jclass clazz, jint fd, jobjectArray buffers, const jint offset, jint length, jlong maxBytesToWrite) {
    struct iovec iov[length];
    struct iovec* iovptr = iov;
    int i;
    int num = offset + length;
    if (posFieldId != NULL && limitFieldId != NULL) {
        for (i = offset; i < num; ++i) {
            jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
            jint pos = (*env)->GetIntField(env, bufObj, posFieldId);
            jint limit = (*env)->GetIntField(env, bufObj, limitFieldId);
            size_t bytesLength = (size_t) (limit - pos);
            if (bytesLength > maxBytesToWrite && i != offset) {
              length = i - offset;
              break;
            }
            maxBytesToWrite -= bytesLength;
            void* buffer = (*env)->GetDirectBufferAddress(env, bufObj);
            // We check that GetDirectBufferAddress will not return NULL in OnLoad
            iovptr->iov_base = buffer + pos;
            iovptr->iov_len = bytesLength;
            ++iovptr;

            // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
            // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
            //
            // See https://github.com/netty/netty/issues/2623
            (*env)->DeleteLocalRef(env, bufObj);
        }
    } else if (posFieldId != NULL && limitFieldId == NULL) {
        for (i = offset; i < num; ++i) {
            jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
            jint pos = (*env)->GetIntField(env, bufObj, posFieldId);
            jint limit = (*env)->CallIntMethod(env, bufObj, limitId, NULL);
            size_t bytesLength = (size_t) (limit - pos);
            if (bytesLength > maxBytesToWrite && i != offset) {
              length = i - offset;
              break;
            }
            maxBytesToWrite -= bytesLength;
            void* buffer = (*env)->GetDirectBufferAddress(env, bufObj);
            // We check that GetDirectBufferAddress will not return NULL in OnLoad
            iovptr->iov_base = buffer + pos;
            iovptr->iov_len = bytesLength;
            ++iovptr;

            // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
            // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
            //
            // See https://github.com/netty/netty/issues/2623
            (*env)->DeleteLocalRef(env, bufObj);
        }
    } else if (limitFieldId != NULL) {
        for (i = offset; i < num; ++i) {
            jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
            jint pos = (*env)->CallIntMethod(env, bufObj, posId, NULL);
            jint limit = (*env)->GetIntField(env, bufObj, limitFieldId);
            size_t bytesLength = (size_t) (limit - pos);
            if (bytesLength > maxBytesToWrite && i != offset) {
              length = i - offset;
              break;
            }
            maxBytesToWrite -= bytesLength;
            void* buffer = (*env)->GetDirectBufferAddress(env, bufObj);
            // We check that GetDirectBufferAddress will not return NULL in OnLoad
            iovptr->iov_base = buffer + pos;
            iovptr->iov_len = bytesLength;
            ++iovptr;

            // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
            // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
            //
            // See https://github.com/netty/netty/issues/2623
            (*env)->DeleteLocalRef(env, bufObj);
        }
    } else {
        for (i = offset; i < num; ++i) {
            jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
            jint pos = (*env)->CallIntMethod(env, bufObj, posId, NULL);
            jint limit = (*env)->CallIntMethod(env, bufObj, limitId, NULL);
            size_t bytesLength = (size_t) (limit - pos);
            if (bytesLength > maxBytesToWrite && i != offset) {
              length = i - offset;
              break;
            }
            maxBytesToWrite -= bytesLength;
            void* buffer = (*env)->GetDirectBufferAddress(env, bufObj);
            // We check that GetDirectBufferAddress will not return NULL in OnLoad
            iovptr->iov_base = buffer + pos;
            iovptr->iov_len = bytesLength;
            ++iovptr;

            // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
            // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
            //
            // See https://github.com/netty/netty/issues/2623
            (*env)->DeleteLocalRef(env, bufObj);
        }
    }
    return _writev(env, clazz, fd, iov, length);
}

static jint netty_unix_filedescriptor_read(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _read(env, clazz, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit);
}

static jint netty_unix_filedescriptor_readAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return _read(env, clazz, fd, (void*) (intptr_t) address, pos, limit);
}

static jlong netty_unix_filedescriptor_newPipe(JNIEnv* env, jclass clazz) {
    int fd[2];
    if (pipe2) {
        // we can just use pipe2 and so save extra syscalls;
        if (pipe2(fd, O_NONBLOCK) != 0) {
            return -errno;
        }
    } else {
         if (pipe(fd) == 0) {
            if (fcntl(fd[0], F_SETFD, O_NONBLOCK) < 0) {
                int err = errno;
                close(fd[0]);
                close(fd[1]);
                return -err;
            }
            if (fcntl(fd[1], F_SETFD, O_NONBLOCK) < 0) {
                int err = errno;
                close(fd[0]);
                close(fd[1]);
                return -err;
            }
         } else {
            return -errno;
         }
    }

    // encode the fds into a 64 bit value
    return (((jlong) fd[0]) << 32) | fd[1];
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod method_table[] = {
  { "close", "(I)I", (void *) netty_unix_filedescriptor_close },
  { "open", "(Ljava/lang/String;)I", (void *) netty_unix_filedescriptor_open },
  { "write", "(ILjava/nio/ByteBuffer;II)I", (void *) netty_unix_filedescriptor_write },
  { "writeAddress", "(IJII)I", (void *) netty_unix_filedescriptor_writeAddress },
  { "writevAddresses", "(IJI)J", (void *) netty_unix_filedescriptor_writevAddresses },
  { "writev", "(I[Ljava/nio/ByteBuffer;IIJ)J", (void *) netty_unix_filedescriptor_writev },
  { "read", "(ILjava/nio/ByteBuffer;II)I", (void *) netty_unix_filedescriptor_read },
  { "readAddress", "(IJII)I", (void *) netty_unix_filedescriptor_readAddress },
  { "newPipe", "()J", (void *) netty_unix_filedescriptor_newPipe }
};
static const jint method_table_size = sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

jint netty_unix_filedescriptor_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    void* mem = NULL;
    if (netty_unix_util_register_natives(env, packagePrefix, "io/netty/channel/unix/FileDescriptor", method_table, method_table_size) != 0) {
        goto done;
    }
    if ((mem = malloc(1)) == NULL) {
        goto done;
    }
    jobject directBuffer = (*env)->NewDirectByteBuffer(env, mem, 1);
    if (directBuffer == NULL) {
        goto done;
    }
    if ((*env)->GetDirectBufferAddress(env, directBuffer) == NULL) {
        goto done;
    }
    jclass cls = (*env)->GetObjectClass(env, directBuffer);
    if (cls == NULL) {
        goto done;
    }
 
    // Get the method id for Buffer.position() and Buffer.limit(). These are used as fallback if
    // it is not possible to obtain the position and limit using the fields directly.
    NETTY_GET_METHOD(env, cls, posId, "position", "()I", done);
    NETTY_GET_METHOD(env, cls, limitId, "limit", "()I", done);

    // Try to get the ids of the position and limit fields. We later then check if we was able
    // to find them and if so use them get the position and limit of the buffer. This is
    // much faster then call back into java via (*env)->CallIntMethod(...).
    NETTY_TRY_GET_FIELD(env, cls, posFieldId, "position", "I");
    NETTY_TRY_GET_FIELD(env, cls, limitFieldId, "limit", "I");

    ret = NETTY_JNI_VERSION;
done:
    free(mem);
    return ret;
}

void netty_unix_filedescriptor_JNI_OnUnLoad(JNIEnv* env) { }
