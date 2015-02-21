(ns onyx-benchmark.util
  (:require [cheshire.core :refer [parse-string]]))

(defn in-stack? [stack-name tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-name") (= (:Value t) stack-name))) tags)))

(defn in-region? [stack-region tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-region") (= (:Value t) stack-region))) tags)))

(defn peer? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "peer"))) tags)))

(defn zk? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "zookeeper"))) tags)))

(defn metrics? [tags]
  (seq (filter (fn [t] (and (= (:Key t) "stack-role") (= (:Value t) "metrics"))) tags)))

(defn -main [stack-name stack-region file & args]
  (let [contents (parse-string (slurp file) true)
        instances (mapcat :Instances (:Reservations contents))]
    (spit "/home/ubuntu/zookeeper.txt" (:PublicDnsName (first (filter (fn [i] (and (zk? (:Tags i))
                                                                                  (in-stack? stack-name (:Tags i))
                                                                                  (in-region? stack-region (:Tags i))))
                                                                      instances))))
    (spit "/home/ubuntu/metrics.txt" (:PublicDnsName (first (filter (fn [i] (and (metrics? (:Tags i))
                                                                                (in-stack? stack-name (:Tags i))
                                                                                (in-region? stack-region (:Tags i))))
                                                                    instances))))))

