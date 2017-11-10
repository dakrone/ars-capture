(ns ars-capture.core
  (:require [clojure.string :as str]
            [clj-http.client :as http]
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
          nodes-stats (-> (http/get
                           (str (config :es :url) "/_nodes/stats")
                           {:headers (merge {"Content-Type" "application/json"}
                                            (config :es :headers))
                            :as :json})
                          :body
                          :nodes)
          nodes (keys nodes-stats)
          docs (for [node nodes]
                 {:timestamp time
                  :node node
                  :stats (-> nodes-stats node :adaptive_selection)})]
      (future
        (try
          (let [url (str (config :es-store :url) "/"
                         (config :es-store :index) "/doc/_bulk")
                bulk-body (str/join
                           "\n"
                           (for [doc docs]
                             (str (json/encode {:index {}}) "\n"
                                  (json/encode doc) "\n")))]
            (when debug (println "putting" bulk-body "at" url))
            (http/post url
                       {:body bulk-body
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
  (if (http/missing? (http/head (str (config :es-store :url) "/"
                                     (config :es-store :index))
                                {:throw-exceptions false}))
    (do
      (print "Creating" (config :es-store :index) "index...")
      (-> (http/put (str (config :es-store :url) "/" (config :es-store :index))
                  {:body (json/encode (config :es-store :creation-settings))
                   :headers (merge {"Content-Type" "application/json"}
                                   (config :es :headers))})
          :status
          println))
    (println "Index" (config :es-store :index) "already exists"))
  (let [threadpool (ScheduledThreadPoolExecutor. (config :threads))]
    (.scheduleAtFixedRate threadpool retrieve-and-store-stats
                          1 ;; initial delay of 1 second, just because
                          (config :period-seconds)
                          TimeUnit/SECONDS)
    (println "Reading from" (config :es :url) "and writing to" (config :es-store :url))
    ;; Block forever - TODO: make this not suck
    @(promise)))
