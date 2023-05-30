package com.zlq.nacos.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * DnsService
 * 该类作用为可以在非Netty环境中使用Spring管理的Bean
 *
 * @author zlq
 * @version 2023/05/24 21:20
 **/

@Service
@Slf4j
public class DnsService implements ApplicationContextAware {

    private static ApplicationContext applicationContext;
    @Value("${netty.port}")
    private int port;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

        startDnsService(port);
    }

    private static void startDnsService(int port) {
        log.info("DNS start----->");

        DnsServer dnsServer = new DnsServer(applicationContext, port);

        try {
            new Thread(dnsServer).start();
        } catch (Exception e) {
            log.error("------->>");
        }

    }
}
