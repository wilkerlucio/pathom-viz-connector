(ns com.wsscode.pathom.viz.ws-connector.core
  (:require
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [let-chan]]
    [com.wsscode.pathom.viz.ws-connector.impl.sente-cljs :as sente-cljs]))

(>def ::host string?)
(>def ::port pos-int?)
(>def ::path string?)
(>def ::on-connect fn?)
(>def ::on-disconnect fn?)
(>def ::on-message fn?)
(>def ::parser-id any?)
(>def ::auto-trace? boolean?)

(defn connect-parser
  "Connect a Pathom parser to the Pathom Viz desktop app. The return of this function
  is a new parser, which will log all queries done to it in the app, a suggested
  pattern to use:

    (def parser
      (cond->> (p/parser ...)
        dev-mode?
        (p.connector/connect-parser
          {::p.connector/parser-id ::my-parser})))

  Make that dev flag something you can turn off in production. This way you can see
  the request in the app as they happen.

  The configuration options available are:

    - `::p.connector/host` (default: localhost) Host of the desktop app background server.
    - `::p.connector/port` (default: 8240) Port of app background server
    - `::p.connector/parser-id` - An id for this parser, make it unique for this parser
      so the app can have better memory about it

  In Clojurescript this will connect to the app using websockets. In Clojure the comms
  are done via HTTP.
  "
  [{::keys [auto-trace?] :as config} parser]
  (let [config' (merge {::auto-trace? true} config)
        {::keys [send-message!]} (sente-cljs/connect-parser config' parser)]

    (fn connected-parser [env tx]
      (let [id (random-uuid)]
        (send-message! {::type       ::pathom-request
                        ::request-id id
                        ::tx         tx})
        (let-chan [res (parser env (cond-> tx auto-trace? (conj :com.wsscode.pathom/trace)))]
          (send-message! {::type       ::pathom-request-done
                          ::request-id id
                          ::response   res})
          res)))))
