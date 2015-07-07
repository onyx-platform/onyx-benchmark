(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [onyx.peer.function :as function]
            [onyx.static.default-vals :refer [defaults]]
            [taoensso.timbre :refer [info warn trace fatal level-compile-time] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]))

(def hundred-bytes 
  (into-array Byte/TYPE (range 100)))

(defn flush-swap! [a f-read f-swap]
  (loop []
    (let [v (deref a)]
      (if (compare-and-set! a v (f-swap v))
        (f-read v)
        (recur)))))

(defn inject-reader
  [event lifecycle]
  (let [pipeline (:onyx.core/pipeline event)] 
    {:generator/pending-messages (:pending-messages pipeline)}))

(def reader-calls
  {:lifecycle/before-task-start inject-reader})

(defrecord InputSegment [id message])

(defrecord BenchmarkInput [pending-messages retry max-pending batch-size]
  p-ext/Pipeline
  (write-batch [this event]
    (function/write-batch event))

  (read-batch [_ event]
    (let [pending (count @pending-messages)
          max-segments (min (- max-pending pending) batch-size)
          segments (->> (flush-swap! retry 
                                     #(take max-segments %)
                                     #(subvec % (min max-segments (count %))))
                        (map (fn [m] (->InputSegment (java.util.UUID/randomUUID) m))))
          batch (loop [n (count segments) 
                       sgs segments]
                  (if (= n max-segments)
                    sgs
                    (recur (inc n)
                           (conj sgs (->InputSegment (java.util.UUID/randomUUID)
                                                     {:n n :data hundred-bytes})))))]
      (doseq [m batch] 
        (swap! pending-messages assoc (:id m) (:message m)))
      {:onyx.core/batch batch}))

  (seal-resource [this event])

  p-ext/PipelineInput
  (ack-message [_ _ message-id]
    (swap! pending-messages dissoc message-id))

  (retry-message 
    [_ _ message-id]
    (when-let [msg (get @pending-messages message-id)]
      (swap! retry conj msg)
      (swap! pending-messages dissoc message-id)))

  (pending?
    [_ _ message-id]
    (get @pending-messages message-id))

  (drained?
    [_ _]
    false))

(defn generator [pipeline-data]
  (let [task-map (:onyx.core/task-map pipeline-data)
        max-pending (or (:onyx/max-pending task-map) (:onyx/max-pending defaults))
        batch-size (:onyx/batch-size task-map)
        retry-counter (atom 0)]
    (->BenchmarkInput (atom {}) (atom []) max-pending batch-size))) 
