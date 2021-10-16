(ns com.wsscode.pathom.viz.ws-connector.pathom3
  (:require
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [#?(:clj  com.wsscode.async.async-clj
        :cljs com.wsscode.async.async-cljs) :refer [let-chan]]
    #?(:clj  [com.wsscode.pathom.viz.ws-connector.impl.http-clj :as http-clj]
       :cljs [com.wsscode.pathom.viz.ws-connector.impl.sente-cljs :as sente-cljs])
    [com.wsscode.async.processing :as wap]
    [com.wsscode.pathom.viz.ws-connector.core :as pvc]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
    [com.wsscode.pathom3.plugin :as p.plugin]
    [com.wsscode.promesa.macros :refer [clet]]
    [com.wsscode.pathom3.connect.operation.transit :as pcot]
    [edn-query-language.core :as eql]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.connect.planner :as pcp]))

(defn- call-connector-impl [config parser]
  #?(:clj  (http-clj/connect-parser config parser)
     :cljs (sente-cljs/connect-parser config parser)))

(defn send-message!
  [env msg]
  (let [send-fn! (get-in env [::connector ::pvc/send-message!])]
    (send-fn! msg)))

(defn wrap-log-request [env query process]
  (let [id (wap/random-request-id)]
    (send-message! env
      {::pvc/type       ::pvc/pathom-request
       ::pvc/request-id id
       ::pvc/tx         query})
    (clet [res (process)]
      (send-message! env
        {::pvc/type       ::pvc/pathom-request-done
         ::pvc/request-id id
         ::pvc/response   res})
      res)))

(defn request-wrapper-plugin [wrapper]
  {::pcr/wrap-root-run-graph!
   (fn track-request-root-run-external [process]
     (fn track-request-root-run-internal [{::pcr/keys [root-query] :as env} ast entity*]
       (if root-query
         (process env ast entity*)
         (let [query (if (:type ast)
                       (eql/ast->query ast))]
           (wrapper env query #(process env ast entity*))))))

   :com.wsscode.pathom3.interface.eql/wrap-process-ast
   (fn track-request-process-ast-external [process]
     (fn track-request-process-ast-internal [{::pcr/keys [root-query] :as env} ast]
       (if root-query
         (wrapper env root-query #(process env ast))
         (let [root-query (eql/ast->query ast)
               env        (assoc env ::pcr/root-query root-query)]
           (wrapper env (eql/ast->query ast) #(process env ast))))))})

(p.plugin/defplugin track-requests
  (request-wrapper-plugin wrap-log-request))

(pco/defmutation request-snapshots
  [env {::pcp/keys [source-ast available-data]}]
  {:snapshots
   (pcp/compute-plan-snapshots
     (assoc env
       ::pcp/available-data available-data
       :edn-query-language.ast/node source-ast))})

(defn connect-env
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

  In Clojurescript this will connect to the app using websockets. In Clojure the comes
  are done via HTTP.
  "
  [{::p.a.eql/keys [parallel?]
    :as            env}
   {::keys [async?]
    :or    {async? #?(:clj false :cljs true)}
    :as    config}]
  (let [config     (if (string? config) {::pvc/parser-id config} config)
        config     (assoc config
                     :transit/read-handlers pcot/read-handlers
                     :transit/write-handlers pcot/write-handlers)
        inside-env (pci/register env request-snapshots)
        interface  (if (or parallel? async?)
                     (p.a.eql/boundary-interface inside-env)
                     (p.eql/boundary-interface inside-env))
        env        (assoc inside-env ::connector (call-connector-impl config interface))]

    (-> env
        (p.plugin/register track-requests))))

(defonce logs-connection
  (memoize
    (fn []
      (call-connector-impl {::pvc/parser-id :pathom.viz.hidden/logs
                            ::pvc/hidden?   true}
        (fn [_ _] {})))))

(defn log-entry [msg]
  (let [{::pvc/keys [send-message!]} (logs-connection)]
    (send-message! {::pvc/type  ::pvc/log-entry
                    ::pvc/entry msg})))

(comment
  ; for static analysis
  :pathom.viz.log.type/plan-snapshots
  :pathom.viz.log.type/plan-view
  :pathom.viz.log.type/trace)
