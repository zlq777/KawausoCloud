package cn.kawauso;

import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.network.EpollTCPService;
import cn.kawauso.network.GeneralTCPService;
import cn.kawauso.network.NetworkService;
import cn.kawauso.util.CommonUtils;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ThreadFactory;

/**
 * {@link StorageClusterApplication}作为对象存储体系的组成部分，负责存储对象主体数据
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class StorageClusterApplication {

    private static final Logger log = LogManager.getLogger(StorageClusterApplication.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        SpringApplication.run(StorageClusterApplication.class, args);
    }

    /**
     * 初始化全局可用的{@link EventExecutor}任务执行线程池
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
     * 配置并初始化{@link NetworkService}
     *
     * @param stateMachine {@link RaftStateMachine}
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @param ioThreads io线程数量
     * @return {@link cn.kawauso.network.NetworkService}
     */
    @Bean(destroyMethod = "close")
    public NetworkService initNetworkService(RaftStateMachine stateMachine, EventExecutor execThreadGroup,
                                             @Value("${network.tcp.host}") String host,
                                             @Value("${network.tcp.port}") int port,
                                             @Value("${network.tcp.io-threads}") int ioThreads) {

        try {

            ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NotNull SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    // TODO
                }
            };

            NetworkService networkService;

            if (Epoll.isAvailable()) {
                networkService = new EpollTCPService(host, port, ioThreads, initializer);
            } else {
                networkService = new GeneralTCPService(host, port, ioThreads, initializer);
            }

            networkService.start();

            log.info("Network-Service has started successfully !");
            log.info("Network-Service: core={} io-threads={} host={} port={}",
                    networkService.getName(), networkService.getIOThreads(),
                    networkService.getHost(), networkService.getPort());

            return networkService;
        } catch (Exception e) {
            log.info("Network-Service started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

}
