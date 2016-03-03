(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.lifecycle.metrics.metrics]
            [onyx.lifecycle.metrics.timbre]
            [onyx.lifecycle.metrics.riemann]
            [aero.core :refer [read-config]]
            [onyx.peer.pipeline-extensions :as p-ext]
            [taoensso.timbre :refer  [info warn trace fatal error] :as timbre]
            [onyx.plugin.bench-plugin]
            [onyx.metrics.riemann :as riemann]
            [onyx.monitoring.events :as monitoring]
            [onyx.plugin.core-async]
            [taoensso.timbre :as t]
            [onyx.api])
  (:gen-class))

(defn standard-out-logger
  "Logger to output on std-out, for use with docker-compose"
  [data]
  (let [{:keys [output-fn]} data]
    (println (output-fn data))))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(def no-op-calls
  {:lifecycle/before-task-start inject-no-op-ch})

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn multi-segment-generator [n-new-segments {:keys [n] :as segment}]
  (map (fn [k] (assoc segment :n (+ n k))) (range n-new-segments)))

(defn integer-grouping-fn [segment]
  (mod (:n segment) 10))

(defn last-digit-passes? [event old-segment new-segment all-new n]
  (>= (mod (:n new-segment) 10) n))

(defn restartable? [e]
  true)

(defn -main [n & args]
  (let [n-peers (Integer/parseInt n)
        {:keys [riemann-config
                env-config
                peer-config] :as cfg}(read-config (clojure.java.io/resource "config.edn")
                                          {:profile :default})
        peer-config (assoc peer-config :onyx.log/config {:appenders
                                                         {:standard-out
                                                          {:enabled? true
                                                           :async? false
                                                           :output-fn t/default-output-fn
                                                           :fn standard-out-logger}}})
        peer-group (onyx.api/start-peer-group peer-config)
        env (onyx.api/start-env env-config)
        monitoring-cfg (monitoring/monitoring-config 10000)
        monitoring-thread (riemann/riemann-sender riemann-config (:monitoring/ch monitoring-cfg))
        peers (onyx.api/start-peers n-peers peer-group monitoring-cfg)]
    (clojure.pprint/pprint cfg)
    (println "Attempting to connect to Zookeeper @" (:zookeeper/address peer-config))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread.
                       (fn []
                         (doseq [v-peer peers]
                           (onyx.api/shutdown-peer v-peer))
                         (onyx.api/shutdown-peer-group peer-group)
                         (shutdown-agents))))
    (println "Started peers. Blocking forever.")
    @(promise)))
