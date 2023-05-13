package cn.kawauso;

import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.network.*;
import cn.kawauso.util.CommonUtils;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

/**
 * {@link ObjectStorageServer}是对象存储服务节点进程的启动入口
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class ObjectStorageServer {

    private static final Logger log = LogManager.getLogger(ObjectStorageServer.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(Level.DISABLED);
        SpringApplication.run(ObjectStorageServer.class, args);
    }

    /**
     * 初始化全局可用的{@link EventExecutor}线程池
     *
     * @param execThreads 任务执行线程数量
     * @return {@link EventExecutor}
     */
    @Bean(destroyMethod = "shutdownGracefully")
    public EventExecutor initExecThreadGroup(@Value("${main.exec-threads}") int execThreads) {
        ThreadFactory execThreadFactory = CommonUtils.getThreadFactory("exec", true);
        return new UnorderedThreadPoolEventExecutor(execThreads, execThreadFactory);
    }

    /**
     * 初始化{@link UDPService}
     *
     * @param port 监听端口
     * @param ioThreads io线程数量
     * @return {@link UDPService}
     */
    @Bean(destroyMethod = "close")
    public UDPService initUDPService(@Value("${network.udp.port}") int port,
                                     @Value("${network.udp.io-threads}") int ioThreads) {
        if (Epoll.isAvailable()) {
            return new EpollUDPService(port, ioThreads);
        } else {
            return new GeneralUDPService(port);
        }
    }

    /**
     * 配置并初始化{@link RaftStateMachine}状态机内核，尝试启动相关的UDP网络通信服务
     *
     * @param udpService {@link UDPService}
     * @param execThreadGroup {@link ExecutorService}任务执行线程池
     * @param index 当前节点在集群中的序列号
     * @param tickValue 每个时间单元
     * @param sendInterval 发送消息的时间间隔
     * @param minElectTimeout 最小的选举超时时间
     * @param maxElectTimeout 最大的选举超时时间
     * @param sendWindowSize Entry同步数据发送的时间间隔
     * @param allNodeAddresses 所有节点的地址列表
     *
     * @return {@link RaftStateMachine}
     */
    @Bean(destroyMethod = "close")
    public RaftStateMachine initNewStateMachine(UDPService udpService, EventExecutor execThreadGroup,
                                                @Value("${raft.index}") int index,
                                                @Value("${raft.timer.tick-value}") int tickValue,
                                                @Value("${raft.timer.send-interval}") int sendInterval,
                                                @Value("${raft.timer.min-elect-timeout}") int minElectTimeout,
                                                @Value("${raft.timer.max-elect-timeout}") int maxElectTimeout,
                                                @Value("${raft.send-window-size}") int sendWindowSize,
                                                @Value("${raft.all-node-address}") String allNodeAddresses) {

        try {

            RaftStateMachine stateMachine = new DefaultRaftStateMachine(
                    udpService,
                    index,
                    tickValue,
                    sendInterval,
                    minElectTimeout,
                    maxElectTimeout,
                    sendWindowSize,
                    allNodeAddresses.split(",")
            );

            ChannelInboundHandler handler = new UDPMessageHandler(stateMachine);

            udpService.setChannelInitializer(new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NotNull DatagramChannel ch) {
                    ch.pipeline().addLast(execThreadGroup, handler);
                }
            });

            udpService.start();
            stateMachine.start();

            log.info("RaftStateMachine has started successfully !");
            log.info("UDP-Service: core={} io-threads={} port={}",
                    udpService.getName(), udpService.getIOThreads(), udpService.getPort());

            return stateMachine;
        } catch (Exception e) {
            log.info("RaftStateMachine started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

    /**
     * 配置并初始化{@link HTTPService}，尝试启动相关的TCP网络通信服务
     *
     * @param stateMachine {@link RaftStateMachine}
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @param ioThreads io线程数量
     * @return {@link HTTPService}
     */
    @Bean(destroyMethod = "close")
    public HTTPService initHTTPService(RaftStateMachine stateMachine, EventExecutor execThreadGroup,
                                       @Value("${network.tcp.host}") String host,
                                       @Value("${network.tcp.port}") int port,
                                       @Value("${network.tcp.io-threads}") int ioThreads) {

        try {

            ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NotNull SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();

                    HttpServerCodec serverCodec = new HttpServerCodec(
                            DEFAULT_MAX_INITIAL_LINE_LENGTH,
                            DEFAULT_MAX_HEADER_SIZE,
                            Integer.MAX_VALUE
                    );

                    pipeline.addLast(serverCodec);
                    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                    pipeline.addLast(execThreadGroup, new HTTPMessageHandler(stateMachine));
                }
            };

            HTTPService httpService;

            if (Epoll.isAvailable()) {
                httpService = new EpollHTTPService(host, port, ioThreads, initializer);
            } else {
                httpService = new GeneralHTTPService(host, port, ioThreads, initializer);
            }

            httpService.start();

            log.info("HTTP/TCP-Service has started successfully !");
            log.info("HTTP/TCP-Service: core={} io-threads={} host={} port={}",
                    httpService.getName(), httpService.getIOThreads(),
                    httpService.getHost(), httpService.getPort());

            return httpService;
        } catch (Exception e) {
            log.info("HTTP-Service started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

}
