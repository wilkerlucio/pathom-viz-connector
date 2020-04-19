#!/usr/bin/env bb

(defn prompt
  ([question] (prompt question identity))
  ([question coercion]
   (print (str question ": "))
   (flush)
   (let [response (str/trim (read-line))]
     (coercion response))))

(let [login    (prompt "Clojars username")
      password (prompt "Clojars password")
      new-env  (assoc (into {} (System/getenv)) "CLOJARS_USERNAME" login "CLOJARS_PASSWORD" password)]
  (println "Packing...")
  (shell/sh "clojure" "-A:pack")
  (println "Deploying...")
  (println (:out (shell/sh "clojure" "-A:deploy" :env new-env))))
