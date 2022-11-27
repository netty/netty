#include <winsock2.h>
#include <iphlpapi.h>
#include <stdlib.h>
#include <system_error>
#include "NetworkAdapters.h"
#include <memory>


#pragma comment(lib, "IPHLPAPI.lib")

using namespace std;

struct free_delete
{
    void operator()(void* x) { free(x); }
};


std::unique_ptr<IP_ADAPTER_ADDRESSES, free_delete> read_adapter_addresses() {
    const size_t MAX_TRIES = 3;

    unsigned long outBufLen = 15000;
 
    std::unique_ptr<IP_ADAPTER_ADDRESSES, free_delete> pAddresses;
    unsigned long dwRetVal = 1;

    for (int tries = 0; tries < MAX_TRIES; ++tries) {
        pAddresses = std::unique_ptr<IP_ADAPTER_ADDRESSES, free_delete>((IP_ADAPTER_ADDRESSES*) malloc(outBufLen));

        if (pAddresses == nullptr) {
            // This probably won't work either...
            throw runtime_error("Could not allocate address buffer");
        }

        ULONG flags = GAA_FLAG_SKIP_UNICAST | GAA_FLAG_SKIP_ANYCAST
            | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_FRIENDLY_NAME;

        // This call can update the outBufLen variable with the actual (required) size.
        dwRetVal = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, pAddresses.get(), &outBufLen);
        
        if (dwRetVal != ERROR_BUFFER_OVERFLOW) {
            break;
        }
    }


    if (dwRetVal == NO_ERROR) {
        return pAddresses;
    }
    else {
        throw runtime_error("Could not read adapter information.");
    }
}

NetworkAdapters::NetworkAdapters()
{
    std::unique_ptr<IP_ADAPTER_ADDRESSES, free_delete> addresses = read_adapter_addresses();

    for (IP_ADAPTER_ADDRESSES* adapter = addresses.get(); adapter != NULL; adapter = adapter->Next) {

        if (adapter->OperStatus != IfOperStatusUp) {
            continue;
        }

        if (adapter->IfType == IF_TYPE_SOFTWARE_LOOPBACK) {
            continue;
        }

        this->adapters.emplace_back(adapter);

    }
}

const std::vector<NetworkAdapter>& NetworkAdapters::get_adapters()
{
    return this->adapters;
}