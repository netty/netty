/*
 * Copyright 2023 The Netty Project
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

#include <jni.h>

#include "netty_jni_util.h"

#include <winsock2.h>
#include <Ws2ipdef.h>
#include <ws2tcpip.h>
#include <iphlpapi.h>
#include <stdbool.h>

#pragma comment(lib, "IPHLPAPI.lib")
#pragma comment(lib, "ws2_32.lib")

#define NETTY_JNI_UTIL_JNI_VERSION JNI_VERSION_1_6

#define ADAPTER_INFO_CLASS "io/netty/resolver/dns/windows/WindowsAdapterInfo"

static jclass nativeExceptionClass = NULL;

static jclass networkAdapterClass = NULL;
static jmethodID networkAdapterCtor = NULL;

static jclass stringClass = NULL;

static jclass inetSocketAddressClass = NULL;
static jmethodID inetSocketAddressCtor = NULL;

static const char* cached_package_prefix = NULL;

int throwNativeException(JNIEnv* env, const char* fmt, ...) {
    va_list countArgs;
    va_start(countArgs, fmt);

    va_list printArgs;
    va_copy(printArgs, countArgs);
    
    size_t length = vsnprintf(NULL, 0, fmt, countArgs);
    size_t nbytes = length + 1;

    va_end(countArgs);

    char* buffer = malloc(nbytes);

    if (buffer == NULL) {
        va_end(printArgs);
        return JNI_ERR;
    }

    vsprintf_s(buffer, nbytes, fmt, printArgs);
    va_end(printArgs);

    jint result = (*env)->ThrowNew(env, nativeExceptionClass, buffer);

    free(buffer);

    return result;
}

jint read_adapter_addresses(JNIEnv* env, IP_ADAPTER_ADDRESSES** target) {
    const int MAX_TRIES = 3;

    // Allocate a 15 KB buffer to start with according to https://learn.microsoft.com/en-us/windows/win32/api/iphlpapi/nf-iphlpapi-getadaptersaddresses
    unsigned long outBufLen = 15000;

    IP_ADAPTER_ADDRESSES* pAddresses = NULL;
    unsigned long dwRetVal = 1;

    for (int tries = 0; tries < MAX_TRIES; ++tries) {
        pAddresses = malloc(outBufLen);

        if (pAddresses == NULL) {
            fprintf(stderr, "FATAL: Out of memory.\n");
            fflush(stderr);
            throwNativeException(env, "Out of memory for IP_ADAPTER_ADDRESSES buffer. Tried to allocate %lu bytes.", outBufLen);
            return JNI_ERR;
        }

        unsigned long flags = GAA_FLAG_SKIP_UNICAST | GAA_FLAG_SKIP_ANYCAST
            | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_FRIENDLY_NAME;

        // This call can update the outBufLen variable with the actual (required) size.
        dwRetVal = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, pAddresses, &outBufLen);

        if (dwRetVal != ERROR_BUFFER_OVERFLOW) {
            break;
        }

        free(pAddresses);
        pAddresses = NULL;
    }

    if (dwRetVal == NO_ERROR) {
        *target = pAddresses;
        return JNI_OK;
    }

    if (pAddresses != NULL) {
        free(pAddresses);
    }

    throwNativeException(env, "Could not read adapter information. Return code %d", dwRetVal);
    return JNI_ERR;
}

jobject sockAddrToJava(JNIEnv* env, LPSOCKADDR sockAddr) {
    unsigned short port;
    jstring host;
    
    switch (sockAddr->sa_family) {
    case AF_INET: {
        char ip[INET_ADDRSTRLEN];
        struct sockaddr_in* addr_in = (struct sockaddr_in*)sockAddr;

        if (inet_ntop(AF_INET, &addr_in->sin_addr, ip, sizeof(ip)) == NULL) {
            throwNativeException(env, "Could not convert Ipv4 to String");
            return NULL;
        }

        port = ntohs(addr_in->sin_port);
        host = (*env)->NewStringUTF(env, ip);
        break;
    }
    case AF_INET6: {
        char ip[INET6_ADDRSTRLEN];
        struct sockaddr_in6* addr_in = (struct sockaddr_in6*)sockAddr;
		
        if (inet_ntop(AF_INET6, &addr_in->sin6_addr, ip, sizeof(ip)) == NULL) {
            throwNativeException(env, "Could not convert Ipv6 to String");
            return NULL;
        }

        port = ntohs(addr_in->sin6_port);
        host = (*env)->NewStringUTF(env, ip);
        break;
    }
    default:
        throwNativeException(env, "Unknown address family %d", sockAddr->sa_family);
        return NULL;
    }

    return (*env)->NewObject(env, inetSocketAddressClass, inetSocketAddressCtor, host, port);
}

jobjectArray dnsServersToJava(JNIEnv* env, IP_ADAPTER_ADDRESSES* adapter) {
    jsize size = 0;
    for (IP_ADAPTER_DNS_SERVER_ADDRESS* dnsServer = adapter->FirstDnsServerAddress;
            dnsServer != NULL; dnsServer = dnsServer->Next) {
        ++size;
    }

    jobjectArray dnsServers = (*env)->NewObjectArray(env, size, inetSocketAddressClass, NULL);

    if (dnsServers == NULL) {
        throwNativeException(env, "Could not create dnsServers array");
        return NULL;
    }

    jsize index = 0;
    for (IP_ADAPTER_DNS_SERVER_ADDRESS* dnsServer = adapter->FirstDnsServerAddress;
        dnsServer != NULL; dnsServer = dnsServer->Next) {

        jobject serverAddress = sockAddrToJava(env, dnsServer->Address.lpSockaddr);

        if (serverAddress == NULL) {
            return NULL;
        }

        (*env)->SetObjectArrayElement(env, dnsServers, index, serverAddress);
        ++index;
    }

    return dnsServers;
}

jstring wideCharToString(JNIEnv* env, const wchar_t* input) {
    size_t bufferSize = (wcslen(input) + 1) * 2;

    char* buffer = malloc(bufferSize);

    if (buffer == NULL) {
        throwNativeException(env, "Could not allocate buffer for conversion from wchar to char.");
        return NULL;
    }

    size_t convertedSize;

    if (wcstombs_s(&convertedSize, buffer, bufferSize, input, bufferSize - 1)) {
        free(buffer);
        throwNativeException(env, "Could not convert from wchar to char.");
        return NULL;
    }

    jstring result = (*env)->NewStringUTF(env, buffer);
    free(buffer);

    return result;
}

jobjectArray searchDomainsToJava(JNIEnv* env, IP_ADAPTER_ADDRESSES* adapter) {
    jsize size = 0;

    if (adapter->DnsSuffix[0] != '\0') {
        size++;
    }

    for (PIP_ADAPTER_DNS_SUFFIX dnsSuffix = adapter->FirstDnsSuffix; dnsSuffix != NULL; dnsSuffix = dnsSuffix->Next) {
        if (dnsSuffix->String[0] != '\0') {
            size++;
        }
    }

    jobjectArray searchDomains = (*env)->NewObjectArray(env, size, stringClass, NULL);

    if (searchDomains == NULL) {
        throwNativeException(env, "Could not create searchDomains");
        return NULL;
    }

    int index = 0;
    if (adapter->DnsSuffix[0] != '\0') {
        jstring dnsSuffix = wideCharToString(env, adapter->DnsSuffix);

        if (dnsSuffix == NULL) {
            return NULL;
        }

        (*env)->SetObjectArrayElement(env, searchDomains, index, dnsSuffix);
        ++index;
    }

    for (PIP_ADAPTER_DNS_SUFFIX dnsSuffix = adapter->FirstDnsSuffix; dnsSuffix != NULL; dnsSuffix = dnsSuffix->Next) {
        if (dnsSuffix->String[0] != '\0') {
            jstring dnsText = wideCharToString(env, dnsSuffix->String);

            if (dnsText == NULL) {
                return NULL;
            }

            (*env)->SetObjectArrayElement(env, searchDomains, index, dnsText);
            ++index;
        }
    }

    return searchDomains;
}

jobject adapterToJava(JNIEnv* env, IP_ADAPTER_ADDRESSES* adapter) {
    jobjectArray searchDomains = searchDomainsToJava(env, adapter);

    if (searchDomains == NULL) {
        return NULL;
    }

    jobject dnsServers = dnsServersToJava(env, adapter);

    if (dnsServers == NULL) {
        return NULL;
    }

    return (*env)->NewObject(env, networkAdapterClass, networkAdapterCtor, dnsServers, searchDomains);
}

bool isRelevantAdapter(IP_ADAPTER_ADDRESSES* adapter) {
    return adapter->OperStatus == IfOperStatusUp && adapter->IfType != IF_TYPE_SOFTWARE_LOOPBACK;
}

static jobjectArray windows_adapters(JNIEnv* env, jclass clazz) {
    IP_ADAPTER_ADDRESSES* adapters;

    if (read_adapter_addresses(env, &adapters) != JNI_OK) {
        return NULL;
    }

    jsize adapterCount = 0;
    for (IP_ADAPTER_ADDRESSES* adapter = adapters; adapter != NULL; adapter = adapter->Next) {
        if (!isRelevantAdapter(adapter)) {
            continue;
        }

        adapterCount++;
    }

    jobjectArray javaAdapters = (*env)->NewObjectArray(env, adapterCount, networkAdapterClass, NULL);

    if (javaAdapters == NULL) {
        throwNativeException(env, "Could not create javaAdapters array");
        goto error;
    }

    jsize index = 0;
    for (IP_ADAPTER_ADDRESSES* adapter = adapters; adapter != NULL; adapter = adapter->Next) {
        if (!isRelevantAdapter(adapter)) {
            continue;
        }

        jobject result = adapterToJava(env, adapter);

        if (result == NULL) {
            goto error;
        }

        (*env)->SetObjectArrayElement(env, javaAdapters, index, result);

        ++index;
    }

    free(adapters);

    return javaAdapters;

error:
    free(adapters);
    return NULL;
}

jint loadClass(JNIEnv* env, const char* name, jclass* target) {
    jclass clazz;
    NETTY_JNI_UTIL_LOAD_CLASS(env, clazz, name, fail);

    *target = clazz;
    return JNI_OK;
fail:
    fprintf(stderr, "Class not found: %s\n", name);
    fflush(stderr);
    return JNI_ERR;
}

jint loadNettyClass(JNIEnv* env, const char* name, jclass* target, const char* packagePrefix) {
    char* nettyClassName = netty_jni_util_prepend(packagePrefix, name);
    jclass clazz;
    NETTY_JNI_UTIL_LOAD_CLASS(env, clazz, nettyClassName, fail);
    free(nettyClassName);
    *target = clazz;
    return JNI_OK;
fail:
    fprintf(stderr, "Class not found: %s\n", nettyClassName);
    fflush(stderr);
    free(nettyClassName);
    return JNI_ERR;
}

jint loadMethod(JNIEnv* env, jclass clazz, const char* name, const char* sig, jmethodID* target) {
    *target = (*env)->GetMethodID(env, clazz, name, sig);

    if (*target == NULL) {
        fprintf(stderr, "Method not found: %s(%s)\n", name, sig);
        fflush(stderr);
        return JNI_ERR;
    }

    return JNI_OK;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * 1);

    if (dynamicMethods == NULL) {
        return NULL;
    }

    char* dynamicTypeName = netty_jni_util_prepend(packagePrefix, "io/netty/resolver/dns/windows/NetworkAdapter;");
    char* dynamicArrayTypeName = netty_jni_util_prepend("()[L", dynamicTypeName);
    free(dynamicTypeName);

    JNINativeMethod* dynamicMethod = &dynamicMethods[0];
    dynamicMethod->name = "adapters";
    dynamicMethod->signature = dynamicArrayTypeName;
    dynamicMethod->fnPtr = (void *) windows_adapters;
    return dynamicMethods;
}

static jint register_natives(JNIEnv* env, const char* packagePrefix) {
    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        return JNI_ERR;
    }

    cached_package_prefix = packagePrefix;

    jint result = netty_jni_util_register_natives(env,
            packagePrefix,
            ADAPTER_INFO_CLASS,
            dynamicMethods, 1);

    netty_jni_util_free_dynamic_methods_table(dynamicMethods, 0, 1);

    return result == 0 ? JNI_OK : JNI_ERR;
}

static void unload_jvm_references(JNIEnv* env) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, nativeExceptionClass);

    free(networkAdapterCtor);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, networkAdapterClass);

    NETTY_JNI_UTIL_UNLOAD_CLASS(env, stringClass);

    free(inetSocketAddressCtor);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, inetSocketAddressClass);
}

static jint netty_resolver_dns_native_windows_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    if (loadClass(env, "java/lang/RuntimeException", &nativeExceptionClass) != JNI_OK) {
        goto fail;
    }

    if (loadNettyClass(env, "io/netty/resolver/dns/windows/NetworkAdapter", &networkAdapterClass, packagePrefix) != JNI_OK) {
        goto fail;
    }

    if (loadMethod(env, networkAdapterClass, "<init>", "([Ljava/net/InetSocketAddress;[Ljava/lang/String;)V", &networkAdapterCtor) != JNI_OK) {
        goto fail;
    }

    if (loadClass(env, "java/lang/String", &stringClass) != JNI_OK) {
        goto fail;
    }

    if (loadClass(env, "java/net/InetSocketAddress", &inetSocketAddressClass) != JNI_OK) {
        goto fail;
    }

    if (loadMethod(env, inetSocketAddressClass, "<init>", "(Ljava/lang/String;I)V", &inetSocketAddressCtor) != JNI_OK) {
        goto fail;
    }

    if (register_natives(env, packagePrefix) != JNI_OK) {
        goto fail;
    }

    return NETTY_JNI_UTIL_JNI_VERSION;
fail:
    unload_jvm_references(env);
    return JNI_ERR;
}

static void netty_resolver_dns_native_windows_JNI_OnUnLoad(JNIEnv* env) {
    unload_jvm_references(env);

    netty_jni_util_unregister_natives(env, cached_package_prefix, ADAPTER_INFO_CLASS);
}

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_resolver_dns_native_windows(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_resolver_dns_native_windows", netty_resolver_dns_native_windows_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_resolver_dns_native_windows(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_resolver_dns_native_windows_JNI_OnUnLoad);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_resolver_dns_native_windows", netty_resolver_dns_native_windows_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_resolver_dns_native_windows_JNI_OnUnLoad);
}
#endif /* NETTY_BUILD_STATIC */
