# Distributed Logistics & Supply-Chain Coordination Platform
## Architecture Baseline

**Document status:** Living architecture document  
**Version:** 0.1  
**Project phase:** Step 00 — Project Framing  
**Purpose:** Establish the architectural baseline, boundaries, principles, and intended evolution without jumping ahead to the final distributed/cloud-native architecture.

---

## 1. Architectural Goal

The project is not intended to demonstrate the maximum possible number of technologies. It is intended to show **why distributed information-system architecture becomes necessary and how middleware abstractions solve concrete problems**.

The architecture therefore evolves incrementally:

```text
Business requirements
      -> observed problem
      -> architectural alternatives
      -> explicit trade-off
      -> decision
      -> implementation
      -> failure experiment
      -> revised architecture
```

No major infrastructure component should be introduced only because it is fashionable or because it appears later in the final target architecture.

---

## 2. Architectural Drivers

The strongest drivers are:

1. **Distributed coordination** across warehouses, carriers, suppliers, payment providers, and regulatory systems.
2. **Transactional correctness** for operations such as inventory reservation and order acceptance.
3. **Long-running workflows** where physical and external operations cannot be rolled back by one ACID transaction.
4. **Autonomous external systems** with independent availability, data models, interfaces, and release cycles.
5. **Asynchronous event flow**, especially tracking and operational events.
6. **Failure recovery** after crashes, timeouts, duplicate delivery, and partial business-process completion.
7. **Scalability and availability** as order and tracking load increases.
8. **Operational and analytical coexistence** without allowing analytics to damage transaction-processing performance.
9. **Heterogeneous information integration** across incompatible sources.
10. **Explainability and observability** across distributed execution.

---

## 3. Logical Information-System View

The project uses the course's conceptual separation between:

- **Presentation (P)** — user/system interaction;
- **Application Logic (AL)** — business functions and process coordination;
- **Resource Management (RM)** — databases, files, queues, and autonomous external systems/resources.

Initial logical view:

```text
+----------------------------------------------------+
| Presentation                                       |
| Customer UI / Operator UI / External API boundary  |
+--------------------------+-------------------------+
                           |
                           v
+----------------------------------------------------+
| Application Logic                                  |
| Orders | Inventory | Fulfillment | Shipment        |
| Tracking | Billing | Notification | Integration    |
+--------------------------+-------------------------+
                           |
                           v
+----------------------------------------------------+
| Resource Management                                |
| Operational DB(s) | External Systems | later MQ    |
| later cache | later analytical stores              |
+----------------------------------------------------+
```

These are **logical layers**, not necessarily separate deployment units.

---

## 4. System Context

```text
                       +------------------+
                       |     Customer     |
                       +--------+---------+
                                |
                                v
                  +---------------------------+
                  | Logistics Coordination    |
                  | Platform                  |
                  +----+----+----+----+-------+
                       |    |    |    |
          +------------+    |    |    +----------------+
          |                 |    |                     |
          v                 v    v                     v
   +-------------+   +-----------+-----------+   +------------+
   | Warehouse   |   | Carrier Systems       |   | Payment /  |
   | Systems     |   |                       |   | Billing    |
   +-------------+   +-----------------------+   +------------+
          |                    |
          v                    v
   +-------------+      +--------------+
   | Supplier    |      | Customs /    |
   | Systems     |      | Regulatory   |
   +-------------+      +--------------+
```

The platform is responsible for **coordination**, not for replacing all participants.

---

## 5. Initial Internal Business Modules

The following are initially **modules/capabilities**, not separate services:

```text
customer
order
inventory
fulfillment
warehouse
shipment
tracking
billing
notification
integration
reporting
```

### Why modules first?

The project needs clean domain boundaries without prematurely introducing network boundaries. Keeping business modules explicit makes later distribution decisions observable: when a local method call becomes a remote interaction, we will be able to identify exactly what changed in latency, failure modes, transaction scope, deployment, and observability.

---

## 6. Initial Deployment Intent

The exact first deployable architecture is finalized in the Chapter 2 step. The **current intended baseline** is deliberately conservative:

```text
Browser / simple client
        |
        v
+----------------------------+
| Application                |
| API + business modules     |
+-------------+--------------+
              |
              v
      +---------------+
      | Relational DB |
      +---------------+
```

External systems may initially be represented by local simulators/stubs behind explicit integration adapters.

