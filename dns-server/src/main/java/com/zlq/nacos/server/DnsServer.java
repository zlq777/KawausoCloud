package com.zlq.nacos.server;

import com.zlq.nacos.domain.EntrRoute;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * DnsServer
 *
 * @author zlq
 * @version 2023/05/23 16:35
 **/

/**
 * DNS服务器主函数
 */
@Slf4j
public class DnsServer implements Runnable {
    private final ApplicationContext applicationContext;
    private final int port;

    public DnsServer(ApplicationContext applicationContext, int port) {
        this.applicationContext = applicationContext;
        this.port = port;
    }


    private void start() {
        final NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsResponseEncoder());
                            nioDatagramChannel.pipeline().addLast(new DnsHandler(applicationContext));
                        }
                    });

            log.error("DNS Server Start!");

            ChannelFuture future = bootstrap.bind(port).sync();

            log.error("...Waiting...");

            future.channel().closeFuture().sync();

            log.error("DNS Server End!");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }


    @Override
    public void run() {
        this.start();
    }
}
