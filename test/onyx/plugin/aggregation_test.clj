(ns onyx.plugin.aggregation-test
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
            [onyx.api]))

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

(defn restartable? [e] 
  true)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/plugin :onyx.plugin.bench-plugin/generator
    :onyx/type :input
    :benchmark/segment-generator :grouping-fn
    :onyx/medium :generator
    :onyx/max-pending 10000
    :onyx/max-peers 2
    :onyx/min-peers 2
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :agg
    :onyx/fn :clojure.core/identity
    :onyx/group-by-key :group-key
    :onyx/restart-pred-fn ::restartable?
    :onyx/uniqueness-key :id
    :onyx/min-peers 3
    :onyx/max-peers 3
    :onyx/flux-policy :recover
    :onyx/type :function
    :onyx/batch-size batch-size}

   {:onyx/name :no-op
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Drops messages on the floor"}])

(def workflow [[:in :agg] 
               [:agg :no-op]])

(println "Starting Vpeers")
(def v-peers (onyx.api/start-peers 6 peer-group))

(println "Started vpeers")
(def bench-length 120000)

(Thread/sleep 10000)

(def test-state (atom []))

(defn update-atom! [event window-id lower-bound upper-bound state]
  (swap! test-state conj [lower-bound upper-bound state]))

(def in-calls 
  {:lifecycle/before-task-start (fn inject-no-op-ch [event lifecycle]
                                  {:core.async/chan (chan (dropping-buffer 1))})})

(let [lifecycles [{:lifecycle/task :no-op
                   :lifecycle/calls ::in-calls}
                  {:lifecycle/task :no-op
                   :lifecycle/calls :onyx.plugin.core-async/writer-calls}

                  {:lifecycle/task :in ; or :task-name for an individual task
                   :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
                   :metrics/buffer-capacity 10000
                   :metrics/workflow-name "your-workflow-name"
                   :metrics/sender-fn :onyx.lifecycle.metrics.timbre/timbre-sender
                   :lifecycle/doc "Instruments a task's metrics to timbre"}]


      windows [{:window/id :count-segments
                :window/task :agg
                :window/type :fixed
                :window/aggregation :onyx.windowing.aggregation/count
                :window/window-key :event-time
                :window/range [10 :minutes]}]


      triggers [{:trigger/window-id :count-segments
                 :trigger/refinement :accumulating
                 :trigger/on :segment
                 :trigger/fire-all-extents? true
                 ;; Align threshhold with batch-size since we'll be restarting
                 :trigger/threshold [500000 :elements]
                 :trigger/sync ::update-atom!}]]


  (onyx.api/submit-job
    peer-config
    {:catalog catalog 
     :workflow workflow
     :lifecycles lifecycles
     :windows windows
     :triggers triggers
     :task-scheduler :onyx.task-scheduler/balanced}))
(Thread/sleep bench-length)

(doseq [v-peer v-peers]
  (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-peer-group peer-group)

(onyx.api/shutdown-env env)
