package com.zlq.nacos.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public final class UdpClient {
    final private String host = "127.0.0.1";

    public void startDnsClient(String dnsServer, int dnsPort, String queryDomain) throws InterruptedException {

        InetSocketAddress addr = new InetSocketAddress(dnsServer, dnsPort);
        EventLoopGroup group = new NioEventLoopGroup();

        try {

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new UdpChannelInitializer());
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, 8888);
            final Channel ch = b.bind(inetSocketAddress).sync().channel();

            int randomID = (int) (System.currentTimeMillis() / 1000);
            DnsQuery query = new DatagramDnsQuery(inetSocketAddress, addr, randomID).setRecord(
                    DnsSection.QUESTION,
                    new DefaultDnsQuestion(queryDomain, DnsRecordType.A));
            ChannelFuture future = ch.writeAndFlush(query).sync();
            boolean result = ch.closeFuture().await(10, TimeUnit.SECONDS);

            if (!result) {
                log.error("DNS查询失败");
            }

            ch.closeFuture().sync();
            System.out.println(result);

        } finally {

            group.shutdownGracefully();

        }
    }

    @Test
    public void ClientTest() throws Exception {

        UdpClient client = new UdpClient();
        final String dnsServer = "127.0.0.1";
        final int dnsPort = 9999;
        final String queryDomain = "U2727heu.kawauso.cn";
        client.startDnsClient(dnsServer, dnsPort, queryDomain);

    }

}
