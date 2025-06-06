package com.aichat.Model;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document; // MongoDB 实体注解


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Document(collection = "springboot")
public class KnowledgeEntry {

    // Getters and Setters
    @Id
    private String id;

    @Indexed
    private String category;

    private String title;

    private String content;

    private String section;

    private LocalDateTime timestamp;

    // 转换为 Spring AI 文档格式（使用别名 AIDocument）
    public org.springframework.ai.document.Document toVectorDocument() {
        String vectorText = String.format("%s: %s", title, content);

        Map<String, Object> metadata = new HashMap<>();
        if (category != null) metadata.put("category", category);
        if (section != null) metadata.put("section", section);
        if (timestamp != null) metadata.put("timestamp", timestamp.toString());



        return org.springframework.ai.document.Document.builder()
                .withContent(vectorText)
                .withMetadata(metadata)
                .withId(this.id)
                .build();
    }


    // 从 Spring AI 文档转换（参数使用别名）
    public static KnowledgeEntry fromVectorDocument(org.springframework.ai.document.Document doc) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(doc.getId());
        entry.setTitle(extractTitle(doc.getContent()));
        entry.setContent(extractContent(doc.getContent()));
        entry.setCategory((String) doc.getMetadata().get("category"));
        entry.setSection((String) doc.getMetadata().get("section"));
        entry.setTimestamp(LocalDateTime.parse((String) doc.getMetadata().get("timestamp")));
        return entry;
    }

    // 辅助方法保持不变
    private static String extractTitle(String vectorText) {
        int colonIndex = vectorText.indexOf(':');
        return colonIndex > 0 ? vectorText.substring(0, colonIndex).trim() : vectorText;
    }

    private static String extractContent(String vectorText) {
        int colonIndex = vectorText.indexOf(':');
        return colonIndex > 0 ? vectorText.substring(colonIndex + 1).trim() : "";
    }

    public void setId(String id) { this.id = id; }

    public void setCategory(String category) { this.category = category; }

    public void setTitle(String title) { this.title = title; }

    public void setContent(String content) { this.content = content; }

    public void setSection(String section) { this.section = section; }

    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}