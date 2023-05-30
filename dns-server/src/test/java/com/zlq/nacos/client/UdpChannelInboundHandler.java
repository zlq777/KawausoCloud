package com.zlq.nacos.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class UdpChannelInboundHandler extends SimpleChannelInboundHandler<DatagramDnsResponse> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
        try {

            readMsg(msg);
            DatagramDnsResponse content = msg.content();

            DefaultDnsRawRecord dnsRecord = msg.recordAt(DnsSection.ANSWER);

            ByteBuf byteBuf = dnsRecord.content().copy();
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            String message = new String(bytes, CharsetUtil.UTF_8);

            System.out.println(message);
            System.out.println(msg.recordAt(DnsSection.ANSWER).name());

        } finally {

            ctx.close();

        }
    }

    private static void readMsg(DatagramDnsResponse msg) {

        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            log.info("question is :{}", question);
        }

        int i = 0, count = msg.count(DnsSection.ANSWER);

        while (i < count) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                //A记录用来指定主机名或者域名对应的IP地址
                DnsRawRecord raw = (DnsRawRecord) record;
            }
            i++;
        }

    }
}
