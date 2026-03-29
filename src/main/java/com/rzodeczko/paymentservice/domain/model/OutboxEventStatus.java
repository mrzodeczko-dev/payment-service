package com.rzodeczko.paymentservice.domain.model;

public enum OutboxEventStatus {
    PENDING, //czeka na wysłanie
    SENT, // wyslany do seriwsu zlecajacego płatnosci
    FAILED // przekroczono limit prob - wymmaga interwencji
}
