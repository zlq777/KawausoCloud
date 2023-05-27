package cn.kawauso;

import cn.kawauso.network.EpollTCPService;
import cn.kawauso.network.GeneralTCPService;
import cn.kawauso.network.NetworkService;
import cn.kawauso.util.CommonUtils;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alibaba.nacos.spring.context.annotation.config.EnableNacosConfig;
import com.alibaba.nacos.spring.context.annotation.config.NacosPropertySource;
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
@EnableNacosConfig(globalProperties =
    @NacosProperties(serverAddr = "121.199.44.151:8848", username = "nacos", password = "nacos"))
@NacosPropertySource(dataId = "global_config_security", type = ConfigType.JSON)
public class ServiceClusterApplication {

    private static final Logger log = LogManager.getLogger(ServiceClusterApplication.class);

    @NacosValue("${secret-key}")
    private String secretKey;

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
    public EventExecutor initExecThreadGroup(@Value("${exec-threads}") int execThreads) {
        ThreadFactory execThreadFactory = CommonUtils.getThreadFactory("exec", true);
        return new UnorderedThreadPoolEventExecutor(execThreads, execThreadFactory);
    }

    /**
     * 配置并初始化{@link NetworkService}
     *
     * @param execThreadGroup {@link EventExecutor}任务执行线程池
     * @param ioThreads io线程数量
     * @param host 绑定的host地址
     * @param port 监听端口号
     * @return {@link NetworkService}
     */
    @Bean(destroyMethod = "close")
    public NetworkService initNetworkService(EventExecutor execThreadGroup,
                                             @Value("${network.tcp.io-threads}") int ioThreads,
                                             @Value("${network.tcp.host}") String host,
                                             @Value("${network.tcp.port}") int port) {

        NetworkService networkService = null;

        try {

            JWTVerifier tokenVerifier = JWT.require(Algorithm.HMAC256(secretKey)).build();

            ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(SocketChannel ch) {

                    ChannelPipeline pipeline = ch.pipeline();

                    HTTPRequestHandler requestHandler = new HTTPRequestHandler(tokenVerifier);
                    HttpObjectAggregator aggregator = new HttpObjectAggregator(Integer.MAX_VALUE);
                    HttpServerCodec serverCodec = new HttpServerCodec(
                            DEFAULT_MAX_INITIAL_LINE_LENGTH,
                            DEFAULT_MAX_HEADER_SIZE,
                            Integer.MAX_VALUE
                    );

                    pipeline.addLast(serverCodec);
                    pipeline.addLast(aggregator);
                    pipeline.addLast(execThreadGroup, requestHandler);
                }
            };

            if (Epoll.isAvailable()) {
                networkService = new EpollTCPService(host, port, ioThreads, initializer);
            } else {
                networkService = new GeneralTCPService(host, port, ioThreads, initializer);
            }

            networkService.start();

            log.info("Network-Service has started successfully !");
            log.info("Network-Service : core={} io-threads={} host={} port={}",
                    networkService.getName(), networkService.getIOThreads(),
                    networkService.getHost(), networkService.getPort());

        } catch (Exception e) {
            log.info("Network-Service started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return networkService;
    }

}
