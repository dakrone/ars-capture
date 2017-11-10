(ns ars-capture.core
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [carica.core :refer :all])
  (:import (java.util.concurrent ScheduledThreadPoolExecutor
                                 TimeUnit))
  (:gen-class))

(def debug false)

(defn retrieve-and-store-stats
  "Retrieve the adaptive replica stats from /_nodes/stats and store it in a
  different ES instance"
  []
  (try
    (let [time (System/currentTimeMillis)
          nodes-stats (:body (http/get
                              (str (config :es :url) "/_nodes/stats")
                              {:headers (merge {"Content-Type" "application/json"}
                                               (config :es :headers))
                               :as :json}))
          doc {:time time
               :stats nodes-stats}]
      (future
        (try
          (let [url (str (config :es-store :url) "/"
                         (config :es-store :index) "/doc/")]
            (when debug (println "putting" doc "at" url))
            (http/post url
                       {:body (json/encode doc)
                        :headers (merge {"Content-Type" "application/json"}
                                        (config :es :headers))}))
          (print ".")
          (catch Exception e
            (if debug
              (println "\n" e)
              (print "F")))
          (finally (flush)))))
    (catch Exception e
      (if debug
        (println "\n" e)
        (print "f"))
      (flush))))

(defn -main
  "Main entry point"
  [& args]
  (println "Starting up...")
  (let [threadpool (ScheduledThreadPoolExecutor. (config :threads))
        block (promise)]
    (.scheduleAtFixedRate threadpool retrieve-and-store-stats
                          1 ;; initial delay of 1 second
                          (config :period-seconds)
                          TimeUnit/SECONDS)
    (println "Reading from" (config :es :url) "and writing to" (config :es-store :url))
    ;; Block forever - TODO: make this not suck
    @block))
