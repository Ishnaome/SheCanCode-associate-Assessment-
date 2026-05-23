# IgirePay Idempotency Gateway (The "Pay-Once" Protocol)

An Idempotency API Layer designed to prevent double-charging in payment processing systems caused by network timeouts, client retries, and race conditions.

## 1. Architecture Flow

This system uses a thread-safe, in-memory architecture to handle concurrent requests and guarantee absolute single-execution processing.

```mermaid
sequenceDiagram
    participant Client
    participant API as Gateway Controller
    participant Service as Idempotency Service
    participant Store as ConcurrentHashMap
    
    Client->>API: POST /process-payment (Key: 123)
    API->>Service: Handle Request
    Service->>Store: computeIfAbsent(123)
    
    alt Key Does Not Exist (Happy Path)
        Store-->>Service: Create new RECORD (Status: PROCESSING)
        Service->>Service: Simulate 2s payment processing
        Service->>Store: Update RECORD (Status: COMPLETED, save Response)
        Service-->>API: 201 Created
        API-->>Client: { "message": "Charged 100 RWF" }
        
    else Key Exists & Processing (In-Flight Race Condition)
        Client->>API: POST /process-payment (Key: 123) [Concurrent]
        Store-->>Service: Return existing RECORD
        Service->>Service: Check payload match
        Service->>Service: Block thread (latch.await()) until Request A completes
        Service-->>API: Return cached response + X-Cache-Hit
        API-->>Client: 201 Created (X-Cache-Hit: true)
        
    else Key Exists & Completed (Standard Retry)
        Client->>API: POST /process-payment (Key: 123) [Later]
        Store-->>Service: Return existing RECORD
        Service->>Service: Check payload match
        Service-->>API: Return cached response + X-Cache-Hit
        API-->>Client: 201 Created (X-Cache-Hit: true)
        
    else Key Exists & Payload Mismatch (Fraud/Error)
        Client->>API: POST /process-payment (Key: 123, diff body)
        Store-->>Service: Return existing RECORD
        Service->>Service: Detect Payload mismatch
        Service-->>API: 409 Conflict
        API-->>Client: 409 Conflict
    end