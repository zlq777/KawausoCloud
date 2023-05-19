package cn.kawauso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@link EntranceService}是系统的入口服务，提供了首页资源分发、用户账号的注册和登录服务
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class EntranceService {

    public static void main(String[] args) {
        SpringApplication.run(EntranceService.class, args);
    }

}
