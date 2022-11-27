#pragma once
#include <jni.h>
#include "Connection.h"

class InetSocketAddress
{
public:
    InetSocketAddress(JNIEnv* env);

    jobject createInstance(JNIEnv* env, const Connection& connection) const;
    jobjectArray createArray(JNIEnv* env, int size) const;

private:
    jclass clazz;
    jmethodID constructor;
};

