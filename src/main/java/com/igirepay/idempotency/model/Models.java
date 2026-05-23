package com.igirepay.idempotency.model;

import org.springframework.http.ResponseEntity;
import java.util.concurrent.CountDownLatch;

public class Models {

    public record PaymentRequest(int amount, String currency) {}

    public record PaymentResponse(String message) {}

    public enum Status { PROCESSING, COMPLETED, FAILED }

    // This holds the state of a specific Idempotency Key
    public static class IdempotencyRecord {
        private final PaymentRequest requestBody;
        private volatile Status status;
        private volatile ResponseEntity<PaymentResponse> response;
        private final CountDownLatch latch;
        private final long createdAt;

        public IdempotencyRecord(PaymentRequest requestBody) {
            this.requestBody = requestBody;
            this.status = Status.PROCESSING;
            this.latch = new CountDownLatch(1);
            this.createdAt = System.currentTimeMillis();
        }

        public PaymentRequest getRequestBody() { return requestBody; }
        public Status getStatus() { return status; }
        public ResponseEntity<PaymentResponse> getResponse() { return response; }
        public long getCreatedAt() { return createdAt; }

        public void complete(ResponseEntity<PaymentResponse> response) {
            this.response = response;
            this.status = Status.COMPLETED;
            this.latch.countDown(); // Releases any waiting threads
        }

        public void fail() {
            this.status = Status.FAILED;
            this.latch.countDown();
        }

        public void awaitCompletion() throws InterruptedException {
            this.latch.await(); // Blocks until countDown() is called
        }
    }
}
