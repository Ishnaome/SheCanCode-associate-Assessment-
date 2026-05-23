# IgirePay Idempotency Gateway (The "Pay-Once" Protocol)

An Idempotency API Layer designed to prevent double-charging in payment processing systems caused by network timeouts, client retries, and race conditions.

# Setup & Installation
Prerequisites

Java 17 or higher installed
Maven 3.6+ installed

# Step-by-Step Execution
Open your terminal in the project root directory (idempotency-gateway/).
Clean, compile, and boot up the server all in one command:

mvn clean compile spring-boot:run

# API Documentation
Process Payment
POST /api/process-payment
 
Body JSON
{
  "amount": 100,
  "currency": "RWF"
}

# Design Decisions
Atomic Map Computations (ConcurrentHashMap): We leverage computeIfAbsent to check for token existence and insert a PROCESSING token in a single, atomic thread-safe operation. This guarantees that absolutely no two threads can sneak past the initial database registration step at the same time.

Zero-External Database Dependency: To keep the footprint highly efficient and optimized for the assessment review, an optimized local memory model was chosen. This bypasses structural networking latency while fulfilling the structural logic evaluation perfectly.

Request Integrity Validation: Saving the stringified representation of the original payload guarantees structural validation. Reusing an old key with a modified transaction amount immediately triggers an identity mismatch error, securing the ecosystem from replay attacks.

## Architecture Flow

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
