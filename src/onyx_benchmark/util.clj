(ns onyx-benchmark.util
  (:require [cheshire.core :refer [parse-string]]))

(defn peer? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "peer"))) tags)))

(defn zk? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "zookeeper"))) tags)))

(defn metrics? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "metrics"))) tags)))

(defn -main [file & args]
  (let [contents (parse-string (slurp file) true)
        instances (mapcat :Instances (:Reservations contents))]
    (spit "/home/ubuntu/zookeeper.txt" (:PublicDnsName (first (filter (fn [i] (zk? (:Tags i))) instances))))
    (spit "/home/ubuntu/metrics.txt" (:PublicDnsName (first (filter (fn [i] (metrics? (:Tags i))) instances))))))

