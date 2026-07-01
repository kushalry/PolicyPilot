package com.kushal.policypilot.controller;

import com.kushal.policypilot.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Upload a PDF, chunk it, embed it, store it in pgvector — all in one call.
     * Returns the number of chunks created (useful for sanity-checking the pipeline).
     *
     * Tip for interview: explain why ingestion is idempotent on content hash (you'll
     * add that in v2) — you don't want re-uploading the same PDF to double-store.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "PDF required"));
        }
        int chunkCount = ingestionService.ingest(file);
        return ResponseEntity.ok(Map.of(
            "filename", file.getOriginalFilename(),
            "chunks", chunkCount,
            "status", "INGESTED"
        ));
    }
}
