#include "NetworkAdapter.h"


std::vector<Connection> calculate_dns_servers(IP_ADAPTER_ADDRESSES* address)
{
	std::vector<Connection> servers;

	for (IP_ADAPTER_DNS_SERVER_ADDRESS* dnsServer = address->FirstDnsServerAddress;
		dnsServer != NULL; dnsServer = dnsServer->Next) {
		servers.emplace_back(dnsServer->Address.lpSockaddr);
	}

	return servers;
}

std::vector<std::wstring> calculate_search_domains(IP_ADAPTER_ADDRESSES* address)
{
	std::vector<std::wstring> search_domains;

	if (address->DnsSuffix[0] != '\0') {
		search_domains.emplace_back(address->DnsSuffix);
	}

	for (PIP_ADAPTER_DNS_SUFFIX dnsSuffix = address->FirstDnsSuffix; dnsSuffix != nullptr; dnsSuffix = dnsSuffix->Next) {
		if (dnsSuffix->String[0] != '\0') {
			search_domains.emplace_back(dnsSuffix->String);
		}
	}

	return search_domains;
}

NetworkAdapter::NetworkAdapter(IP_ADAPTER_ADDRESSES* address) : dns_servers(calculate_dns_servers(address)), 
  search_domains(calculate_search_domains(address)), name(address->AdapterName),
  ipv4_index(address->IfIndex), ipv6_index(address->Ipv6IfIndex)
{

}

const std::vector<Connection>& NetworkAdapter::get_dns_servers() const {
	return this->dns_servers;
}

const std::vector<std::wstring>& NetworkAdapter::get_search_domains() const {
	return this->search_domains;
}

const std::string& NetworkAdapter::get_name() const {
	return this->name;
}

const int NetworkAdapter::get_ipv4_index() const {
    return this->ipv4_index;
}

const int NetworkAdapter::get_ipv6_index() const {
    return this->ipv6_index;
}