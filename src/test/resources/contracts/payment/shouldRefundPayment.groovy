package contracts.payment

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should refund a payment and return 200 OK"

    request {
        method POST()
        url "/payments/${value(consumer(anyUuid()), producer('e4d12c3a-1234-5678-9abc-def012345678'))}/refund"
    }

    response {
        status OK()
    }
}
