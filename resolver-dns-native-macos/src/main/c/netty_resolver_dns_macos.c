/*
 * Copyright 2019 The Netty Project
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
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <stdlib.h>
#include "dnsinfo.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"
#include "netty_unix_socket.h"
#include "netty_unix_errors.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STREAM_CLASSNAME "io/netty/resolver/dns/macos/MacOSDnsServerAddressStreamProvider"

static jclass dnsResolverClass = NULL;
static jclass byteArrayClass = NULL;
static jclass stringClass = NULL;
static jmethodID dnsResolverMethodId = NULL;
static char* staticPackagePrefix = NULL;

// JNI Registered Methods Begin

// We use the same API as mDNSResponder and Chromium to retrieve the current nameserver configuration for the system:
// See:
//     https://src.chromium.org/viewvc/chrome?revision=218617&view=revision
//     https://opensource.apple.com/tarballs/mDNSResponder/
static jobjectArray netty_resolver_dns_macos_resolvers(JNIEnv* env, jclass clazz) {
    dns_config_t* config = dns_configuration_copy();
    if (config == NULL) {
        goto error;
    }
    jobjectArray array = (*env)->NewObjectArray(env, config->n_resolver, dnsResolverClass, NULL);
    if (array == NULL) {
        goto error;
    }

    for (int i = 0; i < config->n_resolver; i++) {
        dns_resolver_t* resolver = config->resolver[i];
        if (resolver == NULL) {
            goto error;
        }
        jstring domain = NULL;

        if (resolver->domain != NULL) {
            domain = (*env)->NewStringUTF(env, resolver->domain);
            if (domain == NULL) {
                goto error;
            }
        }

        jobjectArray addressArray = (*env)->NewObjectArray(env, resolver->n_nameserver, byteArrayClass, NULL);
        if (addressArray == NULL) {
            goto error;
        }

        for (int a = 0; a < resolver->n_nameserver; a++) {
            const struct sockaddr_storage* addr = (const struct sockaddr_storage *) resolver->nameserver[a];
            if (addr == NULL) {
                goto error;
            }
            jbyteArray address = netty_unix_socket_createInetSocketAddressArray(env, addr);
            if (address == NULL) {
                netty_unix_errors_throwOutOfMemoryError(env);
                goto error;
            }
            (*env)->SetObjectArrayElement(env, addressArray, a, address);
        }

        jint port = resolver->port;

        jobjectArray searchArray = (*env)->NewObjectArray(env, resolver->n_search, stringClass, NULL);
        if (searchArray == NULL) {
            goto error;
        }

        for (int a = 0; a < resolver->n_search; a++) {
            char* s = resolver->search[a];
            if (s == NULL) {
                goto error;
            }
            jstring search = (*env)->NewStringUTF(env, s);
            if (search == NULL) {
                goto error;
            }

            (*env)->SetObjectArrayElement(env, searchArray, a, search);
        }

        jstring options = NULL;
        if (resolver->options != NULL) {
            options = (*env)->NewStringUTF(env, resolver->options);
            if (options == NULL) {
                goto error;
            }
        }

        jint timeout = resolver->timeout;
        jint searchOrder = resolver->search_order;

        jobject java_resolver = (*env)->NewObject(env, dnsResolverClass, dnsResolverMethodId, domain,
                addressArray, port, searchArray, options, timeout, searchOrder);
        if (java_resolver == NULL) {
            goto error;
        }
        (*env)->SetObjectArrayElement(env, array, i, java_resolver);
    }

    dns_configuration_free(config);
    return array;
error:
    if (config != NULL) {
        dns_configuration_free(config);
    }
    return NULL;
}


// JNI Method Registration Table Begin

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * 1);

    char* dynamicTypeName = netty_jni_util_prepend(packagePrefix, "io/netty/resolver/dns/macos/DnsResolver;");
    JNINativeMethod* dynamicMethod = &dynamicMethods[0];
    dynamicMethod->name = "resolvers";
    dynamicMethod->signature = netty_jni_util_prepend("()[L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_resolver_dns_macos_resolvers;
    free(dynamicTypeName);
    return dynamicMethods;
}

// JNI Method Registration Table End

static void netty_resolver_dns_native_macos_JNI_OnUnLoad(JNIEnv* env) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, byteArrayClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, stringClass);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, STREAM_CLASSNAME);

    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }
}

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            MacOSDnsServerAddressStreamProvider to reflect that.
static jint netty_resolver_dns_native_macos_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int providerRegistered = 0;
    char* nettyClassName = NULL;

    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STREAM_CLASSNAME,
            dynamicMethods, 1) != 0) {
        goto done;
    }
    providerRegistered = 1;

    nettyClassName = netty_jni_util_prepend(packagePrefix, "io/netty/resolver/dns/macos/DnsResolver");
    NETTY_JNI_UTIL_LOAD_CLASS(env, dnsResolverClass, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_METHOD(env, dnsResolverClass, dnsResolverMethodId, "<init>", "(Ljava/lang/String;[[BI[Ljava/lang/String;Ljava/lang/String;II)V", done);

    NETTY_JNI_UTIL_LOAD_CLASS(env, byteArrayClass, "[B", done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, stringClass, "java/lang/String", done);

    if (packagePrefix != NULL) {
        staticPackagePrefix = strdup(packagePrefix);
    }

    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    if (ret == JNI_ERR) {
        if (providerRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STREAM_CLASSNAME);
        }
    }
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, 0, 1);
    free(nettyClassName);
    return ret;
}

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_resolver_dns_native_macos(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_resolver_dns_native_macos", netty_resolver_dns_native_macos_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_resolver_dns_native_macos(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_resolver_dns_native_macos_JNI_OnUnLoad);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_resolver_dns_native_macos", netty_resolver_dns_native_macos_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_resolver_dns_native_macos_JNI_OnUnLoad);
}
#endif /* NETTY_BUILD_STATIC */
