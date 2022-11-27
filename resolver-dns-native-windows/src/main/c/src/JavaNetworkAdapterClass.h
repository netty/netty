#pragma once
#include <jni.h>
#include <string>
#include <vector>
#include "Connection.h"
#include "InetSocketAddress.h"
#include "NetworkAdapter.h"

class JavaNetworkAdapterClass {
public:
    JavaNetworkAdapterClass(JNIEnv* env);

    jobject createInstance(JNIEnv* env, const NetworkAdapter& adapter) const;

    jobjectArray createArray(JNIEnv* env, int size) const;
private:
    jclass clazz;
    jmethodID constructor;
    jclass string_clazz;

    const InetSocketAddress socket_address_class;
};

