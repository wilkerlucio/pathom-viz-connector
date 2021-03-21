(ns com.wsscode.pathom.viz.ws-connector.pathom3
  (:require
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [#?(:clj  com.wsscode.async.async-clj
        :cljs com.wsscode.async.async-cljs) :refer [let-chan]]
    #?(:clj  [com.wsscode.pathom.viz.ws-connector.impl.http-clj :as http-clj]
       :cljs [com.wsscode.pathom.viz.ws-connector.impl.sente-cljs :as sente-cljs])
    [clojure.set :as set]
    [com.wsscode.async.processing :as wap]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom.viz.ws-connector.core :as pvc]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
    [com.wsscode.pathom3.plugin :as p.plugin]
    [com.wsscode.promesa.macros :refer [clet]]
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
       (wrapper env root-query #(process env ast))))})

(p.plugin/defplugin track-requests
  (request-wrapper-plugin wrap-log-request))

(pco/defresolver indexes-resolver [env _]
  {::pco/output [::pci/index-oir ::pci/index-io ::pci/index-resolvers
                 ::pci/index-mutations ::pci/index-attributes
                 ::pci/autocomplete-ignore]}
  (select-keys env [::pci/index-oir ::pci/index-io ::pci/index-resolvers
                    ::pci/index-mutations ::pci/autocomplete-ignore
                    ::pci/index-attributes]))

(pco/defresolver indexes-resolver-wrapped [indexes]
  {::pco/input  [(pco/? :com.wsscode.pathom.connect/index-io)
                 (pco/? :com.wsscode.pathom.connect/index-oir)
                 (pco/? :com.wsscode.pathom.connect/index-resolvers)
                 (pco/? :com.wsscode.pathom.connect/index-mutations)
                 (pco/? :com.wsscode.pathom.connect/index-attributes)
                 (pco/? :com.wsscode.pathom.connect/autocomplete-ignore)
                 (pco/? :com.wsscode.pathom.connect/idents)]
   ::pco/output [{:com.wsscode.pathom.connect/indexes
                  [:com.wsscode.pathom.connect/index-io
                   :com.wsscode.pathom.connect/index-oir
                   :com.wsscode.pathom.connect/index-resolvers
                   :com.wsscode.pathom.connect/index-mutations
                   :com.wsscode.pathom.connect/index-attributes
                   :com.wsscode.pathom.connect/autocomplete-ignore]}]}
  {:com.wsscode.pathom.connect/indexes indexes})

(pco/defmutation request-snapshots
  [env {::pcp/keys [source-ast available-data]}]
  {:snapshots
   (pcp/compute-plan-snapshots
     (assoc env
       ::pcp/available-data available-data
       :edn-query-language.ast/node source-ast))})

(defn single-entry-attributes [{::pci/keys [index-resolvers] :as env}]
  (let [root-available (pci/reachable-attributes env {})]
    (into #{}
          (comp (map pco/operation-config)
                (keep (fn [{::pco/keys [requires]}]
                        (let [missing-inputs (set/difference (set (keys requires)) root-available)]
                          (cond
                            (= 1 (count requires))
                            (first (keys requires))

                            (= 1 (count missing-inputs))
                            (first missing-inputs))))))
          (vals index-resolvers))))

(pco/defresolver indexes-idents [indexes]
  {::pco/input  [::pci/index-resolvers ::pci/index-io]
   ::pco/output [:com.wsscode.pathom.connect/idents]}
  {:com.wsscode.pathom.connect/idents
   (single-entry-attributes indexes)})

(def connector-indexes
  (pci/register
    [indexes-resolver
     indexes-resolver-wrapped
     indexes-idents
     request-snapshots

     (pbir/single-attr-resolver
       ::pci/index-oir
       :com.wsscode.pathom.connect/index-oir
       #(coll/map-vals
          (fn [vals]
            (coll/map-keys
              (fn [x]
                (into #{} (keys x)))
              vals))
          %))

     (pbir/alias-resolver
       ::pci/index-io
       :com.wsscode.pathom.connect/index-io)

     (pbir/single-attr-resolver
       ::pci/index-resolvers
       :com.wsscode.pathom.connect/index-resolvers
       (fn [resolvers]
         (coll/map-vals (comp #(-> %
                                   (set/rename-keys {::pco/op-name
                                                     :com.wsscode.pathom.connect/sym

                                                     ::pco/input
                                                     :com.wsscode.pathom.connect/input

                                                     ::pco/output
                                                     :com.wsscode.pathom.connect/output

                                                     ::pco/provides
                                                     :com.wsscode.pathom.connect/provides})
                                   (coll/update-if :com.wsscode.pathom.connect/input
                                     (fn [i]
                                       (into #{} (keys (::pco/requires %))))))
                              pco/operation-config) resolvers)))

     (pbir/single-attr-resolver
       ::pci/index-mutations
       :com.wsscode.pathom.connect/index-mutations
       (fn [mutations]
         (coll/map-vals (comp #(set/rename-keys % {::pco/op-name
                                                   :com.wsscode.pathom.connect/sym

                                                   ::pco/input
                                                   :com.wsscode.pathom.connect/input

                                                   ::pco/output
                                                   :com.wsscode.pathom.connect/output

                                                   ::pco/provides
                                                   :com.wsscode.pathom.connect/provides})
                              pco/operation-config) mutations)))

     (pbir/single-attr-resolver
       ::pci/index-attributes
       :com.wsscode.pathom.connect/index-attributes
       (fn [idx-attrs]
         (coll/map-vals
           #(set/rename-keys % {:com.wsscode.pathom3.connect.indexes/attr-id
                                :com.wsscode.pathom.connect/attribute-id
                                :com.wsscode.pathom3.connect.indexes/attr-provides
                                :com.wsscode.pathom.connect/attr-provides
                                :com.wsscode.pathom3.connect.indexes/attr-input-in
                                :com.wsscode.pathom.connect/attr-input-in
                                :com.wsscode.pathom3.connect.indexes/attr-output-in
                                :com.wsscode.pathom.connect/attr-output-in
                                :com.wsscode.pathom3.connect.indexes/attr-reach-via
                                :com.wsscode.pathom.connect/attr-reach-via})
           idx-attrs)))]))

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
  [env {::keys [async?]
        :or    {async? true}
        :as    config}]
  (let [inside-env (pci/register env connector-indexes)
        parser     (fn [env' tx]
                     (if async?
                       (p.a.eql/process (merge inside-env env') tx)
                       (p.eql/process (merge inside-env env') tx)))
        env        (assoc inside-env ::connector (call-connector-impl config parser))]

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
