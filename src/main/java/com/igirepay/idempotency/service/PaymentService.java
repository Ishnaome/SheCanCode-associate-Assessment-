package com.igirepay.idempotency.service;

import com.igirepay.idempotency.model.Models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled; 
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    // Thread-safe in-memory store
    private final Map<String, IdempotencyRecord> cache = new ConcurrentHashMap<>();

    public ResponseEntity<PaymentResponse> processPayment(String idempotencyKey, PaymentRequest request) {
        
        // Use computeIfAbsent to ensure atomicity. If the key isn't there, create it and mark as PROCESSING.
        IdempotencyRecord newRecord = new IdempotencyRecord(request);
        IdempotencyRecord existingRecord = cache.putIfAbsent(idempotencyKey, newRecord);

        if (existingRecord == null) {
            // WE ARE THE FIRST REQUEST (Happy Path)
            try {
                // Simulate processing delay
                Thread.sleep(2000); 
                
                PaymentResponse payload = new PaymentResponse("Charged " + request.amount() + " " + request.currency());
                ResponseEntity<PaymentResponse> response = ResponseEntity.status(HttpStatus.CREATED).body(payload);
                
                newRecord.complete(response);
                return response;
            } catch (Exception e) {
                newRecord.fail();
                cache.remove(idempotencyKey); // Clear on failure so they can retry
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed");
            }
        } else {
            // KEY ALREADY EXISTS (Duplicate Attempt or In-Flight or Fraud)
            
            // 1. Fraud Check: Did the payload change?
            if (!existingRecord.getRequestBody().equals(request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for a different request body.");
            }

            // 2. In-Flight Check: Is the first request still processing?
            if (existingRecord.getStatus() == Status.PROCESSING) {
                try {
                    existingRecord.awaitCompletion(); // Block until Request A finishes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while waiting for processing");
                }
            }

            // 3. Return Cached Response (with specific header)
            if (existingRecord.getStatus() == Status.COMPLETED) {
                return ResponseEntity.status(existingRecord.getResponse().getStatusCode())
                        .headers(existingRecord.getResponse().getHeaders())
                        .header("X-Cache-Hit", "true")
                        .body(existingRecord.getResponse().getBody());
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Previous request failed, please generate a new key and retry.");
            }
        }
    }

    // DEVELOPER'S CHOICE: Eviction Policy (Runs every hour)
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredKeys() {
        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        cache.entrySet().removeIf(entry -> entry.getValue().getCreatedAt() < twentyFourHoursAgo);
    }
}
// this is my payment service class for the idempotency gateway.