<p align="center">
  <img src="docs/logo.png" alt="unwind" width="160" />
</p>

<h1 align="center">unwind</h1>

<p align="center">
  <strong>A distributed transaction that knows how to reverse itself.</strong>
</p>

<p align="center">
  An orchestration-based saga in Spring Boot and RabbitMQ — coordinating a multi-step money transfer across independent services, and automatically unwinding the completed steps when any one of them fails.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3" />
  <img src="https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white" alt="RabbitMQ" />
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white" alt="React" />
  <img src="https://img.shields.io/badge/Tailwind-v4-38BDF8?logo=tailwindcss&logoColor=white" alt="Tailwind v4" />
  <img src="https://img.shields.io/badge/License-MIT-black" alt="MIT" />
</p>

<p align="center">
  <a href="#the-problem">Problem</a> ·
  <a href="#how-it-works">How it works</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#run-it">Run it</a> ·
  <a href="#design-decisions">Decisions</a>
</p>

---

## The problem

Moving money from one account to another sounds like a single operation. Across a microservice system, it isn't. It is at least three: debit the sender, credit the recipient, write the ledger entry — each owned by a different service, each with its own database. There is no transaction that spans all three. So the moment the credit succeeds but the ledger write fails, you are left with money debited from one account, credited to another, and no record of why — an inconsistency a naive design will simply leave behind.

**unwind** is a working answer to that failure. It implements the saga pattern with a central orchestrator that walks the transfer forward one step at a time and, the instant a step fails, walks it *backward* — issuing the inverse of every step that already committed, in reverse order, until the system is whole again. The failure paths are not an edge case bolted on at the end; they are the entire point of the project.

A live React monitor makes the abstraction concrete: each transfer is a thread that draws forward as it settles, and visibly retracts when it unwinds.

## How it works

A transfer of \$100 from **A** to **B** runs as an ordered saga. Each forward step has a compensating inverse:

| # | Forward step | Service | Compensating step |
| :-: | --- | --- | --- |
| 1 | Debit A | account-service | Refund A |
| 2 | Credit B | account-service | Reverse credit B |
| 3 | Record ledger | ledger-service | — (final step, nothing to undo) |

The orchestrator sends step 1, waits for the reply, sends step 2, and so on. If step 2 fails, it compensates step 1 (refund A). If step 3 fails, it compensates steps 2 and 1 in reverse (reverse the credit to B, then refund A). Because compensation always runs in the reverse of the order things happened, the system lands back in a consistent state regardless of where the failure struck.

Every saga is a persisted state machine:

| State | Meaning |
| --- | --- |
| `STARTED` | Saga created, debit dispatched |
| `DEBITED` | Sender debited, crediting recipient |
| `CREDITED` | Recipient credited, writing ledger |
| `COMPLETED` | All steps succeeded |
| `COMPENSATING` | A step failed; inverse steps in progress |
| `FAILED` | Fully unwound; system consistent |

Because the state is written to PostgreSQL at every transition, the saga's progress is durable and auditable — you can always ask exactly where a transfer is and what it has done.

## Architecture

The orchestrator never calls a service directly. It publishes a **command** to RabbitMQ; the target service acts and publishes a **reply event**; the orchestrator advances its state machine on that reply. Services share no database tables — consistency across them is maintained entirely by the saga.

| Module | Responsibility |
| --- | --- |
| **orchestrator** | The coordinator. Owns the persisted state machine, dispatches commands, runs compensation logic, exposes the REST API, and pushes live updates over WebSocket. |
| **account-service** | Owns account balances. Executes debit and credit, plus their compensations (refund, reverse). |
| **ledger-service** | Records completed transfers. |
| **common** | The shared vocabulary — command and event contracts every module speaks. |
| **frontend** | React monitor. Starts transfers, injects failures per step, and renders each saga as a thread that advances and unwinds in real time. |

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Messaging | RabbitMQ via `spring-amqp` (command / reply) |
| Persistence | PostgreSQL via Spring Data JPA, one schema per service |
| Realtime | Raw WebSocket (`spring-boot-starter-websocket`) |
| Frontend | React + Vite + TypeScript + Tailwind CSS v4 |
| Build | Multi-module Maven |
| Infrastructure | Docker Compose |

## Run it

**Prerequisites:** Java 21, Node 20+, and Docker.

Start the infrastructure, build the backend, run each service, and launch the monitor:

```bash
# 1. RabbitMQ + PostgreSQL
docker compose up -d

# 2. Build all modules
./mvnw clean install

# 3. Run the services (each in its own terminal)
./mvnw -pl account-service spring-boot:run
./mvnw -pl ledger-service spring-boot:run
./mvnw -pl orchestrator spring-boot:run

# 4. Run the monitor
cd frontend && npm install && npm run dev
```

Open the monitor at **http://localhost:5173**. Send a transfer and watch the thread settle. Then set **Inject failure** to *at credit* or *at ledger* and watch the same transfer unwind — the completed steps retracting in reverse as the orchestrator compensates.

| Surface | URL |
| --- | --- |
| Live monitor | http://localhost:5173 |
| Orchestrator API + WebSocket | http://localhost:8080 |
| RabbitMQ management | http://localhost:15672 |

Accounts `acct-A` (1000.00) and `acct-B` (500.00) are seeded automatically on startup.

## Design decisions

**Orchestration over choreography.** In choreography, services react to one another's events with no central coordinator — elegant for simple flows, but the transaction logic scatters across services and no single place knows the overall state. Orchestration keeps the saga's lifecycle in one legible place, which is what makes the pattern teachable and debuggable. Choreography is a natural second implementation.

**A hand-rolled state machine, not a library.** A money-transfer saga is a short linear sequence with a reverse compensation path. A persisted status enum plus a decision function expresses it exactly, and keeps the logic visible in the codebase rather than hidden behind a framework's abstractions.

**Asynchronous messaging, not synchronous REST between services.** Commands travel over RabbitMQ, so a briefly-offline participant does not fail the transfer — the command waits in its queue. This mirrors how real financial systems coordinate, and makes the saga naturally resilient to restarts.

## Known simplifications

Deliberate scope choices, each with a well-understood production path.

- **Balances are illustrative** — enough to demonstrate the saga, not a real banking ledger (no double-entry accounting, currencies, or interest).
- **A single orchestrator instance** owns each saga. Running several would require distributed locking on saga rows.
- **Compensations are assumed to succeed** — the demo does not model a failing compensation, which a production system handles with retries and a manual-intervention queue.
- **At-least-once delivery** — a failed message is rejected and dropped rather than deduplicated against a persisted message ID, which a production consumer would add for full idempotency.

## Project status

Complete and working end to end: the happy path settles, single-step failures compensate, and multi-step failures unwind in reverse order — all visible live in the monitor.

## License

Released under the MIT License. See [`LICENSE`](./LICENSE).