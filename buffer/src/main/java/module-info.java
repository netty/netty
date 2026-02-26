module io.netty.buffer {
    requires io.netty.common;
    requires static jdk.jfr;

    // For tests:
    requires static java.management;
}