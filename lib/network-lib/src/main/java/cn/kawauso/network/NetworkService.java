package cn.kawauso.network;

import io.netty.channel.epoll.Epoll;

/**
 * {@link NetworkService}作为进程本地的网络服务，能够配置各种{@link io.netty.channel.ChannelInboundHandler}，对数据包
 * 进行处理。我们应该积极提供对linux epoll的支持，这通常可以通过{@link Epoll#isAvailable()}进行检测
 *
 * @author RealDragonking
 */
public interface NetworkService {

    /**
     * @return 服务所使用的内核名称
     */
    String getName();

    /**
     * @return 服务绑定的ip地址
     */
    String getHost();

    /**
     * @return 服务监听的端口
     */
    int getPort();

    /**
     * @return 服务使用的IO线程数量
     */
    int getIOThreads();

    /**
     * 尝试启动此{@link NetworkService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    void start() throws Exception;

    /**
     * 关闭此{@link NetworkService}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    void close() throws Exception;

}
