(ns com.wsscode.pathom.viz.ws-connector.core
  (:require
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]))

(>def ::host string?)
(>def ::port pos-int?)
(>def ::path string?)
(>def ::on-connect fn?)
(>def ::on-disconnect fn?)
(>def ::on-message fn?)
(>def ::parser-id any?)
(>def ::auto-trace? boolean?)
