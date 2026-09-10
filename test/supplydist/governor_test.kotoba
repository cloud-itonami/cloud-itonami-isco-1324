(ns supplydist.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [supplydist.store :as store]
            [supplydist.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-sku! st {:sku-id "SKU-1" :client-id "client-1"
                             :name "widget" :on-hand 500
                             :approved-carriers #{"DHL" "FedEx"}})
    st))

(defn- allocate [qty carrier]
  {:op :approve-allocation :effect :propose :sku-id "SKU-1"
   :quantity qty :carrier carrier :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-stock-and-approved-carrier
  (let [st (fresh-store)
        v (governor/check req {} (allocate 300 "DHL") st)]
    (is (:ok? v))))

(deftest ok-at-exact-on-hand
  (testing "allocation exactly equal to stock is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (allocate 500 "DHL") st)]
      (is (:ok? v)))))

(deftest hard-on-insufficient-stock
  (testing "you cannot allocate what does not exist"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (allocate 700 "DHL") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :insufficient-stock (:rule %)) (:violations v))))))

(deftest hard-on-unapproved-carrier
  (let [st (fresh-store)
        v (governor/check req {} (allocate 300 "RandomCourier") st)]
    (is (:hard? v))
    (is (some #(= :unapproved-carrier (:rule %)) (:violations v)))))

(deftest hard-on-unknown-sku
  (let [st (fresh-store)
        v (governor/check req {} (assoc (allocate 300 "DHL") :sku-id "SKU-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-sku (:rule %)) (:violations v)))))

(deftest hard-on-foreign-sku
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (allocate 300 "DHL") st)]
      (is (:hard? v))
      (is (some #(= :sku-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (allocate 300 "DHL") st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (allocate 300 "DHL") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-cross-border-shipment
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-cross-border-shipment :effect :propose
                                  :sku-id "SKU-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (allocate 300 "DHL") :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
