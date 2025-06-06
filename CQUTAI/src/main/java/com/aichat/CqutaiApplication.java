package com.aichat;

import com.aichat.Model.KnowledgeEntry;
import com.aichat.Service.KnowledgeBaseService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@SpringBootApplication
public class CqutaiApplication {



    public static void main(String[] args) {
        SpringApplication.run(CqutaiApplication.class, args);
    }

    @Bean
    CommandLineRunner initData(KnowledgeBaseService service, MongoTemplate mongoTemplate) {
        return args -> {
            List<KnowledgeEntry> allEntries = mongoTemplate.findAll(KnowledgeEntry.class);
            if(!allEntries.isEmpty()) {
                service.batchAddKnowledgeEntries(allEntries);
                System.out.println("已初始化 " + allEntries.size() + " 条数据到向量库");
            }
        };
    }

}
