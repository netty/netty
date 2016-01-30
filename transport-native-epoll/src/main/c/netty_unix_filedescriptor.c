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

#include "netty_unix_util.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"

static jmethodID posId = NULL;
static jmethodID limitId = NULL;
static jfieldID posFieldId = NULL;
static jfieldID limitFieldId = NULL;

// Optional external methods
extern int pipe2(int pipefd[2], int flags) __attribute__((weak));

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
       return -errno;
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

static jlong netty_unix_filedescriptor_writev(JNIEnv* env, jclass clazz, jint fd, jobjectArray buffers, jint offset, jint length) {
    struct iovec iov[length];
    int iovidx = 0;
    int i;
    int num = offset + length;
    for (i = offset; i < num; i++) {
        jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
        jint pos;
        // Get the current position using the (*env)->GetIntField if possible and fallback
        // to slower (*env)->CallIntMethod(...) if needed
        if (posFieldId == NULL) {
            pos = (*env)->CallIntMethod(env, bufObj, posId, NULL);
        } else {
            pos = (*env)->GetIntField(env, bufObj, posFieldId);
        }
        jint limit;
        // Get the current limit using the (*env)->GetIntField if possible and fallback
        // to slower (*env)->CallIntMethod(...) if needed
        if (limitFieldId == NULL) {
            limit = (*env)->CallIntMethod(env, bufObj, limitId, NULL);
        } else {
            limit = (*env)->GetIntField(env, bufObj, limitFieldId);
        }
        void* buffer = (*env)->GetDirectBufferAddress(env, bufObj);
        // We check that GetDirectBufferAddress will not return NULL in OnLoad
        iov[iovidx].iov_base = buffer + pos;
        iov[iovidx].iov_len = (size_t) (limit - pos);
        iovidx++;

        // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
        // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
        //
        // See https://github.com/netty/netty/issues/2623
        (*env)->DeleteLocalRef(env, bufObj);
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
  { "writev", "(I[Ljava/nio/ByteBuffer;II)J", (void *) netty_unix_filedescriptor_writev },
  { "read", "(ILjava/nio/ByteBuffer;II)I", (void *) netty_unix_filedescriptor_read },
  { "readAddress", "(IJII)I", (void *) netty_unix_filedescriptor_readAddress },
  { "newPipe", "()J", (void *) netty_unix_filedescriptor_newPipe }
};
static const jint method_table_size = sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

jint netty_unix_filedescriptor_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    if (netty_unix_util_register_natives(env, packagePrefix, "io/netty/channel/unix/FileDescriptor", method_table, method_table_size) != 0) {
        return JNI_ERR;
    }
    void* mem = malloc(1);
    if (mem == NULL) {
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    jobject directBuffer = (*env)->NewDirectByteBuffer(env, mem, 1);
    if (directBuffer == NULL) {
        free(mem);

        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    if ((*env)->GetDirectBufferAddress(env, directBuffer) == NULL) {
        free(mem);

        netty_unix_errors_throwRuntimeException(env, "failed to get direct buffer address");
        return JNI_ERR;
    }

    jclass cls = (*env)->GetObjectClass(env, directBuffer);

    // Get the method id for Buffer.position() and Buffer.limit(). These are used as fallback if
    // it is not possible to obtain the position and limit using the fields directly.
    posId = (*env)->GetMethodID(env, cls, "position", "()I");
    if (posId == NULL) {
        free(mem);

        // position method was not found.. something is wrong so bail out
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: ByteBuffer.position()");
        return JNI_ERR;
    }

    limitId = (*env)->GetMethodID(env, cls, "limit", "()I");
    if (limitId == NULL) {
        free(mem);

        // limit method was not found.. something is wrong so bail out
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: ByteBuffer.limit()");
        return JNI_ERR;
    }
    // Try to get the ids of the position and limit fields. We later then check if we was able
    // to find them and if so use them get the position and limit of the buffer. This is
    // much faster then call back into java via (*env)->CallIntMethod(...).
    posFieldId = (*env)->GetFieldID(env, cls, "position", "I");
    if (posFieldId == NULL) {
        // this is ok as we can still use the method so just clear the exception
        (*env)->ExceptionClear(env);
    }
    limitFieldId = (*env)->GetFieldID(env, cls, "limit", "I");
    if (limitFieldId == NULL) {
        // this is ok as we can still use the method so just clear the exception
        (*env)->ExceptionClear(env);
    }

    free(mem);
    return JNI_VERSION_1_6;
}

void netty_unix_filedescriptor_JNI_OnUnLoad(JNIEnv* env) { }
