package com.kushal.policypilot.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * The four classical RAG ingestion steps, in one bean:
 *
 *   PDF bytes  →  page-aware text extraction  →  token chunking  →  embed + store
 *
* Spring AI's PagePdfDocumentReader keeps page-number metadata, which the LLM can
 * cite back to the user. TokenTextSplitter uses a CL100K tokenizer so chunk sizes
 * map cleanly to the embedding model's context window.
 */
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingest(MultipartFile file) {
        try {
            Resource resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            };

            // Reader: one Document per PDF page, with page-number metadata preserved
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build()
            );
            List<Document> pages = reader.get();

            // Tag every chunk with source filename so we can filter later
            pages.forEach(d -> d.getMetadata().put("source", file.getOriginalFilename()));

            // Splitter: ~800-token chunks (Spring AI's tuned defaults).
            // No overlap parameter in TokenTextSplitter — that's a LangChain feature.
            TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
            List<Document> chunks = splitter.apply(pages);

            // Real-world PDFs contain null bytes, control chars, and weird whitespace
            // that Postgres TEXT columns reject (0x00 is a string terminator in C).
            // Sanitize BEFORE storage. Cheaper than handling the rejection later.
            List<Document> cleanedChunks = chunks.stream()
                .map(d -> {
                    String text = d.getText();
                    if (text == null) return d;
                    String cleaned = text
                        .replace("\u0000", "")                  // strip null bytes
                        .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")  // other control chars, keep CR/LF/tab
                        .replaceAll("\\s+", " ")                 // collapse repeated whitespace
                        .trim();
                    return new Document(cleaned, d.getMetadata());
                })
                .filter(d -> d.getText() != null && d.getText().length() >= 50)  // drop tiny fragments
                .toList();

            // Spring AI handles embedding internally — VectorStore.add() calls the
            // EmbeddingModel bean, then upserts into pgvector.
            vectorStore.add(cleanedChunks);

            return cleanedChunks.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded PDF", e);
        }
    }
}
