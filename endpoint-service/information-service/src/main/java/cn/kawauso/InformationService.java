package cn.kawauso;

import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@link InformationService}提供了对象存储体系中的信息管理服务
 *
 * @author RealDragonking
 */
@SpringBootApplication
public class InformationService {

    private static final Logger log = LogManager.getLogger(InformationService.class);

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        SpringApplication.run(InformationService.class, args);
    }

}