This baseline is not the final architecture. It exists to provide something simple enough that later distribution has a clear cost and benefit.

---

## 7. Initial Domain Boundaries

### Customer

Owns customer-facing identity and delivery/contact information used by the platform.

### Order

Owns customer order intent and order lifecycle.

### Inventory

Owns the platform's concept of stock availability and reservation state.

### Fulfillment

Coordinates how an accepted order becomes prepared and delivered. It may later become the home of long-running workflow state.

### Warehouse

Represents communication with warehouse operations. Internal warehouse execution may belong to autonomous external systems.

### Shipment

Owns the platform's shipment concept, package association, carrier booking references, and shipment lifecycle.

### Tracking

Accepts and interprets external tracking observations and maintains shipment event history/current projection.

### Billing

Owns the platform's invoice/payment coordination records. It does not replace the external payment provider.

### Notification

Produces customer-visible notifications. Notification delivery is not assumed to be transactionally atomic with the business state that triggered it.

### Integration

Contains adapters/mappings needed to communicate with autonomous external systems without allowing their representations to leak directly into core domain modules.

### Reporting

Initially provides simple operational views. Later it will be separated conceptually and physically from OLTP workloads.

---

## 8. Dependency Direction

Core application/domain logic should not depend directly on external transport or vendor representations.

Preferred conceptual direction:

```text
External protocol / vendor model
          |
          v
   Integration Adapter
          |
          v
 Platform Domain Model
          |
          v
 Application Use Case
```

Examples of external details that should be translated at the boundary:

- carrier-specific shipment codes;
- warehouse-specific product identifiers;
- SOAP/REST/file transport details;
- supplier-specific schemas;
- provider-specific error codes.

This is important later when studying wrappers, schema mapping, and heterogeneity.

---

## 9. Data Architecture Baseline

### Operational state

Initially, the project may use one relational operational store while maintaining clear logical ownership inside the application.

This allows early study of:

- transactions;
- isolation;
- concurrency;
- JDBC;
- ORM;
- connection pooling;
- schema evolution;
- query behavior.

A shared initial database does **not** imply that all later services must share a database.

### External state

External systems remain sources of truth for their native data. The platform may store:

- correlation identifiers;
- integration status;
- local snapshots;
- normalized copies;
- derived projections;
- audit records.

### Analytical state

No warehouse is required initially. Analytical storage is introduced later when the materialized-integration chapters create a reason for it.

---

## 10. Communication Baseline

### Internal communication

Initial module-to-module calls should be local/in-process wherever possible.

### External communication

External systems are always treated as remote/autonomous even if implemented by simulators during development.

Every external interaction should eventually consider:

- timeout;
- retry;
- uncertain outcome;
- authentication;
- versioning;
- correlation identifier;
- duplicate handling;
- observability.

### Asynchronous communication

Do not introduce a message broker in Generation 0/1 simply to create an event-driven architecture. Messaging will be introduced when Chapter 7 provides the conceptual and operational motivation.

---

## 11. Transaction Boundary Baseline

Early versions may use local database transactions for business operations whose state is entirely under one resource manager.

A key learning rule is:

> A local transaction ends where autonomous remote effects begin unless a deliberate distributed transaction mechanism is introduced.

Examples:

- updating order + reservation in one local DB transaction may be atomic initially;
- calling a carrier or payment provider cannot be assumed to roll back when the local DB transaction rolls back;
- sending email/SMS is not undoable by database rollback;
- physical warehouse work is not undoable by ordinary ACID rollback.

These mismatches will later motivate messaging, idempotency, recovery, workflow state, compensation, and distributed transaction discussions.

---

## 12. Failure Model

The architecture assumes failures are normal.

At minimum, later implementations must be tested against:

- process crash;
- remote timeout;
- remote system unavailable;
- uncertain remote outcome;
- duplicate request/message;
- out-of-order event;
- database outage or pool exhaustion;
- broker outage;
- slow consumer/backlog;
- lost application instance;
- incompatible/conflicting external data.

Architecture diagrams should eventually identify which component detects, masks, retries, records, or escalates each class of failure.

---

## 13. Architecture Evolution Map

This project intentionally evolves through generations rather than starting in the final form.

### Generation 0 — Domain and requirements

No implementation architecture beyond context and logical boundaries.

### Generation 1 — Layered / 3-tier application

Goal: learn logical layers vs deployment tiers and establish a clean application baseline.

### Generation 2 — Explicit persistence and DB middleware

