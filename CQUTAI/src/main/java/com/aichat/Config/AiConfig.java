package com.aichat.Config;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AiConfig {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Bean
    public Generation generation() {
        return new Generation();
    }

    @Bean
    public ChatModel chatModel(Generation generation) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("请设置 DASHSCOPE_API_KEY 环境变量");
        }

        return new ChatModel() {

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.create(sink -> {
                    executorService.submit(() -> {
                        try {
                            // 获取提示内容
                            String content = prompt.getContents();

                            // 构建阿里云消息格式
                            Message userMessage = Message.builder()
                                    .role(Role.USER.getValue())
                                    .content(content)
                                    .build();

                            // 创建流式请求参数
                            GenerationParam param = GenerationParam.builder()
                                    .apiKey(apiKey)
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
                                            System.out.println("【流式输出】收到回复块: " + chunkContent);
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
                            System.err.println("【流式输出错误】" );
                            sink.error(new RuntimeException("阿里云流式调用失败", e));
                        }
                    });
                });
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                try {
                    // 获取提示内容
                    String content = prompt.getContents();

                    // 构建阿里云消息格式
                    Message userMessage = Message.builder()
                            .role(Role.USER.getValue())
                            .content(content)
                            .build();

                    GenerationParam param = GenerationParam.builder()
                            .apiKey(apiKey)
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
                .defaultSystem("你是重庆理工大学的智能AI助手小Q，可以帮助学生回答所有关于重庆理工大学的问题。")
                .build();
    }
}