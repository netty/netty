/*
 * Copyright 2019 The Netty Project
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

static jclass dnsResolverClass = NULL;
static jclass byteArrayClass = NULL;
static jclass stringClass = NULL;
static jmethodID dnsResolverMethodId = NULL;

// JNI Registered Methods Begin

// We use the same API as mDNSResponder and Chromium to retrieve the current nameserver configuration for the system:
// See:
//     https://src.chromium.org/viewvc/chrome?revision=218617&view=revision
//     https://opensource.apple.com/tarballs/mDNSResponder/
static jobjectArray netty_resolver_dns_macos_resolvers(JNIEnv* env, jclass clazz) {
    dns_config_t* config = dns_configuration_copy();

    jobjectArray array = (*env)->NewObjectArray(env, config->n_resolver, dnsResolverClass, NULL);
    if (array == NULL) {
        goto error;
    }

    for (int i = 0; i < config->n_resolver; i++) {
        dns_resolver_t* resolver = config->resolver[i];
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
            jbyteArray address = netty_unix_socket_createInetSocketAddressArray(env, (const struct sockaddr_storage *) resolver->nameserver[a]);
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
            jstring search = (*env)->NewStringUTF(env, resolver->search[a]);
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
    dns_configuration_free(config);
    return NULL;
}


// JNI Method Registration Table Begin

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * 1);

    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/resolver/dns/macos/DnsResolver;");
    JNINativeMethod* dynamicMethod = &dynamicMethods[0];
    dynamicMethod->name = "resolvers";
    dynamicMethod->signature = netty_unix_util_prepend("()[L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_resolver_dns_macos_resolvers;
    free(dynamicTypeName);
    return dynamicMethods;
}

static void freeDynamicMethodsTable(JNINativeMethod* dynamicMethods) {
    free(dynamicMethods[0].signature);
    free(dynamicMethods);
}

// JNI Method Registration Table End


static void JNI_OnUnload_netty_resolver_dns_native_macos0(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, NETTY_JNI_VERSION) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    }

    if (byteArrayClass != NULL) {
        (*env)->DeleteGlobalRef(env, byteArrayClass);
        byteArrayClass = NULL;
    }

    if (stringClass != NULL) {
        (*env)->DeleteGlobalRef(env, stringClass);
        stringClass = NULL;
    }
}

static void netty_resolver_dns_native_macos0_OnUnLoad(JNIEnv* env) {

}

static jint JNI_OnLoad_netty_resolver_dns_native_macos0(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, NETTY_JNI_VERSION) != JNI_OK) {
        return JNI_ERR;
    }

#ifndef NETTY_BUILD_STATIC
    Dl_info dlinfo;
    jint status = 0;
    // We need to use an address of a function that is uniquely part of this library, so choose a static
    // function. See https://github.com/netty/netty/issues/4840.
    if (!dladdr((void*) netty_resolver_dns_native_macos0_OnUnLoad, &dlinfo)) {
        fprintf(stderr, "FATAL: resolver-dns-native-macos JNI call to dladdr failed!\n");
        return JNI_ERR;
    }
    char* packagePrefix = netty_unix_util_parse_package_prefix(dlinfo.dli_fname, "netty_resolver_dns_native_macos", &status);
    if (status == JNI_ERR) {
        fprintf(stderr, "FATAL: resolver-dns-native-macos JNI encountered unexpected dlinfo.dli_fname: %s\n", dlinfo.dli_fname);
        return JNI_ERR;
    }
#endif /* NETTY_BUILD_STATIC */

    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/resolver/dns/macos/MacOSDnsServerAddressStreamProvider",
            dynamicMethods, 1) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        fprintf(stderr, "FATAL: Couldnt register natives");

        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;


    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/resolver/dns/macos/DnsResolver");
    jclass localDnsResolverClass = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (localDnsResolverClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    dnsResolverClass = (jclass) (*env)->NewGlobalRef(env, localDnsResolverClass);
    if (dnsResolverClass == NULL) {
        return JNI_ERR;
    }
    dnsResolverMethodId = (*env)->GetMethodID(env, dnsResolverClass, "<init>", "(Ljava/lang/String;[[BI[Ljava/lang/String;Ljava/lang/String;II)V");
    if (dnsResolverMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: DnsResolver.<init>(String, byte[][], String[], String, int, int)");
        return JNI_ERR;
    }

    if (packagePrefix != NULL) {
        free(packagePrefix);
        packagePrefix = NULL;
    }

    jclass byteArrayCls = (*env)->FindClass(env, "[B");
    if (byteArrayCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    byteArrayClass = (jclass) (*env)->NewGlobalRef(env, byteArrayCls);
    if (byteArrayClass == NULL) {
        return JNI_ERR;
    }

    jclass stringCls = (*env)->FindClass(env, "java/lang/String");
    if (stringCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    stringClass = (jclass) (*env)->NewGlobalRef(env, stringCls);
    if (stringClass == NULL) {
        return JNI_ERR;
    }

    return NETTY_JNI_VERSION;
}

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// http://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_resolver_dns_native_macos(JavaVM* vm, void* reserved) {
    return JNI_OnLoad_netty_resolver_dns_native_macos0(vm, reserved);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_resolver_dns_native_macos(JavaVM* vm, void* reserved) {
    JNI_OnUnload_netty_resolver_dns_native_macos0(vm, reserved);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_OnLoad_netty_resolver_dns_native_macos0(vm, reserved);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    return JNI_OnUnload_netty_resolver_dns_native_macos0(vm, reserved);
}
#endif /* NETTY_BUILD_STATIC */
