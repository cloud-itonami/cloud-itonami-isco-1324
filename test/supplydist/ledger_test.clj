(ns supplydist.ledger-test
  "The ledger's job is to say what authorised each write. These tests
  ask it to fail, and pin the REASON it gives — an assertion that only
  sees a throw counts a refusal for the wrong reason as a refusal for
  the right one, which is how a negative test goes quietly green.

  Every ill-formed case below is `good` with exactly ONE key changed,
  so the reason reported is the reason under test."
  (:require [clojure.test :refer [deftest is testing]]
            [supplydist.actor :as actor]
            [supplydist.ledger :as ledger]
            [supplydist.store :as store]))

(def ^:private good
  {:disposition :commit
   :authorisation :governor-clear
   :record {:client-id "client-1" :op :approve-allocation :sku-id "SKU-1"}})

(defn- fault-of
  "The :fault ex-data of the throw `entry` raises, or nil if it did not
  throw. Returning nil rather than throwing keeps `did not refuse`
  distinguishable from `refused for another reason`."
  [m]
  (try (ledger/entry m) nil
       (catch clojure.lang.ExceptionInfo e (:fault (ex-data e)))))

(deftest builds-a-well-formed-entry
  (let [e (ledger/entry good)]
    (is (= :commit (:disposition e)))
    (is (= :governor-clear (ledger/authorisation-of e)))
    (is (false? (ledger/human-signed? e)))))

(deftest refuses-an-entry-that-does-not-name-its-authorisation
  (testing "the whole point: a write with no stated authority"
    (let [f (fault-of (dissoc good :authorisation))]
      (is (some? f) "must refuse")
      (is (re-find #":authorisation が無い" f)
          (str "must refuse FOR THE MISSING AUTHORISATION, got: " f)))))

(deftest refuses-an-unknown-authorisation
  (let [f (fault-of (assoc good :authorisation :looked-fine-to-me))]
    (is (some? f) "must refuse")
    (is (re-find #":authorisation が無い、または未知" f)
        (str "must name the unknown authorisation, got: " f))))

(deftest refuses-a-commit-authorised-by-a-hold
  (let [f (fault-of (assoc good :authorisation :governor-hold))]
    (is (some? f) "must refuse")
    (is (re-find #":commit を :governor-hold が許可することはない" f)
        (str "must name the contradiction, got: " f))))

(deftest refuses-a-hold-claiming-human-sign-off
  (testing "a refusal nobody was asked to sign cannot be recorded as signed"
    (let [f (fault-of (assoc good
                             :disposition :hold
                             :authorisation :human-sign-off
                             :verdict {:hard? true}))]
      (is (some? f) "must refuse")
      (is (re-find #":hold の :authorisation は :governor-hold のみ" f)
          (str "must name the hold/authorisation mismatch, got: " f)))))

(deftest refuses-a-commit-with-no-record
  (let [f (fault-of (dissoc good :record))]
    (is (some? f) "must refuse")
    (is (re-find #":commit には :record が要る" f)
        (str "must name the missing record, got: " f))))

(deftest refuses-a-hold-with-no-reason
  (testing "a hold whose reason is absent is not an audit record"
    (let [f (fault-of (-> good
                          (assoc :disposition :hold
                                 :authorisation :governor-hold)
                          (dissoc :record)))]
      (is (some? f) "must refuse")
      (is (re-find #":hold には拒否理由としての :verdict が要る" f)
          (str "must name the missing verdict, got: " f)))))

(deftest refuses-an-unknown-disposition
  (let [f (fault-of (assoc good :disposition :probably-fine))]
    (is (some? f) "must refuse")
    (is (re-find #":disposition は :commit か :hold" f)
        (str "must name the bad disposition, got: " f))))

;; ---------------------------------------------------------------- actor

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-sku! st {:sku-id "SKU-1" :client-id "client-1"
                             :name "widget" :on-hand 500
                             :approved-carriers #{"DHL" "FedEx"}})
    st))

(deftest ledger-distinguishes-the-two-ways-a-commit-is-authorised
  (testing "measured on 4c4f48f as identical; they must not be identical now"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})]
      ;; governor-clear: ordinary in-stock, approved-carrier allocation
      (actor/run-request! graph
                          {:client-id "client-1" :op :approve-allocation :stake :low
                           :sku-id "SKU-1" :quantity 300 :carrier "DHL"}
                          {} "thread-clear")
      ;; human-sign-off: cross-border escalates, interrupts, human resumes
      (actor/run-request! graph
                          {:client-id "client-1" :op :approve-cross-border-shipment
                           :stake :low :sku-id "SKU-1" :quantity 10 :carrier "DHL"}
                          {} "thread-signed")
      (actor/approve! graph "thread-signed")
      (let [entries (store/ledger st)]
        (is (= 2 (count entries)) "two writes")
        (is (= [:governor-clear :human-sign-off] (map ledger/authorisation-of entries))
            "the cross-border write must be recorded as human-signed")
        (is (= [false true] (map ledger/human-signed? entries)))))))

(deftest a-held-proposal-records-the-refusal-and-its-reason
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})]
    (actor/run-request! graph
                        {:client-id "client-1" :op :approve-allocation :stake :low
                         :sku-id "SKU-1" :quantity 900 :carrier "DHL"}
                        {} "thread-hold")
    (let [e (first (store/ledger st))]
      (is (= :hold (:disposition e)))
      (is (= :governor-hold (ledger/authorisation-of e)))
      (is (false? (ledger/human-signed? e)))
      (is (= [:insufficient-stock] (map :rule (get-in e [:verdict :violations])))
          "the ledger must carry WHY it was refused, not merely THAT it was"))))
