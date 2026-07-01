package com.kushal.policypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UserProfile(
    @Min(18) @Max(100) int age,
    @NotBlank String maritalStatus,           // "single" | "married" | "divorced" | "widowed"
    @Min(0)  @Max(10) int dependents,
    @Min(0)  @Max(500) int incomeLakhs,
    @NotBlank String riskAppetite             // "low" | "medium" | "high"
) {}
