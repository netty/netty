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
#include <string.h>
#include "netty_unix_util.h"

char* netty_unix_util_prepend(const char* prefix, const char* str) {
    if (prefix == NULL) {
        char* result = (char*) malloc(sizeof(char) * (strlen(str) + 1));
        strcpy(result, str);
        return result;
    }
    char* result = (char*) malloc(sizeof(char) * (strlen(prefix) + strlen(str) + 1));
    strcpy(result, prefix);
    strcat(result, str);
    return result;
}

char* netty_unix_util_rstrstr(char* s1rbegin, const char* s1rend, const char* s2) {
    size_t s2len = strlen(s2);
    char *s = s1rbegin - s2len;

    for (; s >= s1rend; --s) {
        if (strncmp(s, s2, s2len) == 0) {
            return s;
        }
    }
    return NULL;
}

jint netty_unix_util_register_natives(JNIEnv* env, const char* packagePrefix, const char* className, const JNINativeMethod* methods, jint numMethods) {
    char* nettyClassName = netty_unix_util_prepend(packagePrefix, className);
    jclass nativeCls = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (nativeCls == NULL) {
        return JNI_ERR;
    }

    return (*env)->RegisterNatives(env, nativeCls, methods, numMethods);
}
