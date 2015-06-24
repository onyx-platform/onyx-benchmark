(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [onyx.peer.function :as function]
            [onyx.static.default-vals :refer [defaults]]
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
    {:generator/pending-messages (:pending-messages pipeline) 
     :generator/retry (:retry pipeline)
     :generator/retry-counter (:retry-counter pipeline)}))

(def reader-calls
  {:lifecycle/before-task-start inject-reader})

(defrecord BenchmarkInput [pending-messages retry retry-counter max-pending batch-size]
  p-ext/Pipeline
  (write-batch [this event]
    (function/write-batch event))

  (read-batch [_ event]
    (let [pending (count @pending-messages)
          max-segments (min (- max-pending pending) batch-size)
          segments (->> (flush-swap! retry 
                                     #(take max-segments %)
                                     #(subvec % (min max-segments (count %))))
                        (map (fn [m] {:id (java.util.UUID/randomUUID)
                                      :input :generator
                                      :message m})))
          batch (loop [n (count segments) 
                       sgs segments]
                  (if (= n max-segments)
                    sgs
                    (recur (inc n)
                           (conj sgs {:id (java.util.UUID/randomUUID)
                                      :input :generator
                                      :message {:n n 
                                                :data hundred-bytes}}))))]
      (doseq [m batch] 
        (swap! pending-messages assoc (:id m) (:message m)))
      {:onyx.core/batch batch}))

  p-ext/PipelineInput
  (ack-message [_ _ message-id]
    (swap! pending-messages dissoc message-id))

  (retry-message 
    [_ _ message-id]
    (when-let [msg (get @pending-messages message-id)]
      ;; TODO: We should be doing this via a retry-message lifecycle function
      (when retry-counter 
        (swap! retry-counter inc))
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
        batch-size (:onyx/batch-size task-map)]
    (->BenchmarkInput (atom {}) (atom []) (atom 0) max-pending batch-size)))

(comment 
  (defn inject-reader
  [event lifecycle]
  {:generator/pending-messages (atom {})
   :generator/retry (atom [])})

(def reader-calls
  {:lifecycle/before-task-start inject-reader})

(defmethod p-ext/read-batch :generator
  [{:keys [onyx.core/task-map
           generator/pending-messages
           generator/retry] :as event}]
  (let [pending (count @pending-messages)
        max-pending (or (:onyx/max-pending task-map) (:onyx/max-pending defaults))
        batch-size (:onyx/batch-size task-map)
        max-segments (min (- max-pending pending) batch-size)
        segments (->> (flush-swap! retry 
                                   #(take max-segments %)
                                   #(subvec % (min max-segments (count %))))
                      (map (fn [m] {:id (java.util.UUID/randomUUID)
                                    :input :generator
                                    :message m})))
        batch (loop [n (count segments) 
                     sgs segments]
                (if (= n max-segments)
                  sgs
                  (recur (inc n)
                         (conj sgs {:id (java.util.UUID/randomUUID)
                                    :input :generator
                                    :message {:n n 
                                              :data hundred-bytes}}))))]
    (doseq [m batch] 
      (swap! pending-messages assoc (:id m) (:message m)))
    {:onyx.core/batch batch}))

(defmethod p-ext/ack-message :generator
  [{:keys [generator/pending-messages]} message-id]
  (swap! pending-messages dissoc message-id))

(defmethod p-ext/retry-message :generator
  [{:keys [generator/pending-messages 
           generator/retry
           retry-counter]} message-id]
  (when-let [msg (get @pending-messages message-id)]
    ;; TODO: We should be doing this via a retry-message lifecycle function
    (when retry-counter 
      (swap! retry-counter inc))
    (swap! retry conj msg)
    (swap! pending-messages dissoc message-id)))

(defmethod p-ext/pending? :generator
  [{:keys [generator/pending-messages]} message-id]
  (get @pending-messages message-id))

(defmethod p-ext/drained? :generator
  [event]
  ;; Infinite stream of messages, never drained.
  false))
