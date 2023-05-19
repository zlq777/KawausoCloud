package cn.kawauso;

import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.network.EpollHTTPService;
import cn.kawauso.network.GeneralHTTPService;
import cn.kawauso.network.HTTPService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

/**
 * {@link StorageService}提供了对象存储体系中的存储服务
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class StorageService {

    private static final Logger log = LogManager.getLogger(StorageService.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        SpringApplication.run(StorageService.class, args);
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
                    pipeline.addLast(execThreadGroup, new HTTPRequestHandler(stateMachine));
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
