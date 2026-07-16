(ns roadfreightops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean shipment-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a dispatch-operation-scheduling request and a
  low-cost maintenance-order coordination naming a verified vendor (both
  auto-commit clean at phase 3), then a high-cost maintenance order
  (ALWAYS escalates regardless of phase), then a safety-concern flag
  (ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered carrier, a carrier registered but
  not yet verified, a maintenance order naming an unverified vendor, a
  proposal whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded load-safety/hazmat/hours-of-
  service-finalization scope."
  (:require [langgraph.graph :as g]
            [roadfreightops.advisor :as advisor]
            [roadfreightops.store :as store]
            [roadfreightops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "dispatch-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :freight-dispatch-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :freight-dispatch-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-shipment-record carrier-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-shipment-record :carrier-id "carrier-1"
                                  :patch {:bill-of-lading "BOL-1001" :load-weight-kg 12000 :status "delivered"}} coordinator-phase-1)]
      (println r)
      (println "-- human dispatch coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-shipment-record carrier-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-shipment-record :carrier-id "carrier-1"
                                  :patch {:bill-of-lading "BOL-1002" :load-weight-kg 8000 :status "in-transit"}} coordinator-phase-3))

    (println "\n== schedule-dispatch-operation carrier-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-dispatch-operation :carrier-id "carrier-1"
                                  :patch {:route "I-80-westbound" :date "2026-07-20" :window "06:00-14:00"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order carrier-1, low cost, verified vendor (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                  :patch {:item "brake-inspection" :quantity 1 :estimated-cost 420.0
                                          :vendor-id "vendor-1"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order carrier-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                 :patch {:item "engine overhaul" :quantity 1 :estimated-cost 9200.0
                                         :vendor-id "vendor-1"}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-safety-concern carrier-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-safety-concern :carrier-id "carrier-1"
                                 :patch {:concern "load shift reported on trailer 4, cargo securement straps appear loose" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & approves --")
      (println (approve! actor "t6")))

    (println "\n== log-shipment-record carrier-99 (unregistered carrier -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-shipment-record :carrier-id "carrier-99"
                                  :patch {:bill-of-lading "BOL-9999"}} coordinator-phase-3))

    (println "\n== log-shipment-record carrier-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-shipment-record :carrier-id "carrier-3"
                                  :patch {:bill-of-lading "BOL-3001"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order carrier-1, vendor-2 unverified (-> HARD hold) ==")
    (println (exec-op actor "t9" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                  :patch {:item "tire replacement" :quantity 6 :estimated-cost 900.0
                                          :vendor-id "vendor-2"}} coordinator-phase-3))

    (println "\n== schedule-dispatch-operation carrier-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t10" {:op :schedule-dispatch-operation :carrier-id "carrier-1"
                                           :patch {:route "I-95-northbound" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-shipment-record carrier-1, advisor drifts into load-safety/hazmat-authorization-finalization scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-shipment-record :carrier-id "carrier-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed dispatch log ==")
    (doseq [r (store/dispatch-log db)] (println r))))
