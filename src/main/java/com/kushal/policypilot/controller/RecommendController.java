package com.kushal.policypilot.controller;

import com.kushal.policypilot.dto.UserProfile;
import com.kushal.policypilot.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final RecommendationService service;

    public RecommendController(RecommendationService service) {
        this.service = service;
    }

    /**
     * Recommend up to top-3 policies for the user based on semantic similarity
     * between the user's profile-derived query and the embedded product catalog.
     */
    @PostMapping
    public List<Map<String, Object>> recommend(@Valid @RequestBody UserProfile profile) {
        return service.recommend(profile, 3);
    }
}
