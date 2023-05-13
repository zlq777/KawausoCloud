package cn.kawauso.network;

import io.netty.channel.epoll.Epoll;

/**
 * {@link HTTPService}作为进程本地的HTTP服务，使用NIO模式进行TCP连接创建、数据读写。
 * 和{@link UDPService}一样，我们应该积极提供对linux epoll的支持，这通常可以通过{@link Epoll#isAvailable()}进行检测
 *
 * @author RealDragonking
 */
public interface HTTPService {

    /**
     * @return TCP服务所使用的内核名称
     */
    String getName();

    /**
     * @return TCP服务绑定的ip地址
     */
    String getHost();

    /**
     * @return TCP服务监听的端口
     */
    int getPort();

    /**
     * @return TCP服务使用的IO线程数量
     */
    int getIOThreads();

    /**
     * 尝试启动此{@link HTTPService}服务
     *
     * @throws Exception 启动过程中出现的异常
     */
    void start() throws Exception;

    /**
     * 关闭此{@link HTTPService}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    void close() throws Exception;

}
