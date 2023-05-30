package com.zlq.nacos.domain;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

/**
 * EntrRoute ConcurrentHashMap实现存储,第一层映射,实现路由功能
 *
 * @author zlq
 * @version 2023/05/23 19:46
 **/
@Component
public class EntrRoute {
    private final HashMap<String, EntrDomain> entroute = new HashMap<>();

    public EntrRoute() {
    }

    public HashMap<String, EntrDomain> getIpMap() {
        return entroute;
    }

}
