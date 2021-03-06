(defproject ars-capture "0.1.0-SNAPSHOT"
  :description "Capture adaptive replica stats at a fixed rate"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.7.0"]
                 [cheshire "5.8.0"]
                 [sonian/carica "1.2.2"]]
  :resource-paths ["etc"]
  :main ars-capture.core)
