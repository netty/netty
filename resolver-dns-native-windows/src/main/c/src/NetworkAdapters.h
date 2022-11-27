#pragma once
#include <winsock2.h>
#include <iphlpapi.h>
#include <vector>
#include "connection.h"
#include "NetworkAdapter.h"

class NetworkAdapters {
public:
	NetworkAdapters();
	const std::vector<NetworkAdapter>& get_adapters();
private:
	std::vector<NetworkAdapter> adapters;
};