![Build project](https://github.com/netty/netty/workflows/Build%20project/badge.svg)

# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.

## Links

* [Web Site](https://netty.io/)
* [Downloads](https://netty.io/downloads.html)
* [Documentation](https://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)
* [Official Discord server](https://discord.gg/q4aQ2XjaCa)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](https://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You require the following to build Netty:

* Latest stable [OpenJDK 8](https://adoptium.net/)
* Latest stable [Apache Maven](https://maven.apache.org/)
* If you are on Linux or MacOS, you need [additional development packages](https://netty.io/wiki/native-transports.html) installed on your system, because you'll build the native transport.

Note that this is build-time requirement.  JDK 5 (for 3.x) or 6 (for 4.0+ / 4.1+) is enough to run your Netty-based application.

## Branches to look

Development of all versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`.  For example, the development of 3.9 and 4.1 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and [the branch '4.1'](https://github.com/netty/netty/tree/4.1) respectively.

## Usage with JDK 9+

You can read the [Modular Netty guide](testsuite-jpms/README.md) to learn more about using Netty with the Java Platform Module System, the guide 
contains a user section and a developer section for Netty contributors.