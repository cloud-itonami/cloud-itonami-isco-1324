(ns supplydist.advisor
  "SupplyDistributionManagersAdvisor — proposes an allocation
  operation (approve an allocation, approve a cross-border shipment)
  for a registered organization. Swappable mock/llm; the advisor ONLY
  proposes — `supplydist.governor` checks the stock arithmetic and
  carrier membership independently. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-allocation|:approve-cross-border-shipment
               :effect :propose :sku-id str :quantity number
               :carrier str :stake kw :confidence n :rationale str}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake sku-id quantity carrier] :as request}]
  {:op op
   :effect :propose
   :sku-id sku-id
   :quantity quantity
   :carrier carrier
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a supply and distribution management advisor. Given a
   request, propose an :op, the :sku-id, :quantity and :carrier, an
   honest :confidence and a :stake. Never call an over-stock
   allocation or an unapproved carrier conforming — the governor
   checks both against the registered sku record.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
