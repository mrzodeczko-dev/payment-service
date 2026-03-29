package com.rzodeczko.paymentservice.domain.model;

public enum PaymentStatus {
    PENDING, //Utworzona oczekuje  na realizacje
    PAID, //płatność potwierdzona
    FAILED //płatność odrzucona
}
