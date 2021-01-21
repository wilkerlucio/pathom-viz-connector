(ns com.wsscode.pathom.viz.ws-connector.pathom3.log-view
  (:require [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.con]))

(defn log-execution-graph [x]
  (let [x (or (some-> x meta :com.wsscode.pathom3.connect.runner/run-stats)
              (some-> x :com.wsscode.pathom3.connect.runner/run-stats)
              x)]
    (if (:com.wsscode.pathom3.connect.planner/nodes x)
      (p.con/log-entry (assoc x :pathom.viz.log/type :pathom.viz.log.type/plan-and-stats))
      (p.con/log-entry x))))
