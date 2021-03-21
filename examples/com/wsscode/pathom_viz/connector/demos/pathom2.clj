(ns com.wsscode.pathom-viz.connector.demos.pathom2
  (:require [com.wsscode.pathom.viz.ws-connector.core :as p.connector]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [clojure.set :as set])
  (:import (java.time LocalDate)))

(pc/defresolver nested [_]
  {:output {:nested {:value "true"}}})

(pc/defresolver item-by-id [{:keys [item/id]}]
  {:item/id   id
   :item/text "bar"})

(def registry
  [nested
   (pc/constantly-resolver :pi Math/PI)
   (pc/constantly-resolver :bad (LocalDate/now))
   (pc/single-attr-resolver :pi :tau #(* 2 %))
   item-by-id])

(def parser
  (p/parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(def tracked-parser
  (p.connector/connect-parser
    {::p.connector/parser-id ::my-parser}
    (fn [env tx]
      (parser (assoc env :extra-connector-stuff "bla") tx))))

(comment


  (parser {} [{::pc/indexes [::pc/index-io]}])

  (pci/reachable-paths
    #:com.wsscode.pathom3.connect.indexes{:index-io {#{:item/id} #:item{:id {}, :text {}}}}
    {:item/id {}})

  (tracked-parser {} [:works-here?]))
