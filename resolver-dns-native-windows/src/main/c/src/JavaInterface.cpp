#include "JavaInterface.h"
#include "NetworkAdapters.h"
#include "NativeExceptionClass.h"
#include <memory>
#include "JavaNetworkAdapterClass.h"
#include <stdexcept>

#define NETTY_JNI_UTIL_JNI_VERSION JNI_VERSION_1_6

std::unique_ptr<NativeExceptionClass> nativeExceptionClass;
std::unique_ptr<JavaNetworkAdapterClass> javaNetworkAdapterClass;

JNIEXPORT jobjectArray JNICALL Java_io_netty_resolver_dns_windows_WindowsResolverDnsServerAddressStreamProvider_adapters(JNIEnv* env, jclass)
{
    try {
        NetworkAdapters adapterWrapper;

        auto& adapters = adapterWrapper.get_adapters();

        if (adapters.size() > INT32_MAX)
        {
            throw std::overflow_error("adapters exceed java array size");
        }

        jobjectArray javaAdapters = javaNetworkAdapterClass->createArray(env, static_cast<jsize>(adapters.size()));
        
        for (int i = 0; i < adapters.size(); ++i) {
            jobject javaAdapter = javaNetworkAdapterClass->createInstance(env, adapters[i]);
            env->SetObjectArrayElement(javaAdapters, i, javaAdapter);
        }

        return javaAdapters;
    }
    catch(const std::exception& e) {
        nativeExceptionClass->throwNew(env, e.what());
    }
    return nullptr;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = NULL;

    if (vm->GetEnv((void**)&env, NETTY_JNI_UTIL_JNI_VERSION) != JNI_OK) {
        fprintf(stderr, "FATAL: JNI version mismatch");
        fflush(stderr);
        return JNI_ERR;
    }

    try {
        nativeExceptionClass = std::make_unique<NativeExceptionClass>(env);
        javaNetworkAdapterClass = std::make_unique<JavaNetworkAdapterClass>(env);
    }
    catch (const std::exception& e) {
        env->FatalError(e.what());
    }

    return NETTY_JNI_UTIL_JNI_VERSION;
}

JNIEXPORT void JNI_OnUnload(JavaVM*, void*) {
    nativeExceptionClass.reset();
    javaNetworkAdapterClass.reset();
}