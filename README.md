# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.

## Links

* [Web Site](http://netty.io/)
* [Downloads](http://netty.io/downloads.html)
* [Documentation](http://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](http://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You require the following to build Netty:

* Latest stable [Oracle JDK 7](http://www.oracle.com/technetwork/java/)
* Latest stable [Apache Maven](http://maven.apache.org/)
* If you are on Linux, you need [additional development packages](http://netty.io/wiki/native-transports.html) installed on your system, because you'll build the native transport.

Note that this is build-time requirement.  JDK 5 (for 3.x) or 6 (for 4.0+) is enough to run your Netty-based application.

## Branches to look

Development of all versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`.  For example, the development of 3.9 and 4.0 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and [the branch '4.0'](https://github.com/netty/netty/tree/4.0) respectively.

##Eclipse build补充

环境：windows   
IDE：Eclipse（64位） + M2E  netty团队建议用InteliJ   
步骤：   
* clone netty4.x代码到本地 https://github.com/netty/netty.git  或 https://github.com/maldou/netty.git
* 导入netty工程
* 错误1  pom.xml文件中 -- Missing artifact io.netty:netty-tcnative:jar:${os.detected.classifier}:1.1.33.Fork19

  原因    m2e不能计算出定义在pom.xml文件中的扩展值，如${os.detected.classifier}
  
  解决方式   下载maven插件os-maven-plugin  该插件存放在 <ECLIPSE_INSTALLATION_DIR>/plugins (Eclipse 4.5) or <ECLIPSE_INSTALLATION_DIR>/dropins (Eclipse 4.6) 路径下
* 错误2：形如：Plugin execution not covered by lifecycle configuration: org.codehaus.mojo:build-helper-maven-plugin:1.10:parse-version (execution: parse-version, phase: validate)

  解决方式见https://www.eclipse.org/m2e/documentation/m2e-execution-not-covered.html

  我采用的方式是配置  Windows -> Preferences -> Maven -> Lifecycle mapping   


