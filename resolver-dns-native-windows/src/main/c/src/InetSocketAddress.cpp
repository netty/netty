#include "InetSocketAddress.h"

InetSocketAddress::InetSocketAddress(JNIEnv* env)
{
	this->clazz = env->FindClass("java/net/InetSocketAddress");
	this->constructor = env->GetMethodID(this->clazz, "<init>", "(Ljava/lang/String;I)V");
}

jobject InetSocketAddress::createInstance(JNIEnv* env, const Connection& connection) const {
	jint port = connection.get_port();
	jstring host = env->NewStringUTF(connection.get_host().c_str());

	return env->NewObject(this->clazz, this->constructor, host, port);
}

jobjectArray InetSocketAddress::createArray(JNIEnv* env, int size) const {
	return env->NewObjectArray(size, this->clazz, nullptr);
}