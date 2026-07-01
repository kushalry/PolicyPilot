package com.kushal.policypilot.controller;

import com.kushal.policypilot.dto.ChatRequest;
import com.kushal.policypilot.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Synchronous RAG chat. The advisors (RAG + memory) configured on the
     * ChatClient bean handle retrieval and history injection automatically.
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest req) {
        String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();

        String answer = chatClient.prompt()
            .user(req.question())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .call()
            .content();

        return new ChatResponse(sessionId, answer);
    }

    /**
     * SSE streaming variant. The client receives tokens as they're generated —
     * critical for perceived latency on long answers.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String question,
                               @RequestParam(required = false) String sessionId) {
        String sid = sessionId != null ? sessionId : UUID.randomUUID().toString();

        return chatClient.prompt()
            .user(question)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sid))
            .stream()
            .content();
    }
}
