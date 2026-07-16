# Governance

`cloud-itonami-isic-4923` is an OSS open-business blueprint for
road-freight (trucking) dispatch/logistics-coordination operations
(ISIC Rev.5 4923 -- freight transport by road).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered carrier, or a maintenance
  order naming an unverified/unregistered vendor, can never commit.
- the RoadFreightDispatchGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, load-safety-clearance/
  hazmat-authorization/hours-of-service-waiver-finalization content, an
  op outside the closed allowlist) cannot be overridden by human
  approval.
- every shipment-record log, dispatch-operation schedule, maintenance-
  order coordination and safety-concern flag is auditable.
- this actor never directly operates a vehicle or overrides a driver's
  or dispatcher's safety judgment.
- carrier, driver and shipper data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing shipment-record, dispatch-scheduling, maintenance-order or
  safety-concern policy checks
- mishandling carrier, driver or shipper data
- misrepresenting certification status
- failing to respond to security or safety incidents
