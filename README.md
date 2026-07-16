# cloud-itonami-isic-4923

Open Business Blueprint for **ISIC Rev.5 4923**: freight transport by
road -- trucking/cargo carriers moving goods over public roads.

This repository publishes a road-freight (trucking) dispatch/logistics
**operations-COORDINATION** actor -- cargo/manifest/delivery-record
logging, truck/route/load dispatch scheduling, fleet-maintenance-order
coordination with registered vendors, and load-safety/hazmat/hours-of-
service concern flagging -- as an OSS business that any qualified
operator can fork, deploy, run, improve and sell, so an independent
trucking carrier never surrenders its dispatch/logistics data to a
closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **RoadFreightDispatchAdvisor
⊣ RoadFreightDispatchGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:road-freight-dispatch-governor`,
is a distinct, independently-named build (checked against this fleet's
own governor-keyword registry before finalizing; distinct from sibling
ISIC 4920's own `:community-freight-transport`-shaped governance and
from passenger-transport siblings ISIC 4921/4922 being built concurrently
in this same batch).

> **Why an actor layer at all?** An LLM is great at drafting a shipment-
> record summary, a dispatch-scheduling proposal, or a maintenance-order
> request -- but it has no license to actually finalize a load-safety
> clearance, grant a hazmat-transport authorization, or grant an
> hours-of-service waiver, no way to independently confirm a carrier or
> a maintenance-order vendor is actually a registered/verified
> counterparty, and no notion of when a "flag this concern" op quietly
> turns into a claim to have already resolved it. Letting it act
> directly invites an unverified carrier's data entering the ledger, an
> unverified vendor receiving a maintenance order, or -- worst of all --
> a fabricated claim to have cleared an unsafe load for departure or
> waived a driver's rest requirement, exposing the carrier, its drivers
> and the public to real liability. This project seals the
> RoadFreightDispatchAdvisor into a single node and wraps it with an
> independent **RoadFreightDispatchGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: dispatch/logistics scheduling coordination only, not safety authority

This actor coordinates **DISPATCH/LOGISTICS SCHEDULING ONLY**. It never
performs or authorizes, and never directly operates a vehicle or
overrides a driver's or dispatcher's safety judgment:

- directly finalizing a load-safety-clearance decision
- granting/issuing a hazmat-transport authorization
- granting an hours-of-service waiver

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a load-safety/
hazmat/hours-of-service concern for a human to triage is exactly this
actor's job -- `:flag-safety-concern` is never excluded by this check,
only FINALIZING/authorizing/waiving/overriding that decision is. A "flag
a concern" op always escalates and is never a member of any phase's
`:auto` set.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`roadfreightops.governor`'s `effect-not-propose-violations` HARD check
and `roadfreightops.phase`'s phase table, which never puts
`:flag-safety-concern` in any phase's `:auto` set). A human dispatcher/
logistics coordinator is always the one who actually acts on a flagged
concern or confirms a high-cost maintenance order.

## The core contract

```
carrier/vendor registration + dispatch/logistics coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ RoadFreightDispatch-  │ ─────────────▶ │ RoadFreightDispatchGovernor  │  (independent system)
   │ Advisor (sealed)      │  + citations    │ carrier-unverified ·        │
   └───────────────────────┘                 │ vendor-unverified ·         │
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (load-safety-        │
    record + ledger        escalate ┼ clearance / hazmat-authorization /  │
          │              (ALWAYS for│ hours-of-service-waiver             │
          │       :flag-safety-     │ finalization) · op-not-allowed      │
          │       concern/high-cost └────────────────────────────┘
          │       maintenance order
          ▼
      human approval
```

**The RoadFreightDispatchAdvisor never commits a proposal the
RoadFreightDispatchGovernor would reject, and a safety-concern flag or a
high-cost maintenance order never commits without a human sign-off.**
Hard violations (an unregistered/unverified carrier; an unregistered/
unverified maintenance vendor; a non-`:propose` effect; content touching
load-safety/hazmat-authorization/hours-of-service-waiver finalization;
an op outside the closed allowlist) force **hold** and *cannot* be
approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: yard-tractor spotting,
automated loading-dock handling) under human/robot dispatch operations
gated by carrier policy. This actor itself does not dispatch robot/
hardware actions -- it is strictly the dispatch/logistics-coordination
layer (shipment-record logging, dispatch scheduling, maintenance-order
coordination, safety-concern flagging) any physical-dispatch layer could
eventually feed proposals into, always gated the same way by the
independent RoadFreightDispatchGovernor.

## Features

- **Closed proposal-op allowlist**: `log-shipment-record`,
  `schedule-dispatch-operation`, `coordinate-maintenance-order`,
  `flag-safety-concern` (all `:effect :propose`). By design, no op in
  this allowlist finalizes a load-safety clearance, a hazmat-transport
  authorization, or an hours-of-service waiver -- those decisions are
  permanently excluded, never merely un-implemented.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Carrier unverified** -- the target carrier's motor-carrier
     operating authority must exist AND be independently registered/
     verified in the store.
  2. **Vendor unverified** -- for `:coordinate-maintenance-order` only,
     the named maintenance vendor must exist AND be independently
     registered/verified.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a load-safety-clearance
     decision, granting/issuing a hazmat-transport authorization,
     granting an hours-of-service waiver, overriding a driver's/
     dispatcher's safety judgment, and an op outside the closed
     allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a load-safety/hazmat/hours-of-service decision
    itself -- it only surfaces the concern for a human.
  - `:coordinate-maintenance-order` above a cost threshold -- a
    large-value fleet-maintenance procurement proposal always needs a
    human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: shipment-record logging only (approval-gated)
  - Phase 2: + dispatch-operation scheduling, maintenance-order
    proposals (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost maintenance orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/roadfreightops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/roadfreightops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/roadfreightops/phase_test.clj` -- rollout phase logic
- `test/roadfreightops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/roadfreightops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `roadfreightops.store` -- SSoT (MemStore, String-keyed carrier/vendor
  directories, append-only ledger)
- `roadfreightops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `roadfreightops.governor` -- independent compliance layer
- `roadfreightops.phase` -- staged rollout (0→3)
- `roadfreightops.operation` -- langgraph-clj StateGraph
- `roadfreightops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4923`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Cargo/manifest/delivery-record logging (`:log-shipment-record`) | Real TMS/telematics-system integration |
| Truck/route/load dispatch-scheduling coordination (`:schedule-dispatch-operation`) | Direct vehicle/ELD (electronic-logging-device) integration |
| Fleet-maintenance-order coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-maintenance-order`) | Real supplier/parts-ordering-system integration |
| Load-safety/hazmat/hours-of-service concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing a load-safety clearance, a hazmat-transport authorization, or an hours-of-service waiver -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Real-time GPS/telemetry tracking -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a proof-of-delivery
or a detention-time-billing check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `RoadFreightDispatchAdvisor` + `RoadFreightDispatchGovernor`
run as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own cargo-safety
+ driver-hours-of-service scope-exclusion design.

## License

Code and implementation templates are AGPL-3.0-or-later.
