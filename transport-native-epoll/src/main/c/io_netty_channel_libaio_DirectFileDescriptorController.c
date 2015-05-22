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


#ifndef _GNU_SOURCE
// libaio, O_DIRECT and other things won't be available without this define
#define _GNU_SOURCE
#endif

//#define DEBUG

#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <libaio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <pthread.h>
#include "io_netty_channel_libaio_DirectFileDescriptorController.h"
#include "exception_helper.h"

struct io_control {
    io_context_t ioContext;
    struct io_event * events;

    // This is used to make sure we don't return IOCB while something else is using them
    // this is to guarantee the submits could be done concurrently with polling
    pthread_mutex_t iocbLock;

    // a resuable pool of iocb
    struct iocb ** iocb;
    int queueSize;
    int iocbPut;
    int iocbGet;
    int used;
};

jclass errorInfoClass = NULL;
jmethodID errorInfoConstr = NULL;

/**
  This is being invoked by Native.c /  JNI_OnLoad..

  There is only one point of entrance of Loading the library,
  this will be a hook so extra stuff that needs to be loaded here.
*/
void directFile_JNI_OnLoad(JNIEnv* env) {
    errorInfoClass = (*env)->FindClass(env, "io/netty/channel/libaio/ErrorInfo");
    if (errorInfoClass == NULL) {
       return;
    }

    // The ErrorInfoClass is barely used. The VM would crash in the event of an error without this GlobalRef
    // and if the class was unloaded from GC
    errorInfoClass = (jclass)(*env)->NewGlobalRef(env, (jobject)errorInfoClass);

    errorInfoConstr = (*env)->GetMethodID(env, errorInfoClass, "<init>", "(Ljava/lang/Object;ILjava/lang/String;)V");

    if (errorInfoConstr == NULL) {
       return;
    }

    /// The ErrorInfoClass is barely used. The VM would crash in the event of an error without this GlobalRef
    errorInfoConstr = (jmethodID)(*env)->NewGlobalRef(env, (jobject)(errorInfoConstr));
}

void directFile_JNI_OnUnLoad(JNIEnv* env) {
    // Deleting global refs so their classes can be GCed
    if (errorInfoConstr != NULL) {
        (*env)->DeleteGlobalRef(env, (jobject)errorInfoConstr);
    }

    if (errorInfoClass != NULL) {
        (*env)->DeleteGlobalRef(env, (jobject)errorInfoClass);
    }
}

static inline struct io_control * getIOControl(JNIEnv* env, jobject pointer) {
    struct io_control * ioControl = (struct io_control *) (*env)->GetDirectBufferAddress(env, pointer);
    return ioControl;
}

/**
 remove an iocb from the pool of IOCBs. Returns null if full
*/
static inline struct iocb * getIOCB(struct io_control * control) {
    struct iocb * iocb = 0;

    pthread_mutex_lock(&(control->iocbLock));

    #ifdef DEBUG
       fprintf (stderr, "getIOCB::used=%d, queueSize=%d, get=%d, put=%d\n", control->used, control->queueSize, control->iocbGet, control->iocbPut);
    #endif

    if (control->used < control->queueSize) {
        control->used++;
        iocb = control->iocb[control->iocbGet++];

        if (control->iocbGet >= control->queueSize) {
           control->iocbGet = 0;
        }
    } else {
        #ifdef DEBUG
            fprintf (stderr, "Could not find iocb\n");
        #endif
    }

    pthread_mutex_unlock(&(control->iocbLock));
    return iocb;
}

/**
  Put an iocb back on the pool of IOCBs
*/
static inline void putIOCB(struct io_control * control, struct iocb * iocbBack) {
    pthread_mutex_lock(&(control->iocbLock));

    #ifdef DEBUG
       fprintf (stderr, "putIOCB::used=%d, queueSize=%d, get=%d, put=%d\n", control->used, control->queueSize, control->iocbGet, control->iocbPut);
    #endif

    control->used--;
    control->iocb[control->iocbPut++] = iocbBack;
    if (control->iocbPut >= control->queueSize) {
       control->iocbPut = 0;
    }
    pthread_mutex_unlock(&(control->iocbLock));
}

static inline void * getBuffer(JNIEnv* env, jobject pointer) {
    return (*env)->GetDirectBufferAddress(env, pointer);
}

/**
  Everything that is allocated here will be freed at deleteContext when the class is unloaded.
*/
JNIEXPORT jobject JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_newContext(JNIEnv* env, jclass clazz, jint queueSize) {
    io_context_t libaioContext;
    int i = 0;

    int res = io_queue_init(queueSize, &libaioContext);
    if (res) {
        // Error, so need to release whatever was done before
        free(libaioContext);

        throwRuntimeException(env, exceptionMessage("Cannot initialize queue:", -res));
        return NULL;
    }

    struct iocb ** iocb = (struct iocb **)malloc(sizeof(struct iocb *) * queueSize);

    for (i = 0; i < queueSize; i++) {
       iocb[i] = (struct iocb *)malloc(sizeof(struct iocb));
    }

    struct io_event * events = (struct io_event *)malloc(sizeof(struct io_event) * queueSize);

    struct io_control * theControl = (struct io_control *) malloc(sizeof(struct io_control));

    theControl->ioContext = libaioContext;
    theControl->events = events;
    theControl->iocb = iocb;
    theControl->queueSize = queueSize;
    theControl->iocbPut = 0;
    theControl->iocbGet = 0;
    theControl->used = 0;
    pthread_mutex_init(&(theControl->iocbLock), 0);

    return (*env)->NewDirectByteBuffer(env, theControl, sizeof(struct io_control));
}

JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_deleteContext(JNIEnv* env, jclass clazz, jobject contextPointer) {
    int i;
    struct io_control * theControl = getIOControl(env, contextPointer);
    io_queue_release(theControl->ioContext);

    // Releasing each individual iocb
    for (i = 0; i < theControl->queueSize; i++) {
       free(theControl->iocb[i]);
    }

    free(theControl->iocb);

    free(theControl->events);

    free(theControl);
}

JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_close(JNIEnv* env, jclass clazz, jint fd) {
   if (close(fd) < 0) {
       throwIOException(env, exceptionMessage("Error closing file:", errno));
   }
}

JNIEXPORT int JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_open(JNIEnv* env, jclass clazz, jstring path) {
    const char* f_path = (*env)->GetStringUTFChars(env, path, 0);

    int res = open(f_path, O_RDWR | O_CREAT | O_DIRECT, 0666);

    (*env)->ReleaseStringUTFChars(env, path, f_path);

    if (res < 0) {
       throwIOException(env, exceptionMessage("Cannot open file:", errno));
    }

    return res;
}

static inline void submit(JNIEnv * env, io_context_t ioContext,  struct iocb * iocb) {
    int result = io_submit(ioContext, 1, &iocb);

    if (result < 0) {
        if (result == -EAGAIN) {
            throwIOException(env, "Not enough space on libaio queue");
        }
        else {
            throwIOException(env, exceptionMessage("Error while submitting IO:", -result));
        }
    }
}

JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_submitWrite
  (JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferWrite, jobject callback) {
    struct io_control * theControl = getIOControl(env, contextPointer);

    struct iocb * iocb = getIOCB(theControl);

    if (iocb == NULL) {
       throwIOException(env, "Not enough space on the queue for submitting");
       return;
    }

    io_prep_pwrite(iocb, fileHandle, getBuffer(env, bufferWrite), size, position);

    // The GlobalRef will be deleted when poll is called. this is done so
    // the vm wouldn't crash if the Callback passed by the user is GCed between submission
    // and callback.
    // also as the real intention is to hold the reference until the life cycle is complete
    iocb->data = (void *) (*env)->NewGlobalRef(env, callback);

    submit(env, theControl->ioContext, iocb);
}

JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_submitRead
  (JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferRead, jobject callback) {
    struct io_control * theControl = getIOControl(env, contextPointer);

    struct iocb * iocb = getIOCB(theControl);

    if (iocb == NULL) {
       throwIOException(env, "Not enough space on the queue for submitting");
       return;
    }

    io_prep_pread(iocb, fileHandle, getBuffer(env, bufferRead), size, position);

    // The GlobalRef will be deleted when poll is called. this is done so
    // the vm wouldn't crash if the Callback passed by the user is GCed between submission
    // and callback.
    // also as the real intention is to hold the reference until the life cycle is complete
    iocb->data = (void *) (*env)->NewGlobalRef(env, callback);

    submit(env, theControl->ioContext, iocb);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_poll
  (JNIEnv * env, jobject obj, jobject contextPointer, jobjectArray callbacks, jint min, jint max) {
    int i = 0;
    struct io_control * theControl = getIOControl(env, contextPointer);

    int result = io_getevents(theControl->ioContext, min, max, theControl->events, 0);

    for (i = 0; i < result; i++) {
        struct iocb * iocbp = (struct iocb *)theControl->events[i].obj;

        int eventResult = theControl->events[i].res;

        #ifdef DEBUG
            fprintf (stderr, "Poll res: %d totalRes=%d\n", eventResult, result);
        #endif

        if (eventResult < 0) {
            #ifdef DEBUG
                fprintf (stderr, "Error: %s\n", strerror(-eventResult));
            #endif

            jstring jstrError = (*env)->NewStringUTF(env, strerror(-eventResult));

            jobject errorObject = (*env)->NewObject(env,
                                                   errorInfoClass,
                                                   errorInfoConstr,
                                                   (jobject)(iocbp->data),
                                                   (jint)(-eventResult),
                                                   jstrError);

            if (errorObject == NULL) {
                return -1;
            }

            (*env)->SetObjectArrayElement(env, callbacks, i, errorObject);
        } else {
            (*env)->SetObjectArrayElement(env, callbacks, i, (jobject)iocbp->data);
        }

        if (iocbp->data != NULL) {
            // We delete the globalRef after the completion of the callback
            (*env)->DeleteGlobalRef(env, (jobject)iocbp->data);
        }

        putIOCB(theControl, iocbp);
    }
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_newAlignedBuffer
(JNIEnv * env, jclass clazz, jint size, jint alignment) {
    if (size % alignment != 0) {
        throwRuntimeException(env, "Buffer size needs to be aligned to passed argument");
        return NULL;
    }

    // This will allocate a buffer, aligned by alignment.
    // Buffers created here need to be manually destroyed by destroyBuffer, or this would leak on the process heap away of Java's GC managed memory
    // NOTE: this buffer will contain non initialized data, you must fill it up properly
    void * buffer;
    int result = posix_memalign(&buffer, alignment, size);

    if (result) {
        throwRuntimeException(env, exceptionMessage("Can't allocate posix buffer:", result));
        return NULL;
    }

    return (*env)->NewDirectByteBuffer(env, buffer, size);
}

JNIEXPORT void JNICALL Java_io_netty_channel_libaio_DirectFileDescriptorController_freeBuffer
  (JNIEnv * env, jclass clazz, jobject jbuffer) {
  	void *  buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
  	free(buffer);
}
