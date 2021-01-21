(ns com.wsscode.transit
  (:refer-clojure :exclude [read write])
  (:require [cognitect.transit :as t]
    #?(:cljs [goog.object :as gobj]))
  #?(:clj (:import (java.io ByteArrayOutputStream ByteArrayInputStream OutputStream)
                   (com.cognitect.transit WriteHandler TransitFactory)
                   (java.util.function Function))))

#?(:clj
   (deftype DefaultHandler []
     WriteHandler
     (tag [this v] "unknown")
     (rep [this v] (pr-str v)))
   :cljs
   (deftype DefaultHandler []
     Object
     (tag [this v] "unknown")
     (rep [this v] (pr-str v))))

(defn read [s]
  #?(:clj
     (let [in     (ByteArrayInputStream. (.getBytes s))
           reader (t/reader in :json)]
       (t/read reader))

     :cljs
     (let [reader (t/reader :json)]
       (t/read reader s))))

#?(:cljs
   (def cljs-write-handlers
     {"default" (DefaultHandler.)}))

#?(:clj
   (defn writer
     "Creates a writer over the provided destination `out` using
      the specified format, one of: :msgpack, :json or :json-verbose.
      An optional opts map may be passed. Supported options are:
      :handlers - a map of types to WriteHandler instances, they are merged
      with the default-handlers and then with the default handlers
      provided by transit-java.
      :transform - a function of one argument that will transform values before
      they are written."
     ([out type] (writer out type {}))
     ([^OutputStream out type {:keys [handlers transform default-handler]}]
      (if (#{:json :json-verbose :msgpack} type)
        (let [handler-map (merge t/default-write-handlers handlers)]
          (t/->Writer
            (TransitFactory/writer (#'t/transit-format type) out handler-map default-handler
              (when transform
                (reify Function
                  (apply [_ x]
                    (transform x)))))))
        (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type}))))))

(defn ^String write [x]
  #?(:clj
     (let [out    (ByteArrayOutputStream. 4096)
           writer (writer out :json {:default-handler (DefaultHandler.)
                                     :transform       t/write-meta})]
       (t/write writer x)
       (.toString out))

     :cljs
     (let [writer (t/writer :json {:handlers  cljs-write-handlers
                                   :transform t/write-meta})]
       (t/write writer x))))

#?(:cljs
   (defn envelope-json [msg]
     #js {:transit-message (write msg)}))

#?(:cljs
   (defn unpack-json [msg]
     (some-> (gobj/get msg "transit-message") read)))
