package contracts.payment

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should initialize a payment and return paymentId + redirectUrl"

    request {
        method POST()
        url "/payments/init"
        headers {
            contentType applicationJson()
        }
        body(
                orderId: $(anyUuid()),
                amount: 99.99,
                email: "buyer@example.com",
                name: "John Doe"
        )
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                paymentId: $(anyUuid()),
                redirectUrl: $(anyNonBlankString())
        )
    }
}
