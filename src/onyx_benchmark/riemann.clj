(ns onyx-benchmark.riemann
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
            [taoensso.timbre :refer [warn info]]
            [riemann.client :as r]))

(defn zookeeper-write-log-entry [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-log-entry.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-log-entry.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-read-log-entry [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.read-log-entry.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-write-catalog [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-catalog.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-catalog.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-workflow [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-workflow.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-workflow.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-flow-conditions [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-flow-conditions.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-flow-conditions.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-lifecycles [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-lifecycles.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-lifecycles.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-task [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-task.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-task.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-chunk [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-chunk.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-chunk.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-job-scheduler [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-job-scheduler.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-job-scheduler.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-messaging [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-messaging.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-messaging.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-force-write-chunk [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.force-write-chunk.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.force-write-chunk.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-write-origin [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-origin.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.write-origin.bytes"
           :state "ok"
           :metric bytes}))

(defn zookeeper-read-catalog [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-catalog.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-workflow [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-workflow.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-flow-conditions [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-flow-conditions.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-lifecycles [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-lifecycles.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-task [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-task.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-chunk [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-chunk.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-origin [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-origin.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-job-scheduler [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-job-scheduler.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-read-messaging [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-messaging.latency"
           :state "ok"
           :metric latency}))

(defn zookeeper-gc-log-entry [ch config {:keys [latency position]}]
  (>!! ch {:service "zookeeper.gc-log-entry.latency"
           :state "ok"
           :metric latency})
  (>!! ch {:service "zookeeper.gc-log-entry.position"
           :state "ok"
           :metric position}))

(defn peer-ack-segments [ch config {:keys [latency]}]
  (>!! ch {:service "peer.ack-segments.latency"
           :state "ok"
           :metric latency}))

(defn peer-retry-segment [ch config {:keys [latency]}]
  (>!! ch {:service "peer.retry-segment.latency"
           :state "ok"
           :metric latency}))

(defn peer-try-complete-job [ch config event]
  (>!! ch {:service "peer.try-complete-job.event" :state "ok"}))

(defn peer-strip-sentinel [ch config event]
  (>!! ch {:service "peer.strip-sentinel.event" :state "ok"}))

(defn peer-complete-message [ch config {:keys [latency]}]
  (>!! ch {:service "peer.complete-message.latency"
           :state "ok"
           :metric latency}))

(defn peer-gc-peer-link [ch config event]
  (>!! ch {:service "peer.gc-peer-link.event" :state "ok"}))

(defn peer-backpressure-on [ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-on.event" :state "ok"}))

(defn peer-backpressure-off [ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-off.event" :state "ok"}))

(defn peer-prepare-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.prepare-join.event" :state "ok"}))

(defn peer-accept-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.accept-join.event" :state "ok"}))

(defn peer-notify-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.notify-join.event" :state "ok"}))

(defn monitoring-config [riemann-host riemann-port buf-capacity]
  (let [ch (chan (dropping-buffer buf-capacity))]
    {:monitoring :custom
     :riemann/host riemann-host
     :riemann/port riemann-port
     :riemann/ch ch
     :zookeeper-write-log-entry (partial zookeeper-write-log-entry ch)
     :zookeeper-read-log-entry (partial zookeeper-read-log-entry ch)
     :zookeeper-write-workflow (partial zookeeper-write-workflow ch)
     :zookeeper-write-catalog (partial zookeeper-write-catalog ch)
     :zookeeper-write-flow-conditions (partial zookeeper-write-flow-conditions ch)
     :zookeeper-write-lifecycles (partial zookeeper-write-lifecycles ch)
     :zookeeper-write-task (partial zookeeper-write-task ch)
     :zookeeper-write-chunk (partial zookeeper-write-chunk ch)
     :zookeeper-write-job-scheduler (partial zookeeper-write-job-scheduler ch)
     :zookeeper-write-messaging (partial zookeeper-write-messaging ch)
     :zookeeper-force-write-chunk (partial zookeeper-force-write-chunk ch)
     :zookeeper-write-origin (partial zookeeper-write-origin ch)
     :zookeeper-read-catalog (partial zookeeper-read-catalog ch)
     :zookeeper-read-workflow (partial zookeeper-read-workflow ch)
     :zookeeper-read-flow-conditions (partial zookeeper-read-flow-conditions ch)
     :zookeeper-read-lifecycles (partial zookeeper-read-lifecycles ch)
     :zookeeper-read-task (partial zookeeper-read-task ch)
     :zookeeper-read-chunk (partial zookeeper-read-chunk ch)
     :zookeeper-read-origin (partial zookeeper-read-origin ch)
     :zookeeper-read-job-scheduler (partial zookeeper-read-job-scheduler ch)
     :zookeeper-read-messaging (partial zookeeper-read-messaging ch)
     :zookeeper-gc-log-entry (partial zookeeper-gc-log-entry ch)
     :peer-ack-segments (partial peer-ack-segments ch)
     :peer-retry-segment (partial peer-retry-segment ch)
     :peer-complete-message (partial peer-complete-message ch)
     :peer-gc-peer-link (partial peer-gc-peer-link ch)
     :peer-backpressure-on (partial peer-backpressure-on ch)
     :peer-backpressure-off (partial peer-backpressure-off ch)
     :peer-prepare-join (partial peer-prepare-join ch)
     :peer-notify-join (partial peer-notify-join ch)
     :peer-accept-join (partial peer-accept-join ch)}))

(defn start-riemann-sender [{:keys [riemann/host riemann/port riemann/ch]}]
  (info "Starting Riemann sender...")
  (future
    (let [client (r/tcp-client {:host host :port port})]
      (loop []
        (try
          (when-let [event (<!! ch)]
            @(r/send-event client event))
          (catch InterruptedException e
            ;; Intentionally pass.
            )
          (catch Throwable e
            (warn e)))
        (recur)))))
