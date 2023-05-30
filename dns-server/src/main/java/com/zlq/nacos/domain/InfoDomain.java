package com.zlq.nacos.domain;

import org.springframework.stereotype.Component;

import javax.sound.sampled.Line;
import java.util.ArrayList;
import java.util.List;

/**
 * InfoDomain information-service节点实例的ip,第二层映射
 *
 * @author zlq
 * @version 2023/05/24 12:10
 **/
@Component
public class InfoDomain {
    private List<String> infodomain;

    public List<String> getInfodomain() {
        return infodomain;
    }

    public void setInfodomain(List<String> infodomain) {
        this.infodomain = infodomain;
    }

    public InfoDomain() {
        this.infodomain = new ArrayList<>();
    }

    public InfoDomain(List<String> list) {
        infodomain = list;
    }

    public InfoDomain(String ip) {
        this.infodomain = new ArrayList<>();
        this.infodomain.add(ip);
    }
}
