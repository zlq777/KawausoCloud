package cn.kawauso.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;

import java.util.concurrent.ThreadFactory;

import static cn.kawauso.Utils.getThreadFactory;

/**
 * {@link EpollUDPService}是{@link UDPService}的linux平台特化级实现，仅能在linux内核3.9以上版本运行。
 * {@link EpollUDPService}除了提供更为强大的性能，同时也支持使用多个IO线程来完成读写任务。为了均匀地将写任务分摊到
 * 每个{@link io.netty.channel.EventLoop}上，我们选择使用无锁化的环形链表来迭代遍历每个节点，每个节点都绑定了一个
 * {@link Channel}
 *
 * @author RealDragonking
 */
public final class EpollUDPService implements UDPService {

    private final EventLoopGroup ioThreadGroup;
    private final int port;
    private final int ioThreads;
    private ChannelInitializer<DatagramChannel> initializer;
    private ChannelNode channelNode;

    public EpollUDPService(int port, int ioThreads) {
        ThreadFactory ioThreadFactory = getThreadFactory("UDP-io", true);
        this.ioThreadGroup = new EpollEventLoopGroup(ioThreads, ioThreadFactory);

        this.ioThreads = ioThreads;
        this.port = port;
    }

    /**
     * @return UDP服务所使用的内核名称
     */
    @Override
    public String getName() {
        return "epoll";
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
        return ioThreads;
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
                .channel(EpollDatagramChannel.class)
                .option(EpollChannelOption.SO_REUSEPORT, true)
                .handler(initializer);

        if (ioThreads > 0) {
            ChannelNode[] nodes = new ChannelNode[ioThreads];
            ChannelNode prev = null;
            ChannelNode node;

            for (int i = 0; i < ioThreads; i++) {
                Channel channel = bootstrap.bind(port).sync().channel();
                node = new ChannelNode(channel);

                if (prev != null) {
                    prev.nextNode = node;
                }

                nodes[i] = prev = node;
            }

            prev.nextNode = nodes[0];
            this.channelNode = prev;
        }
    }

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet {@link DatagramPacket}
     */
    @Override
    public void send(DatagramPacket packet) {
        channelNode.bindChannel.writeAndFlush(packet);
        channelNode = channelNode.nextNode;
    }

    /**
     * 关闭此{@link UDPService}的进程
     */
    @Override
    public void close() {
        ioThreadGroup.shutdownGracefully();
    }

    /**
     * {@link ChannelNode}绑定了一个{@link Channel}，并且具备指向下一个{@link ChannelNode}的指针
     *
     * @author RealDragonking
     */
    private static class ChannelNode {

        private final Channel bindChannel;
        private ChannelNode nextNode;

        private ChannelNode(Channel bindChannel) {
            this.bindChannel = bindChannel;
        }

    }

}
