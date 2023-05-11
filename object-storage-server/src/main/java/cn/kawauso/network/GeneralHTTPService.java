package cn.kawauso.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.concurrent.ThreadFactory;

import static cn.kawauso.util.CommonUtils.getThreadFactory;

/**
 * {@link GeneralHTTPService}是{@link HTTPService}的通用级实现，允许运行在所有操作系统上
 *
 * @author RealDragonking
 */
public final class GeneralHTTPService implements HTTPService {

    private final ChannelInitializer<SocketChannel> initializer;
    private final EventLoopGroup serverThreadGroup;
    private final EventLoopGroup ioThreadGroup;
    private final String host;
    private final int port;
    private final int ioThreads;

    public GeneralHTTPService(String host, int port, int ioThreads,
                              ChannelInitializer<SocketChannel> initializer) {

        ThreadFactory ioThreadFactory = getThreadFactory("TCP-io", true);
        this.ioThreadGroup = new NioEventLoopGroup(ioThreads, ioThreadFactory);

        this.serverThreadGroup = new NioEventLoopGroup(1);
        this.initializer = initializer;

        this.ioThreads = ioThreads;
        this.host = host;
        this.port = port;
    }

    /**
     * @return TCP服务所使用的内核名称
     */
    @Override
    public String getName() {
        return "general";
    }

    /**
     * @return TCP服务绑定的ip地址
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * @return TCP服务监听的端口
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * @return TCP服务使用的IO线程数量
     */
    @Override
    public int getIOThreads() {
        return ioThreads;
    }

    /**
     * 尝试启动此{@link HTTPService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(serverThreadGroup, ioThreadGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(initializer);

        bootstrap.bind(host, port).sync();
    }

    /**
     * 关闭此{@link HTTPService}的进程
     */
    @Override
    public void close() {
        serverThreadGroup.shutdownGracefully();
        ioThreadGroup.shutdownGracefully();
    }

}
