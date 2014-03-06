## How to submit a bug report

Please ensure to specify the following:

* Netty version (e.g. 4.0.17.Final)
* Contextual information (e.g. what you were trying to achieve with Netty)
* Simplest possible steps to reproduce
  * More complex the steps are, lower the priority will be.
  * A pull request with failing JUnit test case is most preferred, although it's OK to paste the test case into the issue description.
* Anything that might be relevant in your opinion, such as:
  * JDK/JRE version or the output of `java -version`
  * Operating system and the output of `uname -a`
  * Network configuration


### Example

```
Netty version: 4.0.17.Final

Context:
I encountered an exception which looks suspicious while load-testing my Netty-based Thrift server implementation.

Steps to reproduce:
1. ...
2. ...
3. ...
4. ...

$ java -version
java version "1.7.0_51"
Java(TM) SE Runtime Environment (build 1.7.0_51-b13)
Java HotSpot(TM) 64-Bit Server VM (build 24.51-b03, mixed mode)

Operating system: Ubuntu Linux 13.04 64-bit

$ uname -a
Linux infinity 3.10.32-1-lts #1 SMP Sun Feb 23 09:44:24 CET 2014 x86_64 GNU/Linux

My system has IPv6 disabled.
```

## How to submit a pull request

Pull requests should be targeted at the branch for the latest stable releases.  If the pull request is for fixing a bug which also affects an old branch like `3.x`, we recommend you to submit another pull request for that branch, too.

1. [Rebase](http://git-scm.com/book/en/Git-Branching-Rebasing) your changes against the upstream branch.  Resolve any conflicts that arise.
1. Write JUnit test cases if possible. If not sure about how to write one, ask to write one before it's merged.
1. Run `mvn test` before the initial submission or the subsequent pushes, and ensure the build succeeds.

For more information on developing Netty, please refer to [the developer guide](http://netty.io/wiki/developer-guide.html).
