(ns roadfreightops.store
  "SSoT for the ISIC-4923 'Freight transport by road' road-freight
  DISPATCH/LOGISTICS-COORDINATION actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every
  `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office dispatch/logistics operations of
  a road-freight (trucking/cargo) carrier: cargo/manifest/delivery-record
  logging, truck/route/load dispatch scheduling, fleet-maintenance-order
  coordination with registered maintenance vendors, and load-safety/
  hazmat/hours-of-service concern flagging. It NEVER directly finalizes a
  load-safety clearance, a hazmat-transport authorization, or an
  hours-of-service waiver, and it never itself operates a vehicle or
  overrides a driver's or dispatcher's safety judgment -- see
  `roadfreightops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `carriers` directory keyed by `:carrier-id` STRING (the
  registered/verified trucking carrier/vehicle-driver unit under dispatch)
  and a `vendors` directory keyed by `:vendor-id` STRING (a registered/
  verified fleet-maintenance vendor), never keywords -- consistent keying
  from the start, avoiding the silent-miss bug that has plagued earlier
  sibling actors.

  A registered/verified carrier record (motor-carrier operating authority
  + insurance + vehicle-safety-inspection on file) must exist before ANY
  proposal targeting that carrier may ever commit or escalate --
  `roadfreightops.governor`'s `carrier-unverified-violations` re-derives
  this from the carrier's own `:registered?`/`:verified?` fields, never
  from a proposal's self-report. A `:coordinate-maintenance-order`
  proposal additionally names a registered maintenance vendor via its own
  `:vendor-id`; the SAME 'ground truth, not self-report' discipline
  applies via `vendor-unverified-violations`.

  The ledger stays append-only: which carrier a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (carrier-record [s carrier-id] "Registered carrier record, or nil.
    Carrier map: {:carrier-id .. :name .. :registered? bool :verified? bool}.")
  (all-carrier-records [s])
  (vendor-record [s vendor-id] "Registered maintenance-vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (dispatch-log [s] "the append-only committed dispatch/coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-carrier-records [s carriers] "replace/seed the carrier directory (map carrier-id->carrier)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained carrier/vendor directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:carriers
   {"carrier-1" {:carrier-id "carrier-1" :name "Riverside Freight Lines"
                 :registered? true :verified? true}
    "carrier-2" {:carrier-id "carrier-2" :name "Sunbelt Intermodal Trucking"
                 :registered? true :verified? true}
    "carrier-3" {:carrier-id "carrier-3" :name "Downtown Courier Fleet (operating authority in intake)"
                 :registered? true :verified? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Northgate Fleet Maintenance & Repair"
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Roadside Repair Co."
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (carrier-record [_ carrier-id] (get-in @a [:carriers carrier-id]))
  (all-carrier-records [_] (sort-by :carrier-id (vals (:carriers @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (dispatch-log [_] (:dispatch-log @a))
  (commit-record! [_ record]
    (swap! a update :dispatch-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-carrier-records [s carriers] (when (seq carriers) (swap! a assoc :carriers carriers)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo carrier/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :dispatch-log []))))

(defn mem-store
  "A MemStore seeded with explicit `carriers`/`vendors` maps (carrier-id/
  vendor-id string -> record map) -- the primary test/dev entry point.
  Either may be empty (an unregistered-everywhere carrier)."
  ([carriers] (mem-store carriers {}))
  ([carriers vendors]
   (->MemStore (atom {:carriers (or carriers {}) :vendors (or vendors {})
                       :ledger [] :dispatch-log []}))))
