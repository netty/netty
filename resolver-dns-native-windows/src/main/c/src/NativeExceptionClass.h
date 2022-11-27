#pragma once
#include <jni.h>

class NativeExceptionClass {
public:
    NativeExceptionClass(JNIEnv* env);

    void throwNew(JNIEnv* env, const char* message);
private:
    jclass clazz;
};

