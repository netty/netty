#include "connection.h"
#include <stdexcept>
#include <iostream>

#pragma comment(lib, "ws2_32.lib")

Connection::Connection(LPSOCKADDR sock_address) {
	switch (sock_address->sa_family) {
	case AF_INET: {
		char ip[INET_ADDRSTRLEN];

		struct sockaddr_in* addr_in = (struct sockaddr_in*)sock_address;

		inet_ntop(AF_INET, &addr_in->sin_addr, ip, sizeof(ip));
		this->port = ntohs(addr_in->sin_port);
		this->host = ip;
		break;
	}
	case AF_INET6: {
		char ip[INET6_ADDRSTRLEN];
		struct sockaddr_in6* addr_in = (struct sockaddr_in6*)sock_address;
		inet_ntop(AF_INET6, &addr_in->sin6_addr, ip, sizeof(ip));
		this->port = ntohs(addr_in->sin6_port);
		this->host = ip;
		break;
	}
	default:
		std::cerr << "Unknown address family " << sock_address->sa_family << std::endl;
		throw std::runtime_error("Unknown address family");
	}
}

std::string Connection::get_host() const
{
	return host;
}

unsigned short Connection::get_port() const
{
	return port;
}


