package cn.kawauso;

import cn.kawauso.network.EpollTCPService;
import cn.kawauso.network.GeneralTCPService;
import cn.kawauso.network.NetworkService;
import cn.kawauso.util.CommonUtils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.JWTVerifier;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ThreadFactory;

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
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param secretKey 全局用户鉴权密钥
     * @param ioThreads io线程数量
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @return {@link NetworkService}
     */
    @Bean(destroyMethod = "close")
    public NetworkService initNetworkService(EventExecutor execThreadGroup,
                                             @Value("${main.secret-key}") String secretKey,
                                             @Value("${network.tcp.io-threads}") int ioThreads,
                                             @Value("${network.tcp.host}") String host,
                                             @Value("${network.tcp.port}") int port) {

        try {

            Algorithm authAlgorithm = Algorithm.HMAC256(secretKey);
            JWTVerifier tokenVerifier = JWT.require(authAlgorithm).build();

            ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();

                    HttpServerCodec serverCodec = new HttpServerCodec(
                            DEFAULT_MAX_INITIAL_LINE_LENGTH,
                            DEFAULT_MAX_HEADER_SIZE,
                            Integer.MAX_VALUE
                    );

                    HTTPRequestHandler requestHandler = new HTTPRequestHandler(tokenVerifier);

                    pipeline.addLast(serverCodec);
                    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                    pipeline.addLast(execThreadGroup, requestHandler);
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
            log.info("TCP-Service started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

}
