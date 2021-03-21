(ns com.wsscode.pathom-viz.connector.demos.pathom3
  (:require [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom.viz.ws-connector.core :as pvc]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [edn-query-language.core :as eql])
  (:import (java.time LocalDate)))

(pco/defmutation message []
  {::pco/op-name 'message}
  {:response "true"})

(pco/defmutation message-error []
  {::pco/op-name 'message-error}
  (throw (ex-info "Error here" {})))

(def registry
  [(pbir/constantly-resolver :pi Math/PI)
   (pbir/constantly-resolver :bad (LocalDate/now))
   (pbir/constantly-resolver :list [{:id 1} {:id 2}])
   (pbir/constantly-resolver :nest {:id 1})
   (pbir/constantly-fn-resolver :error (fn [_] (throw (ex-info "Error" {}))))
   (pbir/single-attr-resolver :pi :tau #(* 2 %))
   (pbir/single-attr-resolver :id :x inc)
   message
   message-error])

(defonce plan-cache* (atom {}))

(def env
  (-> (pci/register registry)
      (pcp/with-plan-cache plan-cache*)))

(def tracked-env
  (-> env
      (p.connector/connect-env {::pvc/parser-id      `env
                                ::p.connector/async? false})))

(comment
  (pcp/compute-plan-snapshots
    (assoc env
      ::pcp/available-data         {}
      :edn-query-language.ast/node (eql/query->ast [:tau])))

  (p.eql/process tracked-env
    [(list 'com.wsscode.pathom.viz.ws-connector.pathom3/request-snapshots
       {::pcp/available-data         {}
        ::pcp/source-ast (eql/query->ast [:pi])})])

  (meta (p.eql/process tracked-env
          '[{(message {})
             [:pi]}]))

  (p.eql/process tracked-env
    [:tau])

  (-> (p.eql/process tracked-env
        [{:list [:x]}])
      meta)

  (-> (p.eql/process tracked-env
        [{:nest [:x]}])
      meta)

  (-> (p.eql/process tracked-env
        [{[:id 123] [:x]}])
      (get [:id 123])
      (meta))

  (p.eql/process tracked-env
    {:items [{:id 1} {:id 2}]}
    [{:items [:x]}])

  (p.eql/process tracked-env
    [:com.wsscode.pathom.connect/index-resolvers])

  (p.eql/process tracked-env
    [:com.wsscode.pathom.connect/idents])

  (-> (p.a.eql/process env
        [{:list [:x]}])
      deref
      meta)

  (def res *1)

  (-> res meta)

  (->> (p.eql/process tracked-env
         [:com.wsscode.pathom.connect/indexes])
       :com.wsscode.pathom.connect/indexes
       ::pci/index-attributes
       vals
       (into #{} (mapcat keys))))
