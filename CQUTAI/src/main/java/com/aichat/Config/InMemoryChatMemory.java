package com.aichat.Config;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChatMemory implements ChatMemory {

    // 使用线程安全的Map存储会话历史: key: conversationId, value: 消息列表
    private final Map<String, List<Message>> memoryStore = new ConcurrentHashMap<>();

    // 最大保留消息数
    private static final int MAX_MESSAGES_PER_CONVERSATION = 20;

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 如果会话ID不存在，创建新的会话
        memoryStore.computeIfAbsent(conversationId, k -> new ArrayList<>());

        // 添加新消息
        List<Message> conversation = memoryStore.get(conversationId);
        conversation.addAll(messages);

        // 限制最大消息数，保留最近的MAX_MESSAGES条
        if (conversation.size() > MAX_MESSAGES_PER_CONVERSATION) {
            conversation = conversation.subList(
                    conversation.size() - MAX_MESSAGES_PER_CONVERSATION,
                    conversation.size()
            );
            memoryStore.put(conversationId, conversation);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> conversation = memoryStore.getOrDefault(conversationId, Collections.emptyList());

        // 获取最近的lastN条消息
        int start = Math.max(0, conversation.size() - lastN);
        return conversation.subList(start, conversation.size());
    }

    @Override
    public void clear(String conversationId) {
        memoryStore.remove(conversationId);
    }

    // 添加清除所有会话的方法（可选）
    public void clearAll() {
        memoryStore.clear();
    }
}