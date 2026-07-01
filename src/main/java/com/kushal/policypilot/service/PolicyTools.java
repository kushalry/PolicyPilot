package com.kushal.policypilot.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tools exposed to the LLM via Spring AI's function-calling mechanism.
 * <p>
 * The LLM doesn't execute these — it emits a structured "tool_call" payload
 * matching the @Tool method's JSON schema (derived from parameter names, types
 * and the @ToolParam descriptions). Spring AI parses that, invokes the method,
 * and feeds the return value back into the conversation as a "tool_result"
 * message. The LLM then writes a natural-language reply on top.
 * <p>
 * In a real codebase these would call your downstream microservices.
 * For the demo, they return canned data — that's fine, the interview signal
 * is about the *mechanism*, not the business logic.
 */
@Component
public class PolicyTools {

    private static final Map<String, Map<String, Object>> CLAIMS = new ConcurrentHashMap<>(Map.of(
        "P12345", Map.of("status", "APPROVED", "amountInr", 25000, "settledOn", "2026-05-12"),
        "P67890", Map.of("status", "UNDER_REVIEW", "submittedOn", "2026-06-20"),
        "P11111", Map.of("status", "REJECTED", "reason", "Pre-existing condition not declared")
    ));

    @Tool(description = "Get the latest status of a submitted insurance claim by policy number")
    public Map<String, Object> getClaimStatus(
            @ToolParam(description = "The policy number, format: P followed by digits, e.g. P12345")
            String policyNumber) {
        return CLAIMS.getOrDefault(policyNumber.toUpperCase(),
            Map.of("status", "NOT_FOUND", "policyNumber", policyNumber));
    }

    @Tool(description = "Calculate the annual premium quote for a travel insurance policy")
    public Map<String, Object> getPremiumQuote(
            @ToolParam(description = "Plan tier: SILVER, GOLD, or PLATINUM")
            String tier,
            @ToolParam(description = "Age of the primary insured in years")
            int age,
            @ToolParam(description = "Coverage amount in lakhs (1 lakh = 100,000 INR)")
            int coverageLakhs) {
        double base = switch (tier.toUpperCase()) {
            case "SILVER" -> 500;
            case "GOLD" -> 850;
            case "PLATINUM" -> 1400;
            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        };
        double ageLoading = age > 60 ? 1.6 : (age > 40 ? 1.2 : 1.0);
        double premium = base * ageLoading * (coverageLakhs / 5.0);
        return Map.of(
            "tier", tier.toUpperCase(),
            "age", age,
            "coverageLakhs", coverageLakhs,
            "annualPremiumInr", Math.round(premium),
            "validityDays", 365
        );
    }
}
