(ns com.wsscode.pathom-viz.connector.demos.pathom3
  (:require [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [com.wsscode.pathom.viz.ws-connector.core :as p.connector.core]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.indexes :as pci])
  (:import (java.time LocalDate)))

(def registry
  [(pbir/constantly-resolver :pi Math/PI)
   (pbir/constantly-resolver :bad (LocalDate/now))
   (pbir/single-attr-resolver :pi :tau #(* 2 %))])

(def env
  (pci/register registry))

(def tracked-env
  (-> env
      (p.connector/connect-env {::p.connector.core/parser-id ::demo})))

(comment
  (p.eql/process tracked-env
    [:tau])

  (->> (p.eql/process tracked-env
         [:com.wsscode.pathom.connect/indexes])
       :com.wsscode.pathom.connect/indexes
       ::pci/index-attributes
       vals
       (into #{} (mapcat keys))))
