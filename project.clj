(defproject com.wsscode/pathom-viz-connector "2021.07.15"
  :description "Connector tools to use the Pathom Viz Application."
  :url "https://github.com/wilkerlucio/pathom-viz-connector"
  :min-lein-version "2.7.0"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[com.wsscode/transito "2021.07.04"]
                 [com.fulcrologic/guardrails "0.0.12"]
                 [com.taoensso/sente "1.16.0"]
                 [com.wsscode/async "1.0.11"]
                 [http-kit "2.3.0"]
                 [com.wsscode/promesa-bridges "2021.01.20"]]

  :source-paths ["src"])
