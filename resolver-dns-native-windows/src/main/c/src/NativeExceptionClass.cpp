#include "NativeExceptionClass.h"
#include <stdexcept>

NativeExceptionClass::NativeExceptionClass(JNIEnv* env)
{
	this->clazz = env->FindClass("io/netty/resolver/dns/windows/NativeException");

	if (this->clazz == nullptr) {
	   throw std::runtime_error("Could not find NativeException class.");
	}
}

void NativeExceptionClass::throwNew(JNIEnv* env, const char* message)
{
	if (env->ThrowNew(this->clazz, message)) {
		throw std::runtime_error("Could not throw java exception");
	}
}
