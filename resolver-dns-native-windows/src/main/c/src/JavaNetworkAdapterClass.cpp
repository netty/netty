#include "JavaNetworkAdapterClass.h"
#include <locale>
#include <codecvt>
#include <cstdint>

JavaNetworkAdapterClass::JavaNetworkAdapterClass(JNIEnv* env) : socket_address_class(InetSocketAddress(env))
{
	this->clazz = env->FindClass("io/netty/resolver/dns/windows/NetworkAdapter");

	if (this->clazz == nullptr) {
        throw std::runtime_error("Could not find NetworkAdapter class.");
    }

	this->constructor = env->GetMethodID(this->clazz, "<init>", "([Ljava/net/InetSocketAddress;[Ljava/lang/String;II)V");

	if (this->constructor == nullptr) {
        throw std::runtime_error("Could not find NetworkAdapter constructor.");
    }

	this->string_clazz = env->FindClass("java/lang/String");
}

jobjectArray JavaNetworkAdapterClass::createArray(JNIEnv* env, int size) const {
	return env->NewObjectArray(size, this->clazz, nullptr);
}

jobject JavaNetworkAdapterClass::createInstance(JNIEnv* env, const NetworkAdapter& adapter) const
{
	std::vector<Connection> nameservers = adapter.get_dns_servers();
	std::vector<std::wstring> search_domains = adapter.get_search_domains();

	if (nameservers.size() > INT32_MAX)
	{
		throw std::overflow_error("nameservers exceed java array size");
	}

	jobjectArray javaNameservers = this->socket_address_class.createArray(env, static_cast<jsize>(nameservers.size()));

	for (int i = 0; i < nameservers.size(); ++i) {
		jobject nameserver = this->socket_address_class.createInstance(env, nameservers[i]);
		env->SetObjectArrayElement(javaNameservers, i, nameserver);
	}

	if (search_domains.size() > INT32_MAX)
	{
		throw std::overflow_error("search domains exceed java array size");
	}

	jobjectArray javaSearchDomains = env->NewObjectArray(static_cast<jsize>(search_domains.size()), this->string_clazz, nullptr);

	std::wstring_convert<std::codecvt_utf8<wchar_t>> converter;

	for (int i = 0; i < search_domains.size(); ++i) {
		jstring search_domain = env->NewStringUTF(converter.to_bytes(search_domains[i]).c_str());
		env->SetObjectArrayElement(javaSearchDomains, i, search_domain);
	}

	return env->NewObject(this->clazz, this->constructor, javaNameservers, javaSearchDomains,
	                        adapter.get_ipv4_index(), adapter.get_ipv6_index());
}
