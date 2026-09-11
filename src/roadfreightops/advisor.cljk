(ns roadfreightops.advisor
  "RoadFreightDispatchAdvisor -- the *contained intelligence node* for the
  ISIC-4923 'Freight transport by road' (trucking/cargo) DISPATCH/
  LOGISTICS-SCHEDULING operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: cargo/manifest/delivery-record logging, truck/route/load
  dispatch scheduling, fleet-maintenance-order coordination, and load-
  safety/hazmat/hours-of-service concern flagging. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale +
  the fields it cited), never a committed record and NEVER a direct
  actuation -- every proposal's `:effect` is always `:propose`. Every
  output is censored downstream by `roadfreightops.governor` before
  anything touches the SSoT.

  This advisor NEVER drafts a decision that finalizes a load-safety
  clearance, grants a hazmat-transport authorization, or grants an
  hours-of-service waiver, and it never directly operates a vehicle or
  overrides a driver's/dispatcher's safety judgment -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `roadfreightops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised or
  confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :carrier-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-shipment-record
  "Draft a cargo/manifest/delivery-record log entry. Pure logging of
  observed shipment data (bill-of-lading reference, pickup/delivery
  timestamps, load weight, cargo description) -- never a load-safety-
  clearance decision."
  [_db {:keys [carrier-id patch]}]
  {:op         :log-shipment-record
   :carrier-id carrier-id
   :summary    (str carrier-id " の貨物マニフェスト/配送記録を記録: " (pr-str (keys patch)))
   :rationale  "積込・配送・受領の観察記録のみ。"
   :cites      [carrier-id]
   :effect     :propose
   :value      (merge {:carrier-id carrier-id} patch)
   :confidence 0.93})

(defn- propose-dispatch-operation
  "Draft a truck/route/load dispatch scheduling proposal (a roster/route
  assignment entry, never a direct vehicle-operation or safety-clearance
  decision)."
  [_db {:keys [carrier-id patch]}]
  {:op         :schedule-dispatch-operation
   :carrier-id carrier-id
   :summary    (str carrier-id " のトラック/ルート/積載の配車予定を提案: " (pr-str (keys patch)))
   :rationale  "車両・ルート・積載の配車調整提案のみ。最終的な配車確定は人間が行う。"
   :cites      [carrier-id]
   :effect     :propose
   :value      (merge {:carrier-id carrier-id} patch)
   :confidence 0.88})

(defn- propose-maintenance-order
  "Draft a fleet-maintenance procurement coordination request naming a
  registered maintenance vendor -- never a finalized purchase order; a
  human always confirms procurement."
  [_db {:keys [carrier-id patch]}]
  {:op         :coordinate-maintenance-order
   :carrier-id carrier-id
   :summary    (str carrier-id " 向け車両整備の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "車両整備・部品調達の発注調整提案のみ。確定発注は人間が行う。"
   :cites      [carrier-id]
   :effect     :propose
   :value      (merge {:carrier-id carrier-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed load-securement/hazmat-placarding/hours-of-service
  concern for HUMAN triage. This op ALWAYS escalates in
  `roadfreightops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real.
  Deliberately reports the OBSERVATION only, never a finalization/
  authorization/waiver action, so the default rationale never trips the
  governor's `scope-excluded-terms` (see that var's docstring). This actor
  coordinates DISPATCH/LOGISTICS SCHEDULING ONLY -- it never directly
  operates a vehicle or overrides a driver's/dispatcher's safety
  judgment, and this op only ever flags a concern; it never resolves one."
  [_db {:keys [carrier-id patch]}]
  {:op         :flag-safety-concern
   :carrier-id carrier-id
   :summary    (str carrier-id " の安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "積載固定不良・危険物表示不備・乗務時間超過の懸念の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [carrier-id]
   :effect     :propose
   :value      (merge {:carrier-id carrier-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-shipment-record (propose-shipment-record _db request)
                   :schedule-dispatch-operation (propose-dispatch-operation _db request)
                   :coordinate-maintenance-order (propose-maintenance-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually cleared the load for departure and authorized the hazmat transport without review")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t          :advisor-proposal
   :op         (:op proposal)
   :carrier-id (:carrier-id proposal)
   :summary    (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
