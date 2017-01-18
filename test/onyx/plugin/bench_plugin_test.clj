(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]
            [onyx.plugin.bench-plugin]
            [onyx.static.logging-configuration :as log-config]
            [onyx.plugin.core-async]
	    [onyx.lifecycle.metrics.timbre]
	    [onyx.lifecycle.metrics.metrics]
            [onyx.monitoring.events :as monitoring]
            [onyx.test-helper :refer [load-config]]
            [com.stuartsierra.component :as component]
            [onyx.api]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/balanced)

(def id (java.util.UUID/randomUUID))

(def config (load-config))

(def env-config (assoc (:env-config config) :onyx/tenancy-id id))
(def peer-config (assoc (:peer-config config) 
                        :onyx/tenancy-id id
                        :onyx.peer/idle-sleep-ns 5000000
                        :onyx.monitoring/config (component/start (monitoring/new-monitoring)) 
                        :onyx.peer/coordinator-barrier-period-ms 2000))

(def env (onyx.api/start-env env-config))

(def peer-group
  (onyx.api/start-peer-group peer-config))

(def batch-size 200)
(def batch-timeout 10)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn print-time [segment]
  #_(when (zero? (mod (System/nanoTime) 50)) 
    (println "TIME" (- (System/nanoTime) (:start-time segment))))
  segment)

(def in-calls {:lifecycle/before-task-start (fn inject-no-op-ch [event lifecycle]
                                              {:core.async/chan (chan (dropping-buffer 1))})})

(println "Starting Vpeers")
(let [v-peers (onyx.api/start-peers 6 peer-group)
      bench-length 120000]
  (println "Started vpeers")
  ;(Thread/sleep 10000)
  (->> {:workflow [[:in :inc1] 
                   [:inc1 :inc2] 
                   [:inc2 :inc3]
                   [:inc3 :inc4]
                   [:inc4 :no-op]]
        :catalog [{:onyx/name :in
                   :onyx/plugin :onyx.plugin.bench-plugin/generator
                   :onyx/type :input
                   :onyx/medium :generator
                   :benchmark/segment-generator :hundred-bytes
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
                   :onyx/fn ::print-time
                   :onyx/max-peers 1
                   :onyx/medium :core.async
                   :onyx/batch-timeout batch-timeout
                   :onyx/batch-size batch-size
                   :onyx/doc "Drops messages on the floor"}] 
        :lifecycles [{:lifecycle/task :no-op
                      :lifecycle/calls :onyx.plugin.bench-plugin-test/in-calls}
                     {:lifecycle/task :all
                      :metrics/lifecycles #{:lifecycle/apply-fn                                                                                                                                        
                                            :lifecycle/unblock-subscribers                                                                                                                             
                                            :lifecycle/write-batch                                                                                                                                     
                                            :lifecycle/read-batch}
                      :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
                      :lifecycle/doc "Instruments a task's metrics and sends to JMX"}]
        :task-scheduler :onyx.task-scheduler/balanced}
       (onyx.api/submit-job peer-config))

  (Thread/sleep bench-length)

  (doseq [v-peer v-peers]
    (onyx.api/shutdown-peer v-peer))

  (onyx.api/shutdown-peer-group peer-group)

  (onyx.api/shutdown-env env))
