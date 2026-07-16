# Business Model: Road-Freight Dispatch/Logistics Operations Coordination

## Classification
- Repository: `cloud-itonami-isic-4923`
- ISIC Rev.5: `4923` -- freight transport by road (trucking/cargo
  carriers moving goods over public roads)
- Social impact: supply-chain resilience, driver safety, transparency

## Customer
- independent trucking carriers needing an auditable dispatch/logistics
  coordination platform
- multi-carrier fleet operators needing consistent dispatch/maintenance/
  safety-concern governance across terminals
- programs that cannot accept closed, unauditable back-office platforms

## Offer
- cargo/manifest/delivery-record logging
- truck/route/load dispatch-scheduling coordination
- fleet-maintenance-order coordination with registered, verified vendors
- load-safety/hazmat/hours-of-service concern flagging for human triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per carrier/terminal
- support retainer with SLA

## Trust Controls
- `:road-freight-dispatch-governor` never lets a proposal for an
  unregistered/unverified carrier, or a maintenance order naming an
  unregistered/unverified vendor, commit or even escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a load-safety clearance, granting/issuing a
  hazmat-transport authorization, or granting an hours-of-service waiver
  is permanently out of scope, not a rollout milestone -- the actor may
  only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost `:coordinate-
  maintenance-order`, always require human sign-off
- this actor coordinates DISPATCH/LOGISTICS SCHEDULING ONLY -- it never
  directly operates a vehicle or overrides a driver's/dispatcher's
  safety judgment
- sensitive carrier, driver and shipper data stays outside Git
