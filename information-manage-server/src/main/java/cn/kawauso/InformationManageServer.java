package cn.kawauso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@link InformationManageServer}是信息管理服务节点进程的启动入口
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class InformationManageServer {

    public static void main(String[] args) {
        SpringApplication.run(InformationManageServer.class, args);
    }

}
