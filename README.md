# PolicyPilot — AI-Powered Insurance Policy Assistant

A production-style RAG (Retrieval-Augmented Generation) chatbot built in Java, that answers questions about insurance policy documents. Ingest a policy PDF, ask questions in natural language, get cited answers grounded in the document — with fallback to typed function calls for live business data.

**Stack:** Java 17 · Spring Boot 3.3 · Spring AI 1.0 GA · OpenAI GPT-4o-mini + text-embedding-3-small · PostgreSQL + pgvector · Redis · Docker Compose

**Author:** Kushal Roy · [LinkedIn](https://www.linkedin.com/in/kusharoy) · [GitHub](https://github.com/kushalry)

---

## What it does

- **Ingests** insurance policy PDFs, chunks and embeds them, stores vectors in Postgres with an HNSW index
- **Answers** natural-language questions using semantic retrieval + GPT-4o-mini synthesis
- **Streams** responses over Server-Sent Events (SSE) for perceived-latency wins
- **Remembers** conversations across turns via Redis-backed chat memory (30-min TTL)
- **Calls** typed Java methods (`getClaimStatus`, `getPremiumQuote`) via LLM function-calling when live data is needed instead of retrieval

The UI is at `http://localhost:8080` after boot — a minimal chat interface built to demonstrate the API without framework overhead.

## Architecture
Client ──► ChatController
│
▼
chatClient.prompt().user(q).call()
│
├──► QuestionAnswerAdvisor (RAG)
│       ├──► embed query ──► OpenAI
│       └──► similaritySearch ──► pgvector (HNSW, cosine)
│
├──► MessageChatMemoryAdvisor
│       └──► load/store history ──► Redis (per sessionId)
│
├──► PolicyTools (function calling)
│       └──► getClaimStatus, getPremiumQuote → typed Java methods
│
▼
GPT-4o-mini ──► composed response
│
▼
SSE stream to browser

## Engineering highlights

- **Chunk sanitization pipeline** — real PDFs contain null bytes and control characters that Postgres rejects. Cleaned at ingestion (`\u0000` stripping, control-char removal, whitespace normalization) before embedding.
- **Similarity threshold tuning** — Spring AI's default of 0.7 rejected on-topic chunks. Dropped to 0.3 based on empirical score distribution from live queries.
- **System prompt evolution** — first version treated "context" as documents only; refused conversational follow-ups even when history was loaded. Rewrote to acknowledge two sources (documents + conversation) and clarify refusal only when BOTH are unrelated.
- **SSE parsing** — the browser's `EventSource` API strips one leading whitespace character per event per the W3C spec, which mangles LLM tokens (each has a leading space). Client uses `fetch` + `ReadableStream` + hand-written SSE parser to preserve whitespace.
- **Observable memory bean** — `RedisChatMemory` instrumented with per-call logging on `.get()` and `.add()` to make silent bean-substitution failures visible.

## Getting started

Requirements: Java 17+, Docker + Compose, an OpenAI API key.

```bash
export OPENAI_API_KEY=sk-proj-...
docker compose up -d
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.ai.openai.api-key=$OPENAI_API_KEY --spring.ai.openai.embedding.api-key=$OPENAI_API_KEY"
```

Then:

1. Open `http://localhost:8080` — you'll see the chat UI
2. Upload a policy PDF via `curl -F "file=@policy.pdf" http://localhost:8080/api/v1/documents`
3. Ask questions in the UI

### Demo queries
"What's the medical coverage limit?"           → RAG retrieval
"How do I file a claim?"                        → RAG retrieval
"What's excluded from coverage?"                → multi-chunk synthesis
"Summarize what you just told me."              → conversation memory
"What's the status of my claim, policy P12345?" → function calling
"How much for a Gold plan, age 35, 10 lakh?"    → function calling
"Recipe for biryani?"                           → out-of-domain refusal

## Trade-offs and known limitations

- **Follow-up retrieval degradation** — vector search on a raw pronoun-anchored question ("what about people over 60") pulls irrelevant chunks because the query is expanded only in the LLM prompt, not in the retriever query. Production fix is a query-rewriting step (`RewriteQueryTransformer` in Spring AI) that expands the follow-up to a standalone question before hitting the vector store. Trade-off is doubled LLM cost per turn.
- **No re-ingestion dedup** — re-uploading the same PDF creates duplicate chunks. Production fix is content-hash-based idempotency on ingestion.
- **No authorization on tools** — `getClaimStatus` will return the status of any policy number the LLM sends. Production requires verifying the sessionId's authenticated user actually owns the policy first.
- **Redis chat memory is per-session** — no cross-session persistence, no summarization of long histories. Beyond ~20 turns, older turns get trimmed rather than summarized.

## What I learned

The interesting engineering isn't really "AI engineering" — it's distributed-systems engineering with a new kind of sink. Idempotency on ingestion, prompt engineering as real engineering, and observability of the seam between framework and model. The bugs that cost time were framework-mediated (silent bean substitution, protocol edge cases in SSE, similarity-threshold defaults tuned for wrong data) — the actual LLM behavior was the least surprising part.
