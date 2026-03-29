package com.zlt.aps;


import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.net.InetAddress;


/**
 *  APS-硫化-主启动程序
 */
@Slf4j
@SpringBootApplication
@EnableSwagger2
@ComponentScan(value = {"com.zlt.*"})
@MapperScan({"com.zlt.aps.**.mapper"})
public class ApsLhApplication {


    /** main启动函数 **/
    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext application = SpringApplication.run(ApsLhApplication.class, args);
        String ip = InetAddress.getLocalHost().getHostAddress();
        Environment env = application.getEnvironment();
        String port = env.getProperty("server.port");

        log.info("\n----------------------------------------------------------\n\t" +
                "Application Lh is running!\n\t" +
                "APS硫化接口文档 URLs:\n\t" +
                "接口文档: \thttp://" + ip + ":" + port + "/swagger-ui/index.html\n\t" +
                "接口文档: \thttp://" + ip + ":" + port + "/doc.html\n" +
                "----------------------------------------------------------\n\t"
        );
    }
}