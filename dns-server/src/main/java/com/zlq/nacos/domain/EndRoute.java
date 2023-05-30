package com.zlq.nacos.domain;

import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * EndRoute
 * endPoint集群配置,第一层映射,实现路由功能
 *
 * @author zlq
 * @version 2023/05/24 12:47
 **/
@Service
public class EndRoute {
    HashMap<String, InfoDomain> endroute = new HashMap<>();

    private Integer endPointsNum;

    public Integer getEndPointsNum() {
        return endPointsNum;
    }

    public void setEndPointsNum(Integer endPointsNum) {
        this.endPointsNum = endPointsNum;
    }

    public HashMap<String, InfoDomain> getEndpoint() {
        return endroute;
    }

    public void setEndpoint(HashMap<String, InfoDomain> endroute) {
        this.endroute = endroute;
    }

    public HashMap<String, InfoDomain> getEndroute() {
        return endroute;
    }
}
