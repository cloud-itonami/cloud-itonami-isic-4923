(ns roadfreightops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout): this repo previously had NO demo page and no generator
  at all. This namespace drives the REAL actor stack
  (`roadfreightops.operation` -> `roadfreightops.governor` ->
  `roadfreightops.store`) through a scenario adapted from this repo's own
  `roadfreightops.sim` demo driver (`clojure -M:run`, confirmed to run
  correctly against the real seeded carrier/vendor directory before this
  file was written), trimmed to a representative subset at phase 3
  (supervised-auto) and rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive
  runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [roadfreightops.store :as store]
            [roadfreightops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :freight-dispatch-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: carrier-1 clears a shipment-record log (auto-
  commit, clean), a dispatch-operation schedule (auto-commit, clean),
  and a low-cost maintenance-order coordination naming verified
  vendor-1 (auto-commit, clean); carrier-1's high-cost maintenance
  order and its safety-concern flag both ALWAYS escalate to a human --
  approved in both cases, so carrier-1's own last recorded status is a
  clean approval, not a hold. carrier-99 (does not exist) HARD-holds on
  `:carrier-unverified`; carrier-3 (registered but operating authority
  not yet verified) HARD-holds on the same rule, showing the distinct
  'unregistered' vs 'registered-but-unverified' ground states; a
  maintenance-order against carrier-1 naming vendor-2 (registered but
  not verified) HARD-holds on `:vendor-unverified`. Every HARD hold
  never reaches a human. Returns the resulting store -- every field
  read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :log-shipment-record :carrier-id "carrier-1"
                       :patch {:bill-of-lading "BOL-1001" :load-weight-kg 12000
                               :status "delivered"}})

    (exec! actor "t2" {:op :schedule-dispatch-operation :carrier-id "carrier-1"
                       :patch {:route "I-80-westbound" :date "2026-07-20"
                               :window "06:00-14:00"}})

    (exec! actor "t3" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                       :patch {:item "brake-inspection" :quantity 1
                               :estimated-cost 420.0 :vendor-id "vendor-1"}})

    (exec! actor "t4" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                       :patch {:item "engine overhaul" :quantity 1
                               :estimated-cost 9200.0 :vendor-id "vendor-1"}})
    (approve! actor "t4")

    (exec! actor "t5" {:op :flag-safety-concern :carrier-id "carrier-1"
                       :patch {:concern "load shift reported on trailer 4, cargo securement straps appear loose"
                               :confidence 0.92}})
    (approve! actor "t5")

    (exec! actor "t6" {:op :log-shipment-record :carrier-id "carrier-99"
                       :patch {:bill-of-lading "BOL-9999"}})

    (exec! actor "t7" {:op :log-shipment-record :carrier-id "carrier-3"
                       :patch {:bill-of-lading "BOL-3001"}})

    (exec! actor "t8" {:op :coordinate-maintenance-order :carrier-id "carrier-1"
                       :patch {:item "tire replacement" :quantity 6
                               :estimated-cost 900.0 :vendor-id "vendor-2"}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger carrier-id]
  (last (filter #(= (:carrier-id %) carrier-id) ledger)))

(defn- status-cell [ledger carrier-id]
  (let [f (last-fact-for ledger carrier-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- bool-cell [v] (if v "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>"))

(defn- carrier-row [ledger {:keys [carrier-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc carrier-id) (esc name) (bool-cell registered?) (bool-cell verified?)
          (status-cell ledger carrier-id)))

(defn- vendor-row [{:keys [vendor-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vendor-id) (esc name) (bool-cell registered?) (bool-cell verified?)))

(defn- ledger-row [{:keys [t op carrier-id basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc (or carrier-id ""))
          (esc (or (some->> basis (map name) (str/join ", ")) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README Features /
  ;; Ops, `roadfreightops.governor`/`roadfreightops.phase`) -- documentation
  ;; of fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-shipment-record</code></td><td><span class=\"ok\">auto-commit when clean, phase 3</span></td></tr>"
   "        <tr><td><code>:schedule-dispatch-operation</code></td><td><span class=\"ok\">auto-commit when clean &middot; carrier independently verified</span></td></tr>"
   "        <tr><td><code>:coordinate-maintenance-order</code></td><td><span class=\"warn\">auto-commit below cost threshold &middot; ALWAYS human approval above it &middot; vendor independently verified</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        carriers (store/all-carrier-records db)
        vendors (store/all-vendor-records db)
        carrier-rows (str/join "\n" (map (partial carrier-row ledger) carriers))
        vendor-rows (str/join "\n" (map vendor-row vendors))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4923 &middot; road freight dispatch</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Freight transport by road (ISIC 4923) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · load-safety/hazmat/hours-of-service finalization always excluded</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Carriers</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>roadfreightops.store</code> via <code>roadfreightops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Carrier</th><th>Name</th><th>Registered</th><th>Verified</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     carrier-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Maintenance vendors</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Vendor</th><th>Name</th><th>Registered</th><th>Verified</th></tr></thead>\n"
     "      <tbody>\n"
     vendor-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Road Freight Dispatch Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Carrier/vendor status is independently re-derived from the store, never trusted from a proposal. Directly finalizing a load-safety clearance, granting a hazmat-transport authorization, or granting an hours-of-service waiver is permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Carrier</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        parent (.getParentFile (java.io.File. out))
        _ (when parent (.mkdirs parent))
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/dispatch-log db)) "dispatch-log entries )")))
