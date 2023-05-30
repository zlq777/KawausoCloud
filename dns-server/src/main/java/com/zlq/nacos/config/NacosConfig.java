package com.zlq.nacos.config;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlq.nacos.domain.EndRoute;
import com.zlq.nacos.domain.EntrDomain;
import com.zlq.nacos.domain.EntrRoute;
import com.zlq.nacos.domain.InfoDomain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * NacosConfig 该类用于在每次启动DNS服务器时候加载Nacos配置信息
 *
 * @author zlq
 * @version 2023/05/24 14:29
 **/
@Slf4j
@Component
public class NacosConfig {
    @Value("${nacos.config.server-addr}")
    private String nacosAddr;
    private HashMap<String, String> dgKey = new HashMap<>();
    @Autowired
    EndRoute endRoute;
    @Autowired
    EntrRoute entrRoute;

    /**
     * 在每次启动Dns服务的时候读取Nacos配置
     * 分别获得登录界面的url
     * 登录节点的域名
     * 终端节点的域名
     */
    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        Properties properties = new Properties();

        properties.setProperty("serverAddr", nacosAddr);
        properties.setProperty("namespace", "");
        properties.setProperty("username", "nacos");
        properties.setProperty("password", "nacos");

        try {

            ConfigService configService = ConfigFactory.createConfigService(properties);
            String config = configService.getConfig("global_config", "DEFAULT_GROUP", 1000);
            JsonNode jsonNode = mapper.readTree(config);
            String domain = jsonNode.get("domain-name").asText();

            String cluster = configService.getConfig("global_config_user_service_cluster", "DEFAULT_GROUP", 1000);
            jsonNode = mapper.readTree(cluster);

            EntrDomain entrDomain = new EntrDomain();
            for (int i = 0; i < jsonNode.size(); i++) {
                JsonNode entry = jsonNode.get(i);
                String ip = entry.get("ip").asText();
                String port = entry.get("port").asText();
                String url = ip + ":" + port;
                entrDomain.getArrayList().add(url);
            }
            entrRoute.getIpMap().put(domain, entrDomain);

            String endcluster = configService.getConfig("global_config_endpoint_service_cluster", "DEFAULT_GROUP", 1000);
            jsonNode = mapper.readTree(endcluster);
            HashMap<String, InfoDomain> endpoint = endRoute.getEndpoint();

            for (int id = 0; id < jsonNode.size(); id++) {
                JsonNode clusterNode = jsonNode.get(id).get("service-cluster");
                String idurl = id + domain;
                InfoDomain infoDomain = new InfoDomain();
                List<String> infodomain = infoDomain.getInfodomain();
                for (int i = 0; i < clusterNode.size(); i++) {
                    JsonNode entry = clusterNode.get(i);
                    String ip = entry.get("ip").asText();
                    String port = entry.get("port").asText();
                    String url = ip + ":" + port;
                    infodomain.add(url);
                }
                endpoint.put(idurl, infoDomain);
            }
            endRoute.setEndPointsNum(jsonNode.size());

        } catch (NacosException | IOException e) {

            log.error("no such property");
            throw new RuntimeException(e);

        }
    }

    public HashMap<String, String> getDgKey() {
        return dgKey;
    }

    public void setDgKey(HashMap<String, String> dgKey) {
        this.dgKey = dgKey;
    }
}
