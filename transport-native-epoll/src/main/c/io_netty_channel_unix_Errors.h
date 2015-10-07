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
#ifndef IO_NETTY_ERROR_UNIX_H_
#define IO_NETTY_ERROR_UNIX_H_

#include <jni.h>

// Exported JNI methods
jint Java_io_netty_channel_unix_Errors_errnoENOTCONN(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoEBADF(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoEPIPE(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoECONNRESET(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoEAGAIN(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoEWOULDBLOCK(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Errors_errnoEINPROGRESS(JNIEnv* env, jclass clazz);
jstring Java_io_netty_channel_unix_Errors_strError(JNIEnv* env, jclass clazz, jint error);

#endif /* IO_NETTY_ERROR_UNIX_H_ */
