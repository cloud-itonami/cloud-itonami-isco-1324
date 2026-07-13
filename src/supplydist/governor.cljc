(ns supplydist.governor
  "SupplyDistributionManagersGovernor — the independent safety/
  traceability layer for the ISCO-08 1324 community supply,
  distribution & related managers actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Logistics twist: a
  proposed allocation quantity is arithmetic comparison against the
  registered on-hand stock (you cannot allocate what does not exist),
  and a proposed carrier is either a member of the registered
  approved-carriers set or it is not — carrier approval is
  traceability, not a shipping preference.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. sku basis          — an allocation must cite a REGISTERED sku
                           belonging to this client.
    4. stock arithmetic   — the proposed allocation quantity must not
                           exceed the sku's registered :on-hand stock.
    5. carrier membership — the proposed carrier must be a member of
                           the sku's registered :approved-carriers set
                           (no invented or unapproved carrier).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-cross-border-shipment (customs/regulatory
                           exposure).
    7. low confidence (< `confidence-floor`)."
  (:require [supplydist.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record sk]
  (let [{:keys [op quantity carrier]} proposal
        allocate? (= :approve-allocation op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and allocate? (nil? sk))
      (conj {:rule :unknown-sku :detail "未登録 sku への引当承認は不可"})

      (and allocate? sk (not= (:client-id sk) (:client-id request)))
      (conj {:rule :sku-wrong-client :detail "sku が別 client のもの"})

      (and allocate? sk (number? quantity) (> quantity (:on-hand sk)))
      (conj {:rule :insufficient-stock
             :detail (str "引当数量 " quantity " > 在庫 " (:on-hand sk)
                          "（存在しない在庫は引当できない）")})

      (and allocate? sk carrier (not (contains? (:approved-carriers sk) carrier)))
      (conj {:rule :unapproved-carrier
             :detail (str "キャリア " carrier " は登録済み承認集合 "
                          (:approved-carriers sk) " の外（キャリア承認は追跡性であって配送の好みではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `supplydist.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        sk (some->> (:sku-id proposal) (store/sku store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record sk)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-cross-border-shipment (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
