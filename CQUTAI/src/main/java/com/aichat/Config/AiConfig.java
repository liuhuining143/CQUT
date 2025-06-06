package com.aichat.Config;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.MongoDBAtlasVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Configuration
public class AiConfig {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Value("${dashscope.api.key}")
    private String dashscopeApiKey;

    @Value("${dashscope.embedding.model:text-embedding-v1}")
    private String embeddingModelName;

    @Bean
    public Generation generation() {
        return new Generation();
    }

    @Bean
    public TextEmbedding textEmbedding() {
        return new TextEmbedding();
    }

    @Bean
    public EmbeddingModel embeddingModel(TextEmbedding textEmbedding) {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> texts = request.getInstructions();
                try {
                    TextEmbeddingParam param = TextEmbeddingParam.builder()
                            .apiKey(dashscopeApiKey)
                            .model(embeddingModelName)
                            .texts(texts)
                            .build();

                    TextEmbeddingResult result = textEmbedding.call(param);

                    List<Embedding> embeddings = result.getOutput().getEmbeddings().stream()
                            .map(item -> {
                                List<Double> embeddingList = item.getEmbedding();
                                float[] vector = new float[embeddingList.size()];
                                for (int i = 0; i < vector.length; i++) {
                                    vector[i] = embeddingList.get(i).floatValue();
                                }
                                return new Embedding(vector, item.getTextIndex());
                            })
                            .collect(Collectors.toList());



                    return new EmbeddingResponse(embeddings);
                } catch (ApiException | NoApiKeyException e) {
                    throw new RuntimeException("DashScope embedding failed", e);
                }
            }

            @Override
            public float[] embed(Document document) {
                try {
                    TextEmbeddingParam param = TextEmbeddingParam.builder()
                            .apiKey(dashscopeApiKey)
                            .model(embeddingModelName)
                            .texts(Collections.singletonList(document.getContent()))
                            .build();

                    TextEmbeddingResult result = textEmbedding.call(param);
                    List<Double> embedding = result.getOutput().getEmbeddings().get(0).getEmbedding();


                    float[] floatArray = new float[embedding.size()];
                    for (int i = 0; i < floatArray.length; i++) {
                        floatArray[i] = embedding.get(i).floatValue();
                    }

                    return floatArray;
                } catch (Exception e) {
                    throw new RuntimeException("Embedding failed", e);
                }
            }
        };
    }


    @Bean
    public VectorStore vectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
        MongoDBAtlasVectorStore.MongoDBVectorStoreConfig config =
                MongoDBAtlasVectorStore.MongoDBVectorStoreConfig.builder()
                        .withVectorIndexName("vector_index") // 与Atlas中创建的索引名一致
                        .withPathName("embedding") // 必须与索引配置中的path字段一致
                        .withMetadataFieldsToFilter(List.of("category", "section")) // 添加过滤字段
                        .build();




        return new MongoDBAtlasVectorStore(
                mongoTemplate,
                embeddingModel,
                config,
                false // 设为false避免重复创建索引
        );
    }






    @Bean
    public ChatModel chatModel(Generation generation) {
        return new ChatModel() {
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.create(sink -> {
                    executorService.submit(() -> {
                        try {
                            // 获取完整的对话内容（包括系统消息和所有历史消息）
                            String fullContent = prompt.getContents();

                            // ✅ 打印完整请求内容到控制台
                            System.out.println("===== 发送给阿里云百炼的完整请求内容 =====");
                            System.out.println(fullContent);
                            System.out.println("========================================");

                            // 构建阿里云消息格式
                            Message userMessage = Message.builder()
                                    .role(Role.USER.getValue())
                                    .content(fullContent)
                                    .build();

                            // 创建流式请求参数
                            GenerationParam param = GenerationParam.builder()
                                    .apiKey(dashscopeApiKey)
                                    .model("deepseek-r1")
                                    .messages(Collections.singletonList(userMessage))
                                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                                    .incrementalOutput(true) // 启用增量输出
                                    .build();

                            // 使用官方推荐的流式调用方式
                            Flowable<GenerationResult> flowable = generation.streamCall(param);

                            // 直接订阅Flowable处理响应
                            flowable.blockingForEach(result -> {
                                // 处理每个流式块
                                if (result != null && result.getOutput() != null &&
                                        result.getOutput().getChoices() != null &&
                                        !result.getOutput().getChoices().isEmpty()) {

                                    // 获取消息内容
                                    GenerationOutput.Choice choice =
                                            result.getOutput().getChoices().get(0);

                                    if (choice.getMessage() != null) {
                                        String chunkContent = choice.getMessage().getContent();

                                        if (chunkContent != null && !chunkContent.isEmpty()) {
                                            // 创建Spring AI响应对象
                                            ChatResponse response = new ChatResponse(
                                                    Collections.singletonList(
                                                            new org.springframework.ai.chat.model.Generation(chunkContent)
                                                    )
                                            );

                                            // 发送响应块
                                            sink.next(response);
                                        }
                                    }
                                }
                            });

                            // 完成流
                            sink.complete();

                        } catch (Exception e) {
                            sink.error(new RuntimeException("阿里云流式调用失败", e));
                        }
                    });
                });
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                try {
                    // 获取完整的对话内容
                    String fullContent = prompt.getContents();

                    // 构建阿里云消息格式
                    Message userMessage = Message.builder()
                            .role(Role.USER.getValue())
                            .content(fullContent)
                            .build();

                    GenerationParam param = GenerationParam.builder()
                            .apiKey(dashscopeApiKey)
                            .model("deepseek-r1")
                            .messages(Collections.singletonList(userMessage))
                            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                            .build();

                    GenerationResult result = generation.call(param);

                    // 提取回复内容
                    String responseContent = result.getOutput().getChoices().get(0).getMessage().getContent();

                    // 转换为Spring AI的Generation对象
                    return new ChatResponse(Collections.singletonList(
                            new org.springframework.ai.chat.model.Generation(responseContent)
                    ));

                } catch (ApiException | NoApiKeyException | InputRequiredException e) {
                    throw new RuntimeException("阿里云API调用失败: " + e.getMessage(), e);
                }
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return new ChatOptions() {
                    @Override
                    public String getModel() {
                        return "deepseek-r1";
                    }

                    @Override
                    public Float getFrequencyPenalty() { return 0f; }

                    @Override
                    public Integer getMaxTokens() { return 1024; }

                    @Override
                    public Float getPresencePenalty() { return 0f; }

                    @Override
                    public List<String> getStopSequences() { return Collections.emptyList(); }

                    @Override
                    public Float getTemperature() { return 0.7f; }

                    @Override
                    public Integer getTopK() { return 50; }

                    @Override
                    public Float getTopP() { return 0.95f; }

                    @Override
                    public ChatOptions copy() { return this; }
                };
            }
        };
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是重庆大学的智能AI助手知理，可以帮助学生回答所有关于重庆大学的问题。")
                .build();
    }
}