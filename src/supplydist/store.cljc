(ns supplydist.store
  "SSoT for the ISCO-08 1324 community supply, distribution & related
  managers actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md
  Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    sku    — a registered stock-keeping unit {:sku-id :client-id :name
             :on-hand number :approved-carriers #{carrier-str}}.
             `:on-hand` is the registered current stock level a
             proposed allocation must not exceed; `:approved-carriers`
             is the registered set a proposed shipment's carrier must
             belong to.
    record — a committed operating record (approved allocation) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (sku [s sku-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-sku! [s sk])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (sku [_ sku-id] (get-in @a [:skus sku-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-sku! [s sk]
    (swap! a assoc-in [:skus (:sku-id sk)] sk) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :skus {} :records [] :ledger []}
                                   seed)))))
