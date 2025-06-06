package com.aichat.Service;

import com.aichat.Model.KnowledgeEntry;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final MongoTemplate mongoTemplate;
    private final EmbeddingModel embeddingModel;

    public KnowledgeBaseService(VectorStore vectorStore, MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.mongoTemplate = mongoTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 添加知识条目到向量数据库
     * @param entry 知识条目实体
     */
    public void addKnowledgeEntry(KnowledgeEntry entry) {
        // 构建标准化文档
        org.bson.Document mongoDoc = new org.bson.Document()
                .append("_id", entry.getId())
                .append("content", entry.getTitle() + ": " + entry.getContent())
                .append("embedding", embeddingModel.embed(entry.toVectorDocument()))
                .append("metadata", new org.bson.Document()
                        .append("category", entry.getCategory())
                        .append("section", entry.getSection())
                        .append("timestamp", Instant.now().toString()));

        mongoTemplate.save(mongoDoc, "vector_store");
    }


    /**
     * 批量添加知识条目
     * @param entries 知识条目列表
     */
    public void batchAddKnowledgeEntries(List<KnowledgeEntry> entries) {
        List<Document> documents = entries.stream()
                .peek(entry -> entry.setTimestamp(LocalDateTime.now()))
                .map(KnowledgeEntry::toVectorDocument)
                .collect(Collectors.toList());

        vectorStore.add(documents);
    }

    /**
     * 语义检索知识条目
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param similarityThreshold 相似度阈值(0-1)
     * @return 知识条目列表
     */
    public List<KnowledgeEntry> semanticSearch(String query, int topK, double similarityThreshold) {
        // 添加关键词回退逻辑
        List<KnowledgeEntry> results = vectorStore.similaritySearch(
                SearchRequest.query(query)
                        .withTopK(topK)
                        .withSimilarityThreshold(similarityThreshold)
        ).stream().map(KnowledgeEntry::fromVectorDocument).toList();

        if(results.isEmpty()) {
            // 关键词匹配回退
            String filter = String.format("content == '%s' or title == '%s'", query, query);
            return vectorStore.similaritySearch(
                    SearchRequest.query("").withFilterExpression(filter)
            ).stream().map(KnowledgeEntry::fromVectorDocument).toList();
        }
        return results;
    }

    /**
     * 带过滤条件的语义检索
     * @param query 查询文本
     * @param category 分类过滤
     * @param section 部分过滤
     * @param minTimestamp 最小更新时间
     * @param topK 返回结果数量
     * @return 知识条目列表
     */


    public List<KnowledgeEntry> filteredSearch(
            String query,
            String category,
            String section,
            LocalDateTime minTimestamp,
            int topK) {

        // 构建过滤表达式字符串
        StringBuilder filterExpr = new StringBuilder();
        if (category != null) {
            filterExpr.append("metadata.category == '").append(category).append("'");
        }
        if (section != null) {
            if (!filterExpr.isEmpty()) filterExpr.append(" AND ");
            filterExpr.append("metadata.section == '").append(section).append("'");
        }
        if (minTimestamp != null) {
            if (!filterExpr.isEmpty()) filterExpr.append(" AND ");
            filterExpr.append("metadata.timestamp >= '").append(minTimestamp.toString()).append("'");
        }

        SearchRequest request = SearchRequest.query(query)
                .withTopK(topK);

        // 设置过滤表达式（如果存在）
        if (!filterExpr.isEmpty()) {
            request = request.withFilterExpression(filterExpr.toString());
        }

        return vectorStore.similaritySearch(request)
                .stream()
                .map(KnowledgeEntry::fromVectorDocument)
                .collect(Collectors.toList());
    }


    /**
     * 按分类检索知识条目
     * @param category 分类名称
     * @param pageable 分页参数
     * @return 分页知识条目
     */
    public Page<KnowledgeEntry> findByCategory(String category, Pageable pageable) {
        // 构建过滤表达式
        String filterExpr = String.format("metadata.category == '%s'", category);

        // 创建搜索请求
        SearchRequest request = SearchRequest.query("")
                .withFilterExpression(filterExpr)
                .withTopK(1000); // 获取最多1000条用于分页

        List<KnowledgeEntry> allEntries = vectorStore.similaritySearch(request).stream()
                .map(KnowledgeEntry::fromVectorDocument)
                .collect(Collectors.toList());

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allEntries.size());
        List<KnowledgeEntry> pageContent = allEntries.subList(start, end);

        return new PageImpl<>(pageContent, pageable, allEntries.size());
    }

    /**
     * 更新知识条目
     * @param id 条目ID
     * @param newContent 新内容
     */
    public void updateKnowledgeEntry(String id, String newContent) {
        // 1. 检索现有条目 - 使用更高效的方式
        String filterExpr = String.format("id == '%s'", id);

        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.query("")
                        .withFilterExpression(filterExpr)
                        .withTopK(1)
        );

        if (existing.isEmpty()) {
            throw new IllegalArgumentException("未找到ID为 " + id + " 的知识条目");
        }

        // 2. 更新内容
        Document document = existing.get(0);
        KnowledgeEntry entry = KnowledgeEntry.fromVectorDocument(document);
        entry.setContent(newContent);
        entry.setTimestamp(LocalDateTime.now());

        // 3. 删除旧条目并添加更新后的条目
        vectorStore.delete(Collections.singletonList(id));
        vectorStore.add(List.of(entry.toVectorDocument()));
    }

    /**
     * 删除知识条目
     * @param id 条目ID
     */
    public void deleteKnowledgeEntry(String id) {
        vectorStore.delete(Collections.singletonList(id));
    }



}