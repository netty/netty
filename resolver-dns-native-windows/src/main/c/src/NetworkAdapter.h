#pragma once
#include <vector>
#include "Connection.h"
#include <iphlpapi.h>

class NetworkAdapter
{
public:
	NetworkAdapter(IP_ADAPTER_ADDRESSES* address);
	const std::vector<Connection>& get_dns_servers() const;
	const std::vector<std::wstring>& get_search_domains() const;
	const std::string& get_name() const;
	const int get_ipv4_index() const;
	const int get_ipv6_index() const;
private:
	const std::vector<Connection> dns_servers;
	const std::vector<std::wstring> search_domains;
	const std::string name;
	const int ipv4_index;
	const int ipv6_index;
};

