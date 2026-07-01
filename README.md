# PolicyPilot

AI-Powered Insurance Policy Assistant — Spring Boot 3 + Spring AI 1.0 + OpenAI + pgvector + Redis.

A production-style RAG application built to demonstrate end-to-end LLM integration in Java: PDF ingestion, semantic search, conversational memory, function calling, streaming, and prompt-injection defense.

## Architecture

```
Client ──► ChatController ──► ChatClient (Spring AI)
                                ├── QuestionAnswerAdvisor ──► pgvector (top-K retrieval)
                                ├── MessageChatMemoryAdvisor ──► Redis (session history)
                                ├── @Tool callbacks ──► PolicyTools (claim status, premium)
                                └── ChatModel ──► OpenAI GPT-4o-mini
```

## Prerequisites

- Java 17+
- Docker + Docker Compose
- An OpenAI API key (free tier works; total cost for end-to-end testing is under $0.10)

## Run It

```bash
export OPENAI_API_KEY=sk-...
docker compose up -d            # postgres+pgvector and redis
./mvnw spring-boot:run
```

Wait for `Started PolicyPilotApplication`. Pgvector's `vector_store` table is created automatically on first boot.

## Demo

### 1. Ingest a policy PDF

```bash
curl -F "file=@/path/to/policy-wording.pdf" \
     http://localhost:8080/api/v1/documents
# → {"filename":"policy-wording.pdf","chunks":42,"status":"INGESTED"}
```

### 2. Ask a question (RAG)

```bash
curl -X POST http://localhost:8080/api/v1/chat \
     -H 'Content-Type: application/json' \
     -d '{"question":"What is covered under medical emergencies abroad?","sessionId":"demo-1"}'
```

### 3. Follow-up (memory in action)

```bash
curl -X POST http://localhost:8080/api/v1/chat \
     -H 'Content-Type: application/json' \
     -d '{"question":"And what is the deductible for that?","sessionId":"demo-1"}'
```

The second question resolves "that" from context — no need to repeat "medical emergencies."

### 4. Trigger a tool call (function calling)

```bash
curl -X POST http://localhost:8080/api/v1/chat \
     -H 'Content-Type: application/json' \
     -d '{"question":"Whats the status of my claim, policy P12345?","sessionId":"demo-2"}'
```

The LLM emits a `getClaimStatus` tool call, Spring AI invokes `PolicyTools.getClaimStatus("P12345")`, and the model writes the reply on top of the structured result.

### 5. Stream a response (SSE)

```bash
curl -N "http://localhost:8080/api/v1/chat/stream?question=Summarise+the+travel+plan&sessionId=demo-3"
```

### 6. Get a recommendation

```bash
curl -X POST http://localhost:8080/api/v1/recommend \
     -H 'Content-Type: application/json' \
     -d '{"age":32,"maritalStatus":"married","dependents":2,"incomeLakhs":18,"riskAppetite":"medium"}'
```

## Project Layout

```
src/main/java/com/kushal/policypilot/
├── PolicyPilotApplication.java
├── config/AiConfig.java             # ChatClient bean: prompt + advisors + tools
├── controller/                       # 3 REST controllers
├── service/
│   ├── DocumentIngestionService.java # PDF → chunks → embeddings → pgvector
│   ├── PolicyTools.java              # @Tool methods (function calling)
│   ├── RecommendationService.java    # embedding-similarity recommendations
│   └── RedisChatMemory.java          # ChatMemory impl over Redis
└── dto/                              # Request/response records
```

## Switching to Anthropic Claude

Spring AI's `ChatClient` is provider-agnostic. To swap OpenAI for Claude:

1. Uncomment the `spring-ai-anthropic-spring-boot-starter` in `pom.xml`.
2. In `application.yml`, replace the `spring.ai.openai.*` block with `spring.ai.anthropic.*` (api-key, model, etc).
3. Done. Zero application-code changes.

This portability is what makes Spring AI the right abstraction for Java teams.

