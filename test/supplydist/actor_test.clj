(ns supplydist.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [supplydist.actor :as actor]
            [supplydist.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-sku! st {:sku-id "SKU-1" :client-id "client-1"
                             :name "widget" :on-hand 500
                             :approved-carriers #{"DHL" "FedEx"}})
    st))

(deftest commits-an-in-stock-approved-carrier-allocation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-allocation :stake :low
                 :sku-id "SKU-1" :quantity 300 :carrier "DHL"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-stock-allocation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-allocation :stake :low
                 :sku-id "SKU-1" :quantity 900 :carrier "DHL"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-ships-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-cross-border-shipment :stake :high
                 :sku-id "SKU-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
