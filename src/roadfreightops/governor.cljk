(ns roadfreightops.governor
  "RoadFreightDispatchGovernor -- the independent compliance layer that
  earns the RoadFreightDispatchAdvisor the right to commit. The advisor
  has no notion of whether a carrier is actually registered and
  license-verified, whether a named maintenance vendor is itself a
  registered/verified counterparty, whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- DISPATCH/LOGISTICS
  SCHEDULING COORDINATION ONLY (cargo/manifest/delivery-record logging,
  truck/route/load dispatch scheduling, fleet-maintenance-order
  coordination, load-safety/hazmat/hours-of-service concern flagging). It
  NEVER performs or authorizes:
    - directly finalizing a load-safety-clearance decision
    - granting/issuing a hazmat-transport authorization
    - granting an hours-of-service waiver
    - operating a vehicle, or overriding a driver's or dispatcher's
      safety judgment

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Carrier unverified          -- the target carrier record must
                                       exist AND be independently
                                       confirmed `:registered?`/
                                       `:verified?` in the store before
                                       ANY proposal for it may commit or
                                       even escalate. Never trusts a
                                       proposal's own claim about the
                                       carrier -- re-derived from the
                                       carrier's own record, the same
                                       'ground truth, not self-report'
                                       discipline every sibling actor's
                                       governor uses.
    2. Vendor unverified           -- for `:coordinate-maintenance-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a `:vendor-id`
                                       that resolves to an independently
                                       `:registered?`/`:verified?`
                                       maintenance-vendor record. A
                                       missing vendor-id, or one that
                                       resolves to an unregistered or
                                       unverified vendor, is a HARD block.
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing a load-safety-
                                       clearance decision, granting/
                                       issuing a hazmat-transport
                                       authorization, granting an
                                       hours-of-service waiver, or
                                       overriding a driver's/dispatcher's
                                       safety judgment is a HARD,
                                       PERMANENT block -- this actor's
                                       charter excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-safety-concern`
                                       itself is never excluded by this
                                       check -- surfacing a load-
                                       securement/hazmat-placarding/
                                       hours-of-service concern for a
                                       human is exactly this actor's job;
                                       only FINALIZING/authorizing/
                                       waiving/overriding a safety
                                       decision is excluded (see
                                       `scope-excluded-terms` below --
                                       phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'safety', 'hazmat' or
                                       'hours-of-service', so the default
                                       mock advisor's own
                                       `:flag-safety-concern` rationale
                                       never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `roadfreightops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one. A 'flag a concern' op must
      always escalate and never be auto-commit-eligible.
    - A `:coordinate-maintenance-order` whose drafted `:value` names an
      `:estimated-cost` above `maintenance-cost-threshold` -- a
      large-value fleet-maintenance procurement proposal always needs a
      human sign-off, even when the governor and phase would otherwise
      allow auto-commit.

  This actor coordinates DISPATCH/LOGISTICS SCHEDULING ONLY -- it never
  directly operates a vehicle or overrides a driver's/dispatcher's
  safety judgment. No op in the closed allowlist finalizes a
  load-safety-clearance, hazmat-transport-authorization or
  hours-of-service-waiver decision; those are HARD, permanent blocks by
  construction (check 4 above), never auto-commit-eligible."
  (:require [kotoba.lang.text :as str]
            [roadfreightops.store :as store]))

(def confidence-floor 0.6)

(def maintenance-cost-threshold
  "Example single-carrier fleet-maintenance procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-maintenance-order` proposal
  citing an `:estimated-cost` above this value ALWAYS escalates to human
  sign-off, regardless of confidence or rollout phase."
  5000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). NOTE:
  by design, no op in this allowlist directly finalizes a load-safety
  clearance, a hazmat-transport authorization, or an hours-of-service
  waiver -- those decisions are permanently excluded by
  `scope-exclusion-violations` (check 4) instead, never merely
  un-implemented."
  #{:log-shipment-record :schedule-dispatch-operation
    :coordinate-maintenance-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not. A 'flag a
  concern' op must always escalate and must never be a member of any
  phase's `:auto` set (see `roadfreightops.phase`)."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  load-safety-clearance decision, granting/issuing a hazmat-transport
  authorization, granting an hours-of-service waiver, or overriding a
  driver's/dispatcher's safety judgment, rather than merely coordinating
  dispatch/logistics scheduling around it. Scanned across the proposal's
  op/summary/rationale/cites/value, never trusting the advisor's own
  framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'finalize the load safety clearance', 'grant the
  hours-of-service waiver'), never a bare noun like 'safety', 'hazmat',
  'clearance' or 'hours of service' -- a bare noun would accidentally
  match inside this actor's own legitimate `:flag-safety-concern`
  default proposal text (whose whole job is to talk about load-
  securement/hazmat-placarding/hours-of-service concerns) and self-block
  the happy path. See
  `roadfreightops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the load safety clearance" "finalized the load safety clearance"
   "finalizes the load safety clearance" "finalizing the load safety clearance"
   "clear the load for departure" "cleared the load for departure" "clearing the load for departure"
   "clear the shipment for departure" "cleared the shipment for departure" "clearing the shipment for departure"
   "grant the hazmat transport authorization" "granted the hazmat transport authorization"
   "granting the hazmat transport authorization"
   "issue the hazmat transport authorization" "issued the hazmat transport authorization"
   "issuing the hazmat transport authorization"
   "authorize the hazmat transport" "authorized the hazmat transport" "authorizing the hazmat transport"
   "approve the hazmat placarding" "approved the hazmat placarding" "approving the hazmat placarding"
   "grant the hours-of-service waiver" "granted the hours-of-service waiver" "granting the hours-of-service waiver"
   "waive the hours-of-service requirement" "waived the hours-of-service requirement"
   "waiving the hours-of-service requirement"
   "waive the driver's hours of service" "waived the driver's hours of service"
   "waiving the driver's hours of service"
   "override the driver's hours-of-service limit" "overrode the driver's hours-of-service limit"
   "overriding the driver's hours-of-service limit"
   "override the driver's rest requirement" "overrode the driver's rest requirement"
   "overriding the driver's rest requirement"
   "approve the overweight load waiver" "approved the overweight load waiver" "approving the overweight load waiver"
   "finalize the load securement clearance" "finalized the load securement clearance"
   "finalizing the load securement clearance"
   "finalize the hazmat transport authorization" "finalized the hazmat transport authorization"
   "finalizing the hazmat transport authorization"
   "finalize the hours-of-service waiver" "finalized the hours-of-service waiver"
   "finalizing the hours-of-service waiver"
   "override the dispatcher's safety judgment" "overrode the dispatcher's safety judgment"
   "overriding the dispatcher's safety judgment"
   "override the driver's safety judgment" "overrode the driver's safety judgment"
   "overriding the driver's safety judgment"
   "貨物安全証明を確定" "貨物安全証明を確定した"
   "積載安全確認を確定" "積載安全確認を確定した"
   "危険物輸送許可を発行" "危険物輸送許可を発行した"
   "危険物輸送許可を承認" "危険物輸送許可を承認した"
   "乗務時間規制の適用除外を承認" "乗務時間規制の適用除外を承認した"
   "労働時間規制の免除を承認" "労働時間規制の免除を承認した"])

;; ----------------------------- checks -----------------------------

(defn- carrier-unverified-violations
  "The target carrier must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:carrier-id` claim without a store lookup."
  [{:keys [carrier-id]} st]
  (let [c (store/carrier-record st carrier-id)]
    (when-not (and c (:registered? c) (:verified? c))
      [{:rule :carrier-unverified
        :detail (str carrier-id " は未登録または未検証のキャリア -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-maintenance-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` maintenance-vendor record. A missing
  vendor-id, or one that resolves to an unregistered or unverified
  vendor, is a HARD block -- never trust the proposal's own vendor claim
  without a store lookup, the SAME 'ground truth, not self-report'
  discipline as `carrier-unverified-violations`, reapplied to the
  maintenance counterparty."
  [proposal st]
  (when (= :coordinate-maintenance-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の整備業者 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a load-safety-clearance
  decision, granting/issuing a hazmat-transport authorization, granting
  an hours-of-service waiver, or overriding a driver's/dispatcher's
  safety judgment, regardless of confidence or how clean every other
  check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "積載安全証明の確定・危険物輸送許可の発行/承認・乗務時間規制の適用除外など安全確定行為(safety-clearance/hazmat-authorization/hours-of-service-waiver finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-maintenance-order?
  "A `:coordinate-maintenance-order` proposal citing an `:estimated-cost`
  above `maintenance-cost-threshold` -- always needs human sign-off
  (SOFT escalate, not a hard block: the order itself is in scope, only
  its size requires a human)."
  [proposal]
  (and (= :coordinate-maintenance-order (:op proposal))
       (some-> proposal :value :estimated-cost (> maintenance-cost-threshold))))

(defn check
  "Censors a RoadFreightDispatchAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [carrier-id (or (:carrier-id proposal) (:carrier-id request))
        hard (into []
                   (concat (carrier-unverified-violations {:carrier-id carrier-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-maintenance-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :carrier-id (:carrier-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
