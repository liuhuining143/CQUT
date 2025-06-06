package com.aichat.Controller;

import com.aichat.Config.InMemoryChatMemory;
import com.aichat.Model.KnowledgeEntry;
import com.aichat.Service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class deepseekController {
    private final ChatClient chatClient;
    private final InMemoryChatMemory chatMemory;
    private final KnowledgeBaseService knowledgeBaseService;


    // 系统消息内容
    private static final String SYSTEM_MESSAGE = "你是重庆大学的智能AI助手知理，可以帮助学生回答所有关于重庆大学的问题。";

    @GetMapping("/chat")
    public SseEmitter sseChat(
            @RequestParam String prompt,
            @RequestParam String chatId) {



        // 1. 从知识库中检索相关信息
        List<KnowledgeEntry> relatedDocs = knowledgeBaseService.semanticSearch(prompt, 10, 0.5); // topK=3, 相似度阈值0.7

        // 2. 构建系统提示词 + 知识片段
        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append(SYSTEM_MESSAGE).append("\n\n");

        if (!relatedDocs.isEmpty()) {
            systemPromptBuilder.append("以下是与问题相关的知识:\n");
            for (int i = 0; i < relatedDocs.size(); i++) {
                systemPromptBuilder.append(i + 1).append(". ").append(relatedDocs.get(i).toVectorDocument().getContent()).append("\n");
            }
            systemPromptBuilder.append("\n请基于以上知识回答用户问题。\n");
        }

        // 3. 获取历史消息
        List<Message> existingHistory = chatMemory.get(chatId, Integer.MAX_VALUE);

        // 4. 构建完整对话上下文
        StringBuilder fullContentBuilder = new StringBuilder();

        fullContentBuilder.append(systemPromptBuilder.toString()).append("\n\n"); // 添加知识增强后的系统提示

        for (Message msg : existingHistory) {
            if (msg instanceof UserMessage) {
                fullContentBuilder.append("用户: ").append(msg.getContent()).append("\n");
            } else if (msg instanceof AssistantMessage) {
                fullContentBuilder.append("助手: ").append(msg.getContent()).append("\n");
            }
        }

        fullContentBuilder.append("用户: ").append(prompt);

        Prompt chatPrompt = new Prompt(fullContentBuilder.toString());

        // 5. 流式回复
        SseEmitter emitter = new SseEmitter(60 * 1000L);
        StringBuilder aiResponseBuilder = new StringBuilder();

        chatClient.prompt(chatPrompt)
                .stream()
                .content()
                .subscribe(
                        content -> {
                            try {
                                String utf8Content = new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                                emitter.send(SseEmitter.event().data(utf8Content));
                                aiResponseBuilder.append(utf8Content);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        () -> {
                            List<Message> messagesToAdd = new ArrayList<>();
                            messagesToAdd.add(new UserMessage(prompt));
                            messagesToAdd.add(new AssistantMessage(aiResponseBuilder.toString()));
                            chatMemory.add(chatId, messagesToAdd);
                            emitter.complete();
                        }
                );

        return emitter;
    }


    // 添加会话管理端点
    @GetMapping("/clearSession")
    public String clearSession(@RequestParam String chatId) {
        chatMemory.clear(chatId);
        return "会话 " + chatId + " 已清除";
    }


}