(ns com.wsscode.pathom.viz.ws-connector.pathom3.adapter
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [clojure.set :as set]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.misc.coll :as coll]
            [com.wsscode.pathom3.interface.eql :as p.eql]))

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

(def env
  (pci/register
    {:pathom/lenient-mode? true}
    [indexes-resolver-wrapped
     indexes-idents

     (pbir/constantly-resolver :pathom.viz/support-boundary-interface? true)

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
           idx-attrs)))

     (pbir/alias-resolver
       ::pci/transient-attrs
       :com.wsscode.pathom.connect/autocomplete-ignore)]))

(defn ensure-pathom2-indexes [indexes]
  (let [indexes (or (::pci/indexes indexes) indexes)]
   (p.eql/satisfy env indexes [:com.wsscode.pathom.connect/indexes])))
