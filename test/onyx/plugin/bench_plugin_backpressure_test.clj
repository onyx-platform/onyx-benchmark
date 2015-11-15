(ns onyx.plugin.bench-plugin-backpressure-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [taoensso.timbre.appenders.rotor :as rotor]
            [interval-metrics.core :as im]
            [onyx.static.logging-configuration :as log-config]
            [onyx.plugin.core-async]
            [onyx.api])
  (:import [onyx.plugin RandomInputPlugin]
           [onyx.plugin JavaFn]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/balanced)

(def messaging :aeron)

(def env-config
  {:zookeeper/address "127.0.0.1:2189"
   :zookeeper/server? true
   :zookeeper.server/port 2189
   :onyx/id id
   :onyx.log/config {:appenders {:standard-out {:enabled? false}
                                 :spit {:enabled? false}
                                 :rotor {:min-level :trace
                                         :enabled? true
                                         :async? false
                                         :max-message-per-msecs nil
                                         :fn rotor/appender-fn}}
                     :shared-appender-config {:rotor {:path "onyx.log"
                                                      :max-size (* 512 102400) :backlog 5}}}
   :onyx.peer/job-scheduler scheduler})

(def pending-timeout 60000)

(def peer-config
  {:zookeeper/address "127.0.0.1:2189"
   :onyx/id id
   ;:onyx.peer/backpressure-check-interval 1000000
   ;:onyx.peer/backpressure-low-water-pct 300
   ;:onyx.peer/backpressure-high-water-pct 600
   :onyx.messaging/ack-daemon-timeout pending-timeout
   :onyx.messaging.aeron/idle-strategy :high-restart-latency
   :onyx.messaging/bind-addr "127.0.0.1"
   :onyx.messaging/peer-ports (vec (range 40000 40200))
   :onyx.peer/job-scheduler scheduler
   :onyx.messaging/impl messaging})


(println "Starting env")
(def env (onyx.api/start-env env-config))

(def peer-group 
  (onyx.api/start-peer-group peer-config))

(def batch-size 20)
(def batch-timeout 100)

(def counter (atom 0))
(def retry-counter (atom 0))
(def messages-tracking (atom {}))
(def rate+latency (im/rate+latency {:rate-unit :nanoseconds 
                                    :latency-unit :nanoseconds}))

(defn my-inc [{:keys [n] :as segment}]
  ;(info "Received segment " segment)
  (when (zero? (rand-int 100000))
    (Thread/sleep 4000))
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/ident :onyx.plugin.bench-plugin/generator
    ;:onyx/ident :onyx.plugin.RandomInputPlugin
    :onyx/type :input
    :onyx/medium :generator
    :onyx/max-pending 500000
    :onyx/pending-timeout pending-timeout
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc1
    :onyx/fn :onyx.plugin.bench-plugin-backpressure-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc2
    :onyx/fn :onyx.plugin.bench-plugin-backpressure-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc3
    :onyx/fn :onyx.plugin.bench-plugin-backpressure-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc4
    :onyx/fn :onyx.plugin.bench-plugin-backpressure-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :no-op
    :onyx/ident :onyx.plugin.core-async/output
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Drops messages on the floor"}])

;; TO TEST

(def workflow [[:in :inc1] 
               [:inc1 :inc2] 
               [:inc2 :inc3]
               [:inc3 :inc4]
               [:inc4 :no-op]])


(println "starting Vpeers")
(def v-peers (onyx.api/start-peers 6 peer-group))

(println "Started vpeers")
(def bench-length 30000000)

(Thread/sleep 10000)

(def start-time (java.util.Date.))

(def start-time-millis (System/currentTimeMillis))

(def bench-calls
  {:lifecycle/before-task-start (fn inject-counter-before-task [event lifecycle]
                                  {:counter counter
                                   :retry-counter retry-counter})
   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (swap! (:counter event) + (count (:onyx.core/batch event)))
                            {})})


(def in-calls 
  {:lifecycle/before-task-start (fn inject-no-op-ch [event lifecycle]
                                  {:core.async/chan (chan (dropping-buffer 1))})})

(def lifecycles
  [{:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/in-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/latency-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}
   {:lifecycle/task :inc1
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}
   {:lifecycle/task :inc2
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}
   {:lifecycle/task :inc3
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}
   {:lifecycle/task :inc4
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.bench-plugin-backpressure-test/bench-calls}])

(println "Starting job at " (java.util.Date.))
(onyx.api/submit-job
  peer-config
  {:catalog catalog 
   :workflow workflow
   :lifecycles lifecycles
   :task-scheduler :onyx.task-scheduler/balanced})

(println "Done starting job at " (java.util.Date.))

(future
  (while true 
    (println "Retry counter " @retry-counter)
    (println "Update " @counter (float (/ @counter
                                          (/ (- (System/currentTimeMillis) start-time-millis) 
                                             1000))) "/sec")
    (Thread/sleep 10000)))


(Thread/sleep bench-length)
(println "Done at " start-time (java.util.Date.) @counter 
         (float (* 1000 (/ @counter bench-length))) "/sec")

(doseq [v-peer v-peers]
  (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-peer-group peer-group)

(onyx.api/shutdown-env env)
