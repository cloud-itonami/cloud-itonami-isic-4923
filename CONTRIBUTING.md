# Contributing

`cloud-itonami-isic-4923` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real carrier, driver, shipper or safety-incident data.
- Keep shipment-record logging, dispatch-operation scheduling,
  maintenance-order coordination and safety-concern flagging behind the
  RoadFreightDispatchGovernor.
- Treat road-freight dispatch/logistics workflows as high-risk: add
  tests for carrier/vendor verification, effect discipline, scope
  exclusion, escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "safety", "hazmat", "hours of service") -- phrase it as the
  finalization/execution ACTION (e.g. "finalize the load safety
  clearance", "grant the hours-of-service waiver"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `roadfreightops.governor/scope-excluded-terms`'s docstring.
- Never add an op to the closed allowlist that directly finalizes a
  load-safety clearance, a hazmat-transport authorization, or an
  hours-of-service waiver -- those decisions are permanently out of
  scope for this actor, enforced structurally by
  `scope-exclusion-violations`, not merely by omission.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
