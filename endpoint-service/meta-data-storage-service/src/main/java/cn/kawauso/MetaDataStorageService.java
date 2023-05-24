package cn.kawauso;

import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.network.EpollTCPService;
import cn.kawauso.network.GeneralTCPService;
import cn.kawauso.network.TCPService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * {@link MetaDataStorageService}作为对象存储体系的组成部分，负责存储对象元数据
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class MetaDataStorageService {

    private static final Logger log = LogManager.getLogger(MetaDataStorageService.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        SpringApplication.run(MetaDataStorageService.class, args);
    }

    /**
     * 配置并初始化{@link TCPService}，尝试启动相关的TCP网络通信服务
     *
     * @param stateMachine {@link RaftStateMachine}
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @param ioThreads io线程数量
     * @return {@link TCPService}
     */
    @Bean(destroyMethod = "close")
    public TCPService initHTTPService(RaftStateMachine stateMachine, EventExecutor execThreadGroup,
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

            TCPService TCPService;

            if (Epoll.isAvailable()) {
                TCPService = new EpollTCPService(host, port, ioThreads, initializer);
            } else {
                TCPService = new GeneralTCPService(host, port, ioThreads, initializer);
            }

            TCPService.start();

            log.info("TCP-Service has started successfully !");
            log.info("TCP-Service: core={} io-threads={} host={} port={}",
                    TCPService.getName(), TCPService.getIOThreads(),
                    TCPService.getHost(), TCPService.getPort());

            return TCPService;
        } catch (Exception e) {
            log.info("TCP-Service started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

}
