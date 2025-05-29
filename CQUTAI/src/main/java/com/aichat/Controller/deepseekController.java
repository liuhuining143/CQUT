package com.aichat.Controller;


import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class deepseekController {
    private final ChatClient chatClient;


   /* @RequestMapping(value = "/chat2",produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chat(String prompt, String chatId) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }*/

    @GetMapping("/chat")
    public SseEmitter sseChat(@RequestParam String prompt) {
        SseEmitter emitter = new SseEmitter(60 * 1000L); // 60秒超时

        chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .subscribe(
                        content -> {
                            try {
                                String utf8Content = new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                                emitter.send(SseEmitter.event().data(utf8Content));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }
}
