(ns com.wsscode.pathom-viz.connector.demos.pathom3
  (:require [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom.viz.ws-connector.core :as pvc]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql])
  (:import (java.time LocalDate)))

(def registry
  [(pbir/constantly-resolver :pi Math/PI)
   (pbir/constantly-resolver :bad (LocalDate/now))
   (pbir/constantly-resolver :list [{:id 1} {:id 2}])
   (pbir/constantly-fn-resolver :error (fn [_] (throw (ex-info "Error" {}))))
   (pbir/single-attr-resolver :pi :tau #(* 2 %))
   (pbir/single-attr-resolver :id :x inc)])

(def env
  (pci/register registry))

(def tracked-env
  (-> env
      (p.connector/connect-env {::pvc/parser-id "demo"})))

(comment
  (p.eql/process tracked-env
    [:tau])

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
