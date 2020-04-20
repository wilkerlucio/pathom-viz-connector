(ns com.wsscode.pathom.viz.ws-connector.impl.http-clj
  (:require [org.httpkit.client :as client]
            [org.httpkit.server :as server]
            [com.wsscode.async.processing :as wap]
            [com.wsscode.async.async-clj :refer [<?!maybe]]
            [com.wsscode.transit :as t])
  (:import (java.util UUID)))

(defonce server* (atom nil))
(defonce parsers* (atom {}))

(defn random-port []
  (+ 10000 (rand-int 5000)))

(defn send-message! [config msg]
  (client/post "http://localhost:8240/request"
    {:headers {"Content-Type" "application/transit+json"}
     :body    (-> msg
                  (assoc
                    :com.wsscode.node-ws-server/client-id
                    (:com.wsscode.node-ws-server/client-id config)

                    ::local-http-address
                    (::local-http-address config))
                  t/write)})
  (wap/await! msg))

(defn handler
  [request]
  (let [{:com.wsscode.pathom.viz.ws-connector.core/keys [type]
         :com.wsscode.node-ws-server/keys               [client-id]
         :edn-query-language.core/keys                  [query]
         :as                                            msg}
        (-> request :body slurp t/read)]
    (if-not (wap/capture-response! msg)
      (if-let [parser (get @parsers* client-id)]
        (case type
          :com.wsscode.pathom.viz.ws-connector.core/parser-request
          (let [res (<?!maybe (parser {} query))]
            (send-message! request (wap/reply-message msg res)))

          (println "Unknown message received" msg))))))

(defn send-connect-message! [config]
  (send-message! config
    {:com.wsscode.pathom.viz.ws-connector.core/type
     :com.wsscode.pathom.viz.ws-connector.core/ping}))

(defn stop! []
  (when-let [{::keys [server]} @server*]
    (server :timeout 100)
    (reset! server* nil)))

(defn start-http-server! [config parser]
  (let [client-id (or (:com.wsscode.pathom.viz.ws-connector.core/parser-id config)
                      (UUID/randomUUID))]
    (swap! parsers* assoc client-id parser)
    (if @server*
      (assoc @server* :com.wsscode.node-ws-server/client-id client-id)
      (let [port (random-port)]
        (let [local-http-address (str "http://localhost:" port "/")
              config'            (assoc config
                                   ::port port
                                   ::local-http-address local-http-address)
              server             (server/run-server #(handler (merge % config')) {:port port})]
          (reset! server* (assoc config' ::server server))
          (assoc config'
            :com.wsscode.node-ws-server/client-id client-id
            ::server server))))))

(defn connect-parser [config parser]
  (let [config' (start-http-server! config parser)]
    (send-connect-message! config')
    {:com.wsscode.pathom.viz.ws-connector.core/send-message!
     #(send-message! config' %)}))

(comment
  (start-http-server! {:com.wsscode.pathom.viz.ws-connector.core/parser-id "testing"}
    (fn [_ _] {:hello "world"}))

  (def s (server/run-server handler {:port 13003}))
  (server/close))
