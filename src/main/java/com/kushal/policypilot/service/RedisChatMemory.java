package com.kushal.policypilot.service;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;



/**
 * Redis-backed ChatMemory. One Redis LIST per conversation, capped at the last
 * N messages, with a sliding 30-minute TTL.
 * <p>
 * Why this matters: in-memory memory dies on every pod restart in a real
 * deployment. Redis lets you horizontally scale the chat service while still
 * giving every user a coherent multi-turn conversation.
 * <p>
 * Trade-off note for interviews: storing entire message history grows tokens
 * linearly. Production systems either (a) cap N, (b) summarize older turns
 * with a cheap model, or (c) embed and retrieve relevant past messages. We do (a).
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final org.slf4j.Logger log = 
        org.slf4j.LoggerFactory.getLogger(RedisChatMemory.class);

    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisChatMemory(StringRedisTemplate redis) {
        this.redis = redis;
        log.warn("RedisChatMemory bean instantiated with Redis template: {}", redis);
    }

    private String key(String conversationId) {
        return "chat:" + conversationId;
    }

   @Override
    public void add(String conversationId, List<Message> messages) {
        log.warn("RedisChatMemory.add() called with conversationId={}, {} messages", 
            conversationId, messages.size());
        String k = key(conversationId);
        for (Message m : messages) {
            redis.opsForList().rightPush(k, serialize(m));
        }
        // Cap list size — trim keeps the LAST MAX_MESSAGES entries
        redis.opsForList().trim(k, -MAX_MESSAGES, -1);
        redis.expire(k, TTL);
    }

@Override
    public List<Message> get(String conversationId) {
        log.warn("RedisChatMemory.get() called with conversationId={}", conversationId);
        List<String> raw = redis.opsForList().range(key(conversationId), 0, -1);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Message> out = new ArrayList<>(raw.size());
        for (String s : raw) out.add(deserialize(s));
        return out;
    }

    @Override
    public void clear(String conversationId) {
        redis.delete(key(conversationId));
    }

    // --- (de)serialization ---
    // We persist a simple {type, content} envelope so we don't depend on
    // Spring AI's internal Jackson polymorphism.

    private String serialize(Message m) {
        try {
            return mapper.writeValueAsString(Map.of(
                "type", m.getMessageType().getValue(),
                "content", m.getText() == null ? "" : m.getText()
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat message", e);
        }
    }

    private Message deserialize(String s) {
        try {
            Map<String, String> map = mapper.readValue(s, new TypeReference<>() {});
            String type = map.get("type");
            String content = map.getOrDefault("content", "");
            return switch (type) {
                case "user" -> new UserMessage(content);
                case "assistant" -> new AssistantMessage(content);
                case "system" -> new SystemMessage(content);
                default -> new UserMessage(content);
            };
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize chat message", e);
        }
    }
}
