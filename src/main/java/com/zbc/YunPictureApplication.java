package com.zbc;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@MapperScan("com.zbc.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
public class YunPictureApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunPictureApplication.class, args);
    }
}
