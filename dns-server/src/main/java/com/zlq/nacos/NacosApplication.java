package com.zlq.nacos;

import com.alibaba.nacos.spring.context.annotation.discovery.EnableNacosDiscovery;
import com.zlq.nacos.domain.EntrDomain;
import com.zlq.nacos.server.DnsServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Slf4j
@SpringBootApplication
@EnableNacosDiscovery
@EnableConfigurationProperties
public class NacosApplication implements CommandLineRunner {


    public static void main(String[] args) {
        SpringApplication.run(NacosApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
    }

}
