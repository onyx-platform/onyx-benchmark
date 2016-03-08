(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.static.logging-configuration :as log-config]
            [onyx.plugin.core-async]
	    [onyx.lifecycle.metrics.timbre]
	    [onyx.lifecycle.metrics.metrics]
            [onyx.monitoring.events :as monitoring]                                                                                                                                                                                                                           
            [onyx.test-helper :refer [load-config]]
            [interval-metrics.core :as im]
            [onyx.api])
  (:import [onyx.plugin RandomInputPlugin]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/balanced)

(def id (java.util.UUID/randomUUID))

(def config (load-config))

(def env-config (assoc (:env-config config) :onyx/tenancy-id id))
(def peer-config (assoc (:peer-config config) :onyx/tenancy-id id))

(def env (onyx.api/start-env env-config))

(def peer-group 
  (onyx.api/start-peer-group peer-config))

(def batch-size 20)
(def batch-timeout 10)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/plugin :onyx.plugin.bench-plugin/generator
    :onyx/type :input
    :onyx/medium :generator
    :benchmark/segment-generator :hundred-bytes
    :onyx/max-pending 10000
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc1
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc2
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc3
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc4
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :no-op
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type :output
    :onyx/max-peers 1
    :onyx/medium :core.async
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Drops messages on the floor"}])

(def workflow [[:in :inc1] 
               [:inc1 :inc2] 
               [:inc2 :inc3]
               [:inc3 :inc4]
               [:inc4 :no-op]])

(println "Starting Vpeers")
(let [host-id (str "thishost")
      m-cfg (monitoring/monitoring-config host-id 10000)
      monitoring-thread (onyx.lifecycle.metrics.timbre/timbre-sender {} (:monitoring/ch m-cfg))
      v-peers (onyx.api/start-peers 6 peer-group m-cfg)]

  (println "Started vpeers")
  (def bench-length 120000)

  (Thread/sleep 10000)

  (def in-calls 
    {:lifecycle/before-task-start (fn inject-no-op-ch [event lifecycle]
                                    {:core.async/chan (chan (dropping-buffer 1))})})

  (def lifecycles
    [{:lifecycle/task :no-op
      :lifecycle/calls :onyx.plugin.bench-plugin-test/in-calls}
     {:lifecycle/task :no-op
      :lifecycle/calls :onyx.plugin.core-async/writer-calls}

     {:lifecycle/task :in ; or :task-name for an individual task
      :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
      :metrics/buffer-capacity 10000
      :metrics/workflow-name "your-workflow-name"
      :metrics/sender-fn :onyx.lifecycle.metrics.timbre/timbre-sender
      :lifecycle/doc "Instruments a task's metrics to timbre"}])

  (onyx.api/submit-job
    peer-config
    {:catalog catalog 
     :workflow workflow
     :lifecycles lifecycles
     :task-scheduler :onyx.task-scheduler/balanced})

  (Thread/sleep bench-length)

  (doseq [v-peer v-peers]
    (onyx.api/shutdown-peer v-peer))

  (onyx.api/shutdown-peer-group peer-group)

  (onyx.api/shutdown-env env))
