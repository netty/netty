package com.lhh.day04;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Copyright (C), 2019-2019
 * FileName: nettyNIODemo
 * Author:   s·D·bs
 * Date:     2019/5/15 23:50
 * Description: netty如何实现的
 * Motto: 0.45%
 *
 * @create 2019/5/15
 * @since 1.0.0
 */
public class nettyNIODemo {

    public static void main(String[] args) {

        clientDemo();
        serverDemo();

    }

    private static void serverDemo() {
//        对于 Netty NIO 服务端来说，创建两个 EventLoopGroup 。
//        bossGroup 对应 Reactor 模式的 mainReactor ，用于服务端接受客户端的连接。比较特殊的是，传入了方法参数 nThreads = 1 ，表示只使用一个 EventLoop ，即只使用一个 Reactor 。这个也符合我们上面提到的，“通常，mainReactor 只需要一个，因为它一个线程就可以处理”。
//        workerGroup 对应 Reactor 模式的 subReactor ，用于进行 SocketChannel 的数据读写。对于 EventLoopGroup ，如果未传递方法参数 nThreads ，表示使用 CPU 个数 Reactor 。这个也符合我们上面提到的，“通常，subReactor 的个数和 CPU 个数相等，每个 subReactor 独占一个线程来处理”。
//        因为使用两个 EventLoopGroup ，所以符合【多 Reactor 多线程模型】的多 Reactor 的要求。实际在使用时，workerGroup 在读完数据时，具体的业务逻辑处理，我们会提交到专门的业务逻辑线程池，例如在 Dubbo 或 Motan 这两个 RPC 框架中。这样一来，就完全符合【多 Reactor 多线程模型】。
//        那么可能有胖友可能和我有一样的疑问，bossGroup 如果配置多个线程，是否可以使用多个 mainReactor 呢？我们来分析一波，一个 Netty NIO 服务端同一时间，只能 bind 一个端口，那么只能使用一个 Selector 处理客户端连接事件。又因为，Selector 操作是非线程安全的，所以无法在多个 EventLoop ( 多个线程 )中，同时操作。所以这样就导致，即使 bossGroup 配置多个线程，实际能够使用的也就是一个线程。
//        那么如果一定一定一定要多个 mainReactor 呢？创建多个 Netty NIO 服务端，并绑定多个端口。
        // 创建两个 EventLoopGroup 对象
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // 创建 boss 线程组 用于服务端接受客户端的连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 创建 worker 线程组 用于进行 SocketChannel 的数据读写
// 创建 ServerBootstrap 对象
        ServerBootstrap b = new ServerBootstrap();
// 设置使用的 EventLoopGroup
        b.group(bossGroup, workerGroup);
    }

    private static void clientDemo() {

//        对于 Netty NIO 客户端来说，仅创建一个 EventLoopGroup 。
//        一个 EventLoop 可以对应一个 Reactor 。因为 EventLoopGroup 是 EventLoop 的分组，所以对等理解，EventLoopGroup 是一种 Reactor 的分组。
//        一个 Bootstrap 的启动，只能发起对一个远程的地址。所以只会使用一个 NIO Selector ，也就是说仅使用一个 Reactor 。即使，我们在声明使用一个 EventLoopGroup ，该 EventLoopGroup 也只会分配一个 EventLoop 对 IO 事件进行处理。
//        因为 Reactor 模型主要使用服务端的开发中，如果套用在 Netty NIO 客户端中，到底使用了哪一种模式呢？
//        如果只有一个业务线程使用 Netty NIO 客户端，那么可以认为是【单 Reactor 单线程模型】。
//        如果有多个业务线程使用 Netty NIO 客户端，那么可以认为是【单 Reactor 多线程模型】。
//        那么 Netty NIO 客户端是否能够使用【多 Reactor 多线程模型】呢？😈 创建多个 Netty NIO 客户端，连接同一个服务端。那么多个 Netty 客户端就可以认为符合多 Reactor 多线程模型了。
//        一般情况下，我们不会这么干。
//        当然，实际也有这样的示例。例如 Dubbo 或 Motan 这两个 RPC 框架，支持通过配置，同一个 Consumer 对同一个 Provider 实例同时建立多个客户端连接。
        // 创建一个 EventLoopGroup 对象
        EventLoopGroup group = new NioEventLoopGroup();
        // 创建 Bootstrap 对象
        Bootstrap b = new Bootstrap();
        // 设置使用的 EventLoopGroup
        b.group(group);
    }
}
