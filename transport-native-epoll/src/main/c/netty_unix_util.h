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

#ifndef NETTY_UNIX_UTIL_H_
#define NETTY_UNIX_UTIL_H_

#include <jni.h>

/**
 * Return a new string (caller must free this string) which is equivalent to <pre>prefix + str</pre>.
 *
 * Caller must free the return value!
 */
char* netty_unix_util_prepend(const char* prefix, const char* str);

char* netty_unix_util_rstrstr(char* s1rbegin, const char* s1rend, const char* s2);

/**
 * Return type is as defined in http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html#wp5833.
 */
jint netty_unix_util_register_natives(JNIEnv* env, const char* packagePrefix, const char* className, const JNINativeMethod* methods, jint numMethods);

#endif /* NETTY_UNIX_UTIL_H_ */
