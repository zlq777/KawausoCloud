package cn.kawauso.network;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramPacket;

/**
 * {@link UDPService}作为进程本地的UDP服务，能够单向接收{@link DatagramPacket}并使用{@link io.netty.channel.ChannelInboundHandler}
 * 进行处理。我们应该积极提供对linux epoll的支持，这通常可以通过{@link Epoll#isAvailable()}进行检测
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

}
