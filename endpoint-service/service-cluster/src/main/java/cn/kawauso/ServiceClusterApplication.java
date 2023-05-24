package cn.kawauso;

import cn.kawauso.network.EpollTCPService;
import cn.kawauso.network.GeneralTCPService;
import cn.kawauso.network.TCPService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

/**
 * {@link ServiceClusterApplication}负责对外提供数据读写、访问的服务
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class ServiceClusterApplication {

    private static final Logger log = LogManager.getLogger(ServiceClusterApplication.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        SpringApplication.run(ServiceClusterApplication.class, args);
    }

    /**
     * 配置并初始化{@link TCPService}
     *
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @param ioThreads io线程数量
     * @return {@link TCPService}
     */
    @Bean(destroyMethod = "close")
    public TCPService initTCPService(EventExecutor execThreadGroup,
                                     @Value("${network.tcp.host}") String host,
                                     @Value("${network.tcp.port}") int port,
                                     @Value("${network.tcp.io-threads}") int ioThreads) {

        try {

            ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();

                    HttpServerCodec serverCodec = new HttpServerCodec(
                            DEFAULT_MAX_INITIAL_LINE_LENGTH,
                            DEFAULT_MAX_HEADER_SIZE,
                            Integer.MAX_VALUE
                    );

                    pipeline.addLast(serverCodec);
                    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                    pipeline.addLast(execThreadGroup, new HTTPRequestHandler());
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
