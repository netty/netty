# Netty io_uring

The new io_uring interface added to the Linux Kernel 5.1 is a high I/O performance scalable interface for fully asynchronous Linux syscalls

## Requirements:

- x86-64 / aarch64 / riscv64 processor
- at least Kernel 5.14, compiled with `CONFIG_IO_URING=y`
- to run the tests, you have to increase memlock(default 64K)


See [our wiki page](https://netty.io/wiki/native-transports.html).

## How to include the dependency

To include the dependency you need to ensure you also specify the right classifier. At the moment we only support linux
 x86_64 and aarch_64 but this may change. 
 
As an example this is how you would include the dependency in maven for x86_64:
```
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-io_uring</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

And in gradle:
```
dependencies {
    implementation 'io.netty:netty-transport-native-io_uring:${netty.version}:linux-x86_64'
}
```

## FAQ

### I tried to use io_uring but got `java.lang.RuntimeException: failed to create io_uring ring fd Cannot allocate memory`

When you tried to use io_uring but saw an exception that looked like this please try to encreate the [memlock limit](https://access.redhat.com/solutions/61334) for the user that tries to use io_uring:


```
Exception in thread "main" java.lang.UnsatisfiedLinkError: failed to load the required native library
	at io.netty.channel.uring.IoUring.ensureAvailability(IOUring.java:63)
        ...
        ...
Caused by: java.lang.RuntimeException: failed to create io_uring ring fd Cannot allocate memory
	at io.netty.channel.uring.Native.ioUringSetup(Native Method)
	at io.netty.channel.uring.Native.createRingBuffer(Native.java:141)
	at io.netty.channel.uring.Native.createRingBuffer(Native.java:174)
	at io.netty.channel.uring.IoUring.<clinit>(IOUring.java:36)
	... 17 more
```


You can check your current memlock settings by

```
$ ulimit -l
65536
```
