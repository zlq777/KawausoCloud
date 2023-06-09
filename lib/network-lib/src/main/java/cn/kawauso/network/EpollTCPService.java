package cn.kawauso.network;

import cn.kawauso.util.CommonUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.util.concurrent.ThreadFactory;

/**
 * {@link EpollTCPService}是{@link NetworkService}的TCP协议linux平台特化级实现，仅能在linux内核3.9以上版本运行，
 * 提供了更为强大的性能
 *
 * @author RealDragonking
 */
public final class EpollTCPService implements NetworkService {

    private final ChannelInitializer<SocketChannel> initializer;
    private final EventLoopGroup serverThreadGroup;
    private final EventLoopGroup ioThreadGroup;
    private final String host;
    private final int port;
    private final int ioThreads;

    public EpollTCPService(String host, int port, int ioThreads,
                           ChannelInitializer<SocketChannel> initializer) {

        ThreadFactory ioThreadFactory = CommonUtils.getThreadFactory("TCP-io", true);
        this.ioThreadGroup = new EpollEventLoopGroup(ioThreads, ioThreadFactory);

        this.serverThreadGroup = new EpollEventLoopGroup(1);
        this.initializer = initializer;

        this.ioThreads = ioThreads;
        this.host = host;
        this.port = port;
    }

    /**
     * @return 服务所使用的内核名称
     */
    @Override
    public String getName() {
        return "epoll";
    }

    /**
     * @return 服务绑定的ip地址
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * @return 服务监听的端口
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * @return 服务使用的IO线程数量
     */
    @Override
    public int getIOThreads() {
        return ioThreads;
    }

    /**
     * 尝试启动此{@link NetworkService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(serverThreadGroup, ioThreadGroup)
                .channel(EpollServerSocketChannel.class)
                .childHandler(initializer);

        bootstrap.bind(host, port).sync();
    }

    /**
     * 关闭此{@link NetworkService}的进程
     */
    @Override
    public void close() {
        serverThreadGroup.shutdownGracefully();
        ioThreadGroup.shutdownGracefully();
    }

}