Goal: understand database gateway/persistence abstractions, connections, transactions, and ORM behavior.

### Generation 3 — First remote/distributed interaction

Goal: convert a local interaction into RPC and observe serialization, binding, latency, timeout, and failure uncertainty.

### Generation 4 — Transaction-aware middleware

Goal: reason about application-server responsibilities, transaction demarcation, security, pooling, and concurrency.

### Generation 5 — Asynchronous messaging

Goal: introduce durable queues/events, retry, redelivery, idempotency, and DB/message consistency problems.

### Generation 6 — Service-oriented external interfaces

Goal: reason about service contracts, REST/SOAP-style interfaces, loose coupling, and interoperability.

### Generation 7 — Long-running workflow

Goal: implement process state, retry, compensation, orchestration/choreography, and recovery across multiple transactions.

### Generation 8 — Complete web information system

Goal: session/security/web concerns and internet-facing architecture.

### Generation 9 — Scalable and highly available system

Goal: caching, pooling, stateless replicas, load balancing, partitioning, replication, and relaxed consistency where appropriate.

### Generation 10 — Enterprise/heterogeneous integration

Goal: integrate autonomous carrier/warehouse/supplier systems with deliberately different interfaces and schemas.

### Generation 11 — Virtual data integration

Goal: wrappers/mediators/federated access to heterogeneous sources.

### Generation 12 — Materialized analytical integration

Goal: ETL/staging/warehouse/OLAP and data-quality handling.

### Generation 13 — Explicit integration methods

Goal: schema matching, mapping, transformation planning, entity resolution, and deployment of mappings.

### Generation 14 — Operational capstone

Goal: containerized, observable, CI/CD-driven, Kubernetes-hosted system with documented guarantees and failure experiments.

---

## 14. Planned Engineering Gates

Engineering tools are introduced when they solve a real project problem.

### Docker gate

Introduced after the application and database have enough deployment complexity to justify reproducible environments.

### CI gate

Introduced once multiple components/tests create a meaningful need for automated build and verification.

### Kubernetes gate

Introduced after scaling/high-availability concepts have established concrete requirements for replicas, service discovery, health management, and rolling deployment.

### Continuous Delivery gate

Introduced once Kubernetes or another realistic deployment target exists.

### Observability

Logs are required from the beginning at a basic level; metrics and distributed tracing become explicit once the architecture has multiple processes and cross-component requests.

---

## 15. Architecture Principles

1. **Business requirements before tools.**
2. **One evolving system, not disconnected demos.**
3. **Modularity before distribution.**
4. **A network boundary must have a reason.**
5. **External systems are autonomous by default.**
6. **Failures are part of normal execution.**
7. **Retries require explicit duplicate/uncertain-outcome reasoning.**
8. **Transactions must have explicit boundaries.**
9. **Derived state must have an identifiable source of truth.**
10. **Analytical workloads should not silently become operational dependencies.**
11. **Every major architecture change requires an ADR.**
12. **Every distributed guarantee must name its assumptions.**

---

## 16. Documentation Expectations

As the architecture evolves, maintain:

- context diagram;
- container/process diagram;
- component/module diagram;
- deployment diagram;
- sequence diagrams for critical interactions;
- BPMN/process diagrams for long-running workflows;
- data ownership/source-of-truth table;
- ADRs;
- experiment reports for failure/performance investigations.

---

## 17. Current Open Architecture Questions

These are intentionally unresolved until the relevant steps:

- Which business module should be the first separately deployed component?
- Which interaction should demonstrate REST vs gRPC/RPC concepts?
- Where should strong consistency remain mandatory?
- Which operations benefit from asynchronous queues?
- Which long-running process should be orchestrated vs event-driven?
- Which external systems should expose REST, SOAP, database, and file interfaces?
- When should operational data be replicated or partitioned?
- Which analytical views belong in a warehouse vs virtual federation?
- What should be cached and what staleness is acceptable?
- Which components need multiple replicas?

---

## 18. Current Architecture Summary

Version 0.1 deliberately contains **fewer components than the final project**.

The starting point is:

```text
Users / Operators
       |
       v
Presentation / API
       |
       v
Modular Application Logic
       |
       +------------------------+
       |                        |
       v                        v
Operational Data        External-system adapters
                                |
                                v
                    Simulated autonomous systems
```

This architecture is expected to become insufficient. Discovering **why** it becomes insufficient is part of the course.
