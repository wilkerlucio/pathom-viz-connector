(ns com.wsscode.pathom-viz.connector.demos.clj-parser
  (:require [com.wsscode.pathom.viz.ws-connector.core :as p.connector]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]))

(def registry
  [(pc/constantly-resolver :works-here? "WORKS!!")])

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
  (p.connector/connect-parser
    parser
    {::p.connector/parser-id ::my-parser})

  (parser {} [:works-here?]))