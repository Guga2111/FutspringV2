package com.futspring.backend.domain.subscription.dto;

import com.futspring.backend.domain.subscription.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

/** "Já fiz o Pix": the plan the creator paid. */
public record PixClaimRequestDTO(@NotNull(message = "Escolha o plano") SubscriptionPlan plan) {}
