# Operator Guide

## First Deployment
1. Register operator, carriers and maintenance vendors; independently
   confirm each carrier's motor-carrier operating authority/insurance/
   vehicle-safety-inspection status and each vendor's registration
   before seeding `roadfreightops.store`.
2. Import existing shipment/manifest, dispatch and maintenance-order
   history.
3. Run read-only shipment-record-logging and dispatch-scheduling
   dry-runs (Phase 0-1).
4. Configure the rollout phase and the `coordinate-maintenance-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run safety-concern flag and audit export.

## Minimum Production Controls
- carrier-registration/verification check before ANY proposal for that
  carrier
- vendor-registration/verification check before ANY `:coordinate-
  maintenance-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-safety-concern` (always) and high-cost
  `:coordinate-maintenance-order` proposals
- audit export for every commit, hold and approval
- backup manual dispatch process

## Certification
Certified operators must prove carrier/vendor-verification discipline,
governor-bypass resistance, evidence-backed safety-concern reporting and
human review for every escalation-gated action. This actor never
directly operates a vehicle or overrides a driver's/dispatcher's safety
judgment -- certified operators must preserve that boundary in any
downstream integration.
