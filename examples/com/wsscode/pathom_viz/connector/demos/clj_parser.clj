(ns com.wsscode.pathom-viz.connector.demos.clj-parser
  (:require [com.wsscode.pathom.viz.ws-connector.core :as p.connector]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc])
  (:import (java.time LocalDate)))

(def registry
  [(pc/constantly-resolver :pi Math/PI)
   (pc/constantly-resolver :bad (LocalDate/now))
   (pc/single-attr-resolver :pi :tau #(* 2 %))])

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

(comment
  (def tracked-parser
    (p.connector/connect-parser
      {::p.connector/parser-id ::my-parser}
      parser))

  (tracked-parser {} [:works-here?]))
