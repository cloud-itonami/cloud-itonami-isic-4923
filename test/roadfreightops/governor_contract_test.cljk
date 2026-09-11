(ns roadfreightops.governor-contract-test
  "Integration tests: full OperationActor graph exercising the governor's
  hard checks, escalation logic, and audit trail."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [roadfreightops.advisor :as advisor]
            [roadfreightops.store :as store]
            [roadfreightops.operation :as op]))

(defn exec-request [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn resume-approval [actor tid status]
  (g/run* actor {:approval {:status status :by "coordinator"}} {:thread-id tid :resume? true}))

(deftest shipment-record-logging-full-flow
  (testing "clean shipment-record proposal -> auto-commit at phase 3"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-1" :phase 3}
          result (exec-request actor "t1"
                               {:op :log-shipment-record :carrier-id "carrier-1" :patch {:bill-of-lading "BOL-1001"}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/ledger db)) 0)
          "commit must append audit facts to ledger")
      (is (> (count (store/dispatch-log db)) 0)
          "commit must append record to dispatch-log"))))

(deftest safety-concern-always-escalates
  (testing ":flag-safety-concern escalates for human approval, regardless of phase/confidence"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2" :phase 3}
          result (exec-request actor "t2"
                               {:op :flag-safety-concern :carrier-id "carrier-1"
                                :patch {:concern "load shift on trailer 4" :confidence 0.99}}
                               ctx)]
      (is (some? result))
      ;; At this point the actor is paused for approval, not yet committed
      (is (= 0 (count (store/dispatch-log db)))
          "safety concern must not auto-commit, must wait for approval")
      ;; Now approve it
      (resume-approval actor "t2" :approved)
      (is (> (count (store/dispatch-log db)) 0)
          "after approval, record must be committed"))))

(deftest high-cost-maintenance-order-always-escalates
  (testing "a high-cost :coordinate-maintenance-order escalates for human approval, even at phase 3 clean"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2b" :phase 3}
          result (exec-request actor "t2b"
                               {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                :patch {:item "engine overhaul" :quantity 1 :estimated-cost 9200.0
                                        :vendor-id "vendor-1"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/dispatch-log db)))
          "high-cost maintenance order must not auto-commit, must wait for approval")
      (resume-approval actor "t2b" :approved)
      (is (> (count (store/dispatch-log db)) 0)
          "after approval, record must be committed"))))

(deftest low-cost-maintenance-order-auto-commits
  (testing "a low-cost :coordinate-maintenance-order naming a verified vendor auto-commits at phase 3 when clean"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2c" :phase 3}
          result (exec-request actor "t2c"
                               {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                :patch {:item "brake-inspection" :quantity 1 :estimated-cost 420.0
                                        :vendor-id "vendor-1"}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/dispatch-log db)) 0)
          "low-cost maintenance order must auto-commit when clean at phase 3"))))

(deftest unregistered-carrier-hard-hold
  (testing "unregistered carrier -> permanent HARD hold, never escalates"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-3" :phase 3}]
      (exec-request actor "t3"
                     {:op :log-shipment-record :carrier-id "unknown-carrier"
                      :patch {:bill-of-lading "BOL-0000"}}
                     ctx)
      (is (= 0 (count (store/dispatch-log db)))
          "HARD hold must never commit"))))

(deftest unverified-carrier-hard-hold
  (testing "registered but unverified carrier -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4" :phase 3}
          result (exec-request actor "t4"
                               {:op :log-shipment-record :carrier-id "carrier-3"
                                :patch {:bill-of-lading "BOL-3001"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/dispatch-log db)))
          "unverified carrier must HARD hold"))))

(deftest unverified-vendor-maintenance-order-hard-hold
  (testing "a maintenance-order naming an unverified vendor -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4b" :phase 3}
          result (exec-request actor "t4b"
                               {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                                :patch {:item "tire replacement" :quantity 6 :estimated-cost 900.0
                                        :vendor-id "vendor-2"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/dispatch-log db)))
          "unverified vendor must HARD hold"))))

(deftest effect-not-propose-hard-hold
  (testing "proposal with :effect :commit (not :propose) -> hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req) :effect :commit)))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-5" :phase 3}
          result (exec-request actor "t5"
                               {:op :log-shipment-record :carrier-id "carrier-1"
                                :patch {:bill-of-lading "BOL-1010"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/dispatch-log db)))
          "non-:propose effect must HARD hold"))))

(deftest scope-excluded-content-hard-hold
  (testing "proposal drifting into load-safety/hazmat-authorization/hours-of-service-finalization scope -> permanent hard hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-6" :phase 3}
          result (exec-request actor "t6"
                               {:op :log-shipment-record :carrier-id "carrier-1"
                                :out-of-scope? true  ; triggers scope pollution in advisor
                                :patch {}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/dispatch-log db)))
          "scope-excluded content must HARD hold"))))

(deftest phase-1-approval-gate
  (testing "phase 1 approved request -> commits after human approval"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-7" :phase 1}]
      (exec-request actor "t7"
                     {:op :log-shipment-record :carrier-id "carrier-1"
                      :patch {:bill-of-lading "BOL-1020"}}
                     ctx)
      (is (= 0 (count (store/dispatch-log db)))
          "phase 1 must not auto-commit, requires approval")
      (resume-approval actor "t7" :approved)
      (is (> (count (store/dispatch-log db)) 0)
          "after approval, must commit")
      (is (some #(= :committed (:t %)) (store/ledger db))
          "committed fact must be logged after approval"))))

(deftest audit-trail-completeness
  (testing "every decision leaves immutable audit facts"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-8" :phase 3}]
      (exec-request actor "t8a"
                     {:op :log-shipment-record :carrier-id "carrier-1" :patch {:bill-of-lading "BOL-1030"}}
                     ctx)
      (exec-request actor "t8b"
                     {:op :log-shipment-record :carrier-id "unknown" :patch {:bill-of-lading "BOL-1040"}}
                     ctx)
      (let [ledger (store/ledger db)]
        (is (> (count ledger) 0))
        (is (some #(= :committed (:t %)) ledger)
            "successful commits must be logged")
        (is (some #(= :governor-hold (:t %)) ledger)
            "HARD holds must be logged")))))
