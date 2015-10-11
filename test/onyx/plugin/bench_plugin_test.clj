(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.static.logging-configuration :as log-config]
            [onyx.plugin.core-async]
	    [onyx.lifecycle.metrics.timbre]
	    [onyx.lifecycle.metrics.metrics]
            [onyx.test-helper :refer [load-config]]
            [interval-metrics.core :as im]
            [onyx.api])
  (:import [onyx.plugin RandomInputPlugin]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/balanced)

(def id (java.util.UUID/randomUUID))

(def config (load-config))

(def env-config (assoc (:env-config config) :onyx/id id))
(def peer-config (assoc (:peer-config config) :onyx/id id))

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
(def v-peers (onyx.api/start-peers 6 peer-group))

(println "Started vpeers")
(def bench-length 120000)

(def messages-tracking (atom {}))
(def rate+latency (im/rate+latency {:rate-unit :nanoseconds 
                                    :latency-unit :nanoseconds}))
(def retry-counter 
  (atom (long 0)))

(def total-segments (atom 0))

(Thread/sleep 10000)

(def retry-calls
  {:lifecycle/before-task-start (fn inject-counter-before-task [event lifecycle]
                                  {:generator/rate-fut (future
                                                         (while (not (Thread/interrupted))
                                                           (Thread/sleep 10000)
                                                           (.println (System/out) (str (:onyx.core/id event) " RETRY COUNTER: " @retry-counter))))})
   :lifecycle/after-retry-segment (fn retry-count-inc [event message-id rets lifecycle]
                                    (swap! retry-counter (fn [v] (inc ^long v))))})

(def latency-calls 
  {:lifecycle/after-ack-segment (fn latency-after-ack [event message-id rets lifecycle]
                                  (when-let [v (@messages-tracking message-id)] 
                                    (im/update! rate+latency (- ^long (System/nanoTime) ^long v))
                                    (swap! messages-tracking dissoc message-id)))
   :lifecycle/before-task-start (fn [event lifecycle]
                                  {:latency-printer-fut 
                                   (future 
                                     (while (not (Thread/interrupted))
                                       (Thread/sleep 10000)
                                       (.println (System/out) (str (:onyx.core/id event) " LATENCY: " (im/snapshot! rate+latency)))))})
   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (doseq [m (:onyx.core/batch event)] 
                              (swap! messages-tracking assoc (:id m) (System/nanoTime)))
                            {})})

(def throughput-calls
  {:lifecycle/before-task-start (fn inject-counter-before-task [event lifecycle]
                                  (let [rate (im/rate)] 
                                    {:generator/rate rate
                                     :generator/rate-fut (future
                                                           (while (not (Thread/interrupted))
                                                             (Thread/sleep 1000)
                                                             (.println (System/out) (str (:onyx.core/id event) " RATE: " (im/snapshot! rate)))))}))
   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (let [cnt (count (:onyx.core/batch event))] 
                              (swap! total-segments + cnt)
                              (im/update! (:generator/rate event) cnt))
                            {})})

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
    :lifecycle/doc "Instruments a task's metrics to timbre"}
   #_{:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-test/retry-calls}
   #_{:lifecycle/task :all
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   #_{:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-test/latency-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}])

(onyx.api/submit-job
  peer-config
  {:catalog catalog 
   :workflow workflow
   :lifecycles lifecycles
   :task-scheduler :onyx.task-scheduler/balanced})

(reset! total-segments 0)
(Thread/sleep bench-length)
(println "AVERAGE THROUGHPUT: " (float (* 1000 (/ @total-segments bench-length))))

(doseq [v-peer v-peers]
  (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-peer-group peer-group)

(onyx.api/shutdown-env env)
