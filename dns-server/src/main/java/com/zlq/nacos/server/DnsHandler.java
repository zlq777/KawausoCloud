package com.zlq.nacos.server;

import com.zlq.nacos.domain.EndRoute;
import com.zlq.nacos.domain.EntrDomain;
import com.zlq.nacos.domain.EntrRoute;
import com.zlq.nacos.domain.InfoDomain;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DnsHandler
 * 用来解析域名并包装并派送DataGram包
 *
 * @author zlq
 * @version 2023/05/23 16:35
 **/
@Slf4j
@Component
public class DnsHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
    final private String NotFound = "No such resource..";
    StringBuilder sb = new StringBuilder();
    private ApplicationContext applicationContext;

    public DnsHandler(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {

        EndRoute endRoute = applicationContext.getBean("endRoute", EndRoute.class);
        HashMap<String, InfoDomain> endpoints = endRoute.getEndpoint();

        EntrRoute entrRoute = applicationContext.getBean("entrRoute", EntrRoute.class);
        HashMap<String, EntrDomain> ipMap = entrRoute.getIpMap();

        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        log.info("客户端的单播请求地址:{}", query.sender());

        try {
            DefaultDnsQuestion dnsQuestion = query.recordAt(DnsSection.QUESTION);
            String name = dnsQuestion.name();
            name = name.substring(0, name.length() - 1);
            log.info("收到报文:{}", name);

            String[] split = name.split("\\.");
            String id = null;
            String domain = null;
            if(split.length == 3) {
                id = split[0];
                domain = split[1] + "." + split[2];
            } else {
                domain = name;
            }

            ByteBuf buf = null;

            if (id == null) {
                if (ipMap.containsKey(domain)) {
                    EntrDomain entrDomain = ipMap.get(domain);
                    List<String> ipList = entrDomain.getIp(2);
                    sb.setLength(0);
                    for (String s : ipList) {
                        sb.append(s).append("/n");
                    }
                    buf = Unpooled.wrappedBuffer(sb.toString().getBytes());
                    log.info("服务端准备返回解析ip:{}", sb);
                }
            } else {
                int sum = 0;
                for(char c : id.toCharArray()) {
                    sum += (int)c;
                }
                String Url = (sum % endRoute.getEndPointsNum()) + domain;

                if (endpoints.containsKey(Url)) {
                    InfoDomain infoDomain = endpoints.get(Url);
                    List<String> infoList = infoDomain.getInfodomain();
                    sb.setLength(0);
                    for (String s : infoList) {
                        sb.append(s).append("/n");
                    }
                    buf = Unpooled.wrappedBuffer(sb.toString().getBytes());
                    log.info("服务端准备返回解析ip:{}", sb);
                }

            }

            if (buf == null) {
                log.error("URL NOT FOUND");
                buf = Unpooled.wrappedBuffer(NotFound.getBytes());
            }

            log.info("DNS返回包准备完毕,询问域名:{},解析id:{}", dnsQuestion.name(), buf.toString());
            DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(dnsQuestion.name(), DnsRecordType.A, 1000, buf);
            response.addRecord(DnsSection.ANSWER, queryAnswer);

        } finally {
            ctx.writeAndFlush(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

}
