package cn.kawauso.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.util.concurrent.ThreadFactory;

import static cn.kawauso.util.CommonUtils.*;

/**
 * {@link GeneralUDPService}是{@link UDPService}的通用级实现，允许运行在所有操作系统环境中。然而唯一不足的是，
 * 在{@link GeneralUDPService}中我们仅能使用一个IO线程来完成全部的读写任务
 *
 * @author RealDragonking
 */
public final class GeneralUDPService implements UDPService {

    private final EventLoopGroup ioThreadGroup;
    private final int port;
    private ChannelInitializer<DatagramChannel> initializer;
    private Channel channel;

    public GeneralUDPService(int port) {
        ThreadFactory ioThreadFactory = getThreadFactory("UDP-io", true);
        this.ioThreadGroup = new NioEventLoopGroup(1, ioThreadFactory);

        this.port = port;
    }

    /**
     * @return UDP服务所使用的内核名称
     */
    @Override
    public String getName() {
        return "general";
    }

    /**
     * @return UDP服务监听的端口
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * @return UDP服务使用的IO线程数量
     */
    @Override
    public int getIOThreads() {
        return 1;
    }

    /**
     * 设置{@link ChannelInitializer}
     *
     * @param initializer {@link ChannelInitializer}
     */
    @Override
    public void setChannelInitializer(ChannelInitializer<DatagramChannel> initializer) {
        this.initializer = initializer;
    }

    /**
     * 尝试启动此{@link UDPService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(ioThreadGroup)
                .channel(NioDatagramChannel.class)
                .handler(initializer);

        channel = bootstrap.bind(port).sync().channel();
    }

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet {@link DatagramPacket}
     */
    @Override
    public void send(DatagramPacket packet) {
        channel.writeAndFlush(packet);
    }

    /**
     * 关闭此{@link UDPService}的进程
     */
    @Override
    public void close() {
        ioThreadGroup.shutdownGracefully();
    }

}
