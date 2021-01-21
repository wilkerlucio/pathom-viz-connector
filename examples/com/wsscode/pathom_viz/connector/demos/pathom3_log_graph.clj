(ns com.wsscode.pathom-viz.connector.demos.pathom3-log-graph
  (:require [com.wsscode.pathom.viz.ws-connector.pathom3.log-view :as log-v]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.interface.eql :as p.eql]))

(def registry
  [(pbir/constantly-resolver :pi Math/PI)
   (pbir/single-attr-resolver :pi :tau #(* 2 %))])

(def env
  (pci/register registry))

(comment
  ; logs the graph from the root
  (log-v/log-execution-graph
    (p.eql/process env [:tau])))
