package com.zlq.nacos.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class UdpChannelInitializer extends ChannelInitializer<DatagramChannel> {
    @Override
    protected void initChannel(DatagramChannel ch) throws Exception {

        ChannelPipeline p = ch.pipeline();
        p.addLast(new DatagramDnsQueryEncoder())
                .addLast(new DatagramDnsResponseDecoder())
                .addLast(new UdpChannelInboundHandler());

    }
}
