# Initial Architecture Decision Records
## Distributed Logistics & Supply-Chain Coordination Platform

**Version:** 0.1  
**Project phase:** Step 00 — Project Framing

This file contains the initial ADRs only. As the project grows, each ADR may be moved into its own file under `docs/adr/`.

---

# ADR-001 — Use One Evolving System Across the Course

**Status:** Accepted  
**Date:** 2026-09-13

## Context

The course covers multiple middleware and information-system concepts: layered architectures, database access, persistence, RPC, transactions, transactional middleware, messaging, web services, workflows, scalability/high availability, and heterogeneous data integration.

Studying each topic through an unrelated toy project would make it difficult to understand how architectural mechanisms interact and why a real system evolves.

## Decision

Use one **Distributed Logistics & Supply-Chain Coordination Platform** throughout the learning plan. Evolve the same system through multiple architectural generations.

## Consequences

### Positive

- Architectural changes have visible motivation.
- Later mechanisms can be compared with earlier designs.
- Cross-cutting concerns such as transactions, failures, observability, and deployment remain connected.
- The final project tells one coherent engineering story.

### Negative

- The codebase and documentation will become more complex over time.
- Earlier architectural assumptions may need refactoring.
- Careful scope control is necessary.

---

# ADR-002 — Start Modular, Not With Microservices

**Status:** Accepted  
**Date:** 2026-09-13

## Context

The final system will eventually contain distributed components, but beginning with many microservices would introduce deployment, network, data ownership, observability, and consistency problems before the course reaches the concepts needed to reason about them.

A local module boundary and a network boundary are not the same thing.

## Decision

Begin with a **modular application** with explicit business modules and a layered/3-tier-oriented design. Keep most business interactions in-process initially.

Potential modules include:

- customer;
- order;
- inventory;
- fulfillment;
- warehouse;
- shipment;
- tracking;
- billing;
- notification;
- integration;
- reporting.

Physical service boundaries will be introduced later only when a concrete learning objective or system requirement justifies them.

## Consequences

### Positive

- Business boundaries can be learned without premature distributed complexity.
- When a module becomes remote, the new failure/latency/transaction costs are directly observable.
- Easier initial development, testing, and debugging.

### Negative

- The first system is intentionally less distributed than the final target.
- Later extraction of components may require refactoring.

---

# ADR-003 — Treat External Organizations as Autonomous Systems

**Status:** Accepted  
**Date:** 2026-09-13

## Context

The later course chapters focus strongly on distribution, autonomy, heterogeneity, wrappers, federation, ETL, and schema integration. If every participating component uses the platform's internal data model and technology, those problems become artificial.

Real logistics coordination naturally involves independently owned carriers, warehouses, suppliers, payment providers, and regulatory systems.

## Decision

Model important external parties as **autonomous systems** from the beginning, even when they are initially implemented as local simulators.

The platform must not assume control over their:

- availability;
- protocol;
- schema;
- identifiers;
- transaction boundaries;
- release schedule;
- internal storage.

Integration adapters will isolate external representations from the platform domain model.

## Consequences

### Positive

- Provides a natural foundation for Chapters 12–15.
- Forces explicit treatment of timeouts, uncertain outcomes, correlation, mapping, and data ownership.
- Prevents vendor/external schemas from leaking into core domain logic.

### Negative

- Test fixtures/simulators require additional effort.
- Integration code will intentionally contain heterogeneity rather than being completely uniform.

---

# ADR-004 — Add Infrastructure Only After the Problem Is Established

**Status:** Accepted  
**Date:** 2026-09-13

## Context

The learning goals include Docker, messaging, CI/CD, observability, Kubernetes, caching, and other engineering tools. Introducing all of them at project start would make the architecture complex before their purpose is understood.

## Decision

Adopt the following decision sequence for major infrastructure additions:

```text
Requirement / observed problem
        -> alternatives
        -> trade-offs
        -> ADR
        -> technology choice
        -> implementation
        -> failure/performance experiment
```

Examples:

- do not add a broker until asynchronous processing/delivery problems are studied;
- do not add Redis until a concrete caching/performance problem exists;
- do not add Kubernetes until replicas, service discovery, health management, and rolling deployment are meaningful requirements.

## Consequences

### Positive

- Technology choices remain explainable.
- Avoids architecture-by-checklist.
- Makes each course chapter responsible for a visible architectural transition.

### Negative

- Some technologies will appear later than in typical cloud-native tutorials.
- Temporary/simple solutions will sometimes be replaced deliberately.

---

# ADR-005 — Keep the Domain Specification Technology-Neutral

**Status:** Accepted  
**Date:** 2026-09-13

## Context

The project needs a stable description of what the logistics system must accomplish while its implementation architecture changes repeatedly.

If business requirements mention concrete technologies, later architecture experiments become constrained by accidental early choices.

## Decision

`SYSTEM_SPEC.md` defines:

- actors;
- business capabilities;
- processes;
- functional requirements;
- invariants;
- failure expectations;
- external systems;
- quality requirements.

It does **not** mandate technologies such as Spring, Kafka/RabbitMQ, Redis, Docker, or Kubernetes.

Technology choices belong in `ARCHITECTURE.md` and ADRs.

## Consequences

### Positive

- Stable business baseline across architectural generations.
- Clear separation of requirements from solutions.
- Enables meaningful comparison of alternative middleware mechanisms.

### Negative

- Some details must be repeated or cross-referenced between specification and architecture documentation.

---

# ADR-006 — Make Failure Experiments Part of the Architecture Process

**Status:** Accepted  
**Date:** 2026-09-13

## Context

A distributed information system can appear correct on the happy path while failing under retry, crash, concurrency, timeout, duplicate delivery, or partial completion.

The course's transaction, messaging, workflow, and high-availability concepts are best understood through observed failure behavior.

## Decision

For each major distributed mechanism, define at least one explicit failure experiment documenting:

1. hypothesis;
2. setup;
3. injected failure;
4. observed state/logs/messages;
5. actual guarantee;
6. recovery behavior;
7. architectural conclusion.

Typical experiments will include:

- concurrent inventory reservation;
- RPC timeout with uncertain outcome;
- duplicate message delivery;
- consumer crash before acknowledgement;
- database failure;
- broker outage;
- partial workflow failure and compensation;
- instance failure behind a load balancer.

## Consequences

### Positive

- Guarantees are learned empirically as well as theoretically.
- Failure handling becomes a first-class architectural concern.
- Produces strong project documentation and interview material.

### Negative

- More engineering effort than happy-path feature development alone.
- Requires deterministic test fixtures and observability as the system grows.

---

# ADR-007 — Keep External-System Simulators Deliberately Different

**Status:** Proposed; activate during the integration chapters  
**Date:** 2026-09-13

## Context

If all simulated external systems expose identical REST/JSON APIs with matching schemas, the project will not meaningfully exercise technical, syntactic, structural, schematic, or semantic heterogeneity.

## Decision

When Chapters 12–15 are reached, external systems will deliberately differ. A likely teaching configuration is:

```text
Carrier A        REST + JSON
Carrier B        SOAP + XML
Warehouse A      API / relational representation
Warehouse B      legacy file/batch exchange
Supplier         autonomous ERP-like schema
```

Exact technologies remain deferred until those chapters.

## Consequences

### Positive

- Creates genuine integration work rather than artificial adapters.
- Supports wrapper, mediator, mapping, federation, ETL, and data-cleaning exercises.

### Negative

- More implementation work than using one uniform protocol.
- Some technologies may be intentionally old/awkward because heterogeneity is the lesson.
