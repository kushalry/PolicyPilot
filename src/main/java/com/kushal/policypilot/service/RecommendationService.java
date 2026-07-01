package com.kushal.policypilot.service;

import com.kushal.policypilot.dto.UserProfile;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recommendation = "RAG without the generation step".
 * <p>
 * We project the user's structured profile into a natural-language query, embed it
 * using the SAME embedding model the catalog was embedded with (this matters — you
 * cannot mix models, the vector spaces are different), and return the top-K
 * cosine-similar product chunks.
 * <p>
 * Interview signal: when you explain this, mention that real recommender systems
 * combine this dense-retrieval signal with collaborative filtering, business rules,
 * and re-ranking — this is a baseline, not a final design.
 */
@Service
public class RecommendationService {

    private final VectorStore vectorStore;
    private final String catalogFilter;

    public RecommendationService(VectorStore vectorStore,
                                 @Value("${policypilot.catalog.tag:product}") String catalogFilter) {
        this.vectorStore = vectorStore;
        this.catalogFilter = catalogFilter;
    }

    public List<Map<String, Object>> recommend(UserProfile profile, int topK) {
        String query = buildQuery(profile);

        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.5)
            .filterExpression("doc_type == '" + catalogFilter + "'")
            .build();

        List<Document> hits = vectorStore.similaritySearch(request);

        return hits.stream()
            .map(d -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("productId", d.getMetadata().get("productId"));
                out.put("title", d.getMetadata().get("title"));
                out.put("snippet", d.getText());
                out.put("score", d.getScore());
                return out;
            })
            .collect(Collectors.toList());
    }

    private String buildQuery(UserProfile p) {
        return String.format(
            "I am %d years old, %s, with %d dependents. My annual income is around %d lakhs. " +
            "I would describe my risk appetite as %s. Recommend insurance policies that suit me.",
            p.age(),
            p.maritalStatus(),
            p.dependents(),
            p.incomeLakhs(),
            p.riskAppetite()
        );
    }
}
