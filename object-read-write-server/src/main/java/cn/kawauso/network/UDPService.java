package cn.kawauso.network;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;

/**
 * {@link UDPService}作为进程本地的UDP服务，提供了发送和接收{@link DatagramPacket}的能力。
 * 我们应该积极提供对linux epoll的支持，这通常可以通过{@link Epoll#isAvailable()}进行检测
 *
 * @author RealDragonking
 */
public interface UDPService {

    /**
     * @return UDP服务所使用的内核名称
     */
    String getName();

    /**
     * @return UDP服务监听的端口
     */
    int getPort();

    /**
     * @return UDP服务使用的IO线程数量
     */
    int getIOThreads();

    /**
     * 设置{@link ChannelInitializer}
     *
     * @param initializer {@link ChannelInitializer}
     */
    void setChannelInitializer(ChannelInitializer<DatagramChannel> initializer);

    /**
     * 尝试启动此{@link UDPService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    void start() throws Exception;

    /**
     * 关闭此{@link UDPService}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    void close() throws Exception;

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet {@link DatagramPacket}
     */
    void send(DatagramPacket packet);

}
