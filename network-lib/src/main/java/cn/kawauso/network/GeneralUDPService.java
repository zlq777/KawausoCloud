package cn.kawauso.network;

import cn.kawauso.util.CommonUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.util.concurrent.ThreadFactory;

/**
 * {@link GeneralUDPService}是{@link UDPService}的通用级实现，允许运行在所有操作系统环境中。然而唯一不足的是，
 * 在{@link GeneralUDPService}中我们仅能使用一个IO线程来完成全部的读写任务
 *
 * @author RealDragonking
 */
public final class GeneralUDPService implements DuplexUDPService {

    private final ChannelInitializer<DatagramChannel> initializer;
    private final EventLoopGroup ioThreadGroup;
    private final int port;
    private Channel channel;

    public GeneralUDPService(int port, ChannelInitializer<DatagramChannel> initializer) {
        ThreadFactory ioThreadFactory = CommonUtils.getThreadFactory("UDP-io", true);
        this.ioThreadGroup = new NioEventLoopGroup(1, ioThreadFactory);

        this.initializer = initializer;
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
     * 尝试启动此{@link UDPService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        RecvByteBufAllocator recvAllocator = new FixedRecvByteBufAllocator(65535);

        bootstrap.group(ioThreadGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.RCVBUF_ALLOCATOR, recvAllocator)
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
