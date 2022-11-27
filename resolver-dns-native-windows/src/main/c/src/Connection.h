#pragma once
#include <winsock2.h>
#include <Ws2ipdef.h>
#include <ws2tcpip.h>
#include <string>

class Connection {
public:
	Connection(LPSOCKADDR sock_address);

	std::string get_host() const;
	unsigned short get_port() const;
private:
	std::string host;
	unsigned short port;
};