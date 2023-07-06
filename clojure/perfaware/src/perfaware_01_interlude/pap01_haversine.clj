(ns perfaware-01-interlude.pap01-haversine
  "Performance Aware Programming | 01-Interlude | The Haversine Distance Problem
  ---
  Haversine distance problem examples and experiments.
  See: https://www.computerenhance.com/p/the-haversine-distance-problem
  ---
  Findings so far:
  - nippy is insanely fast!
  - json is insanely slow! lol"
  (:require
    [clojure.java.io :as io]
    [clojure.core.reducers :as r]
    [criterium.core :as crit]
    [jsonista.core :as jsonista]
    [tech.v3.dataset :as ds]
    [tech.v3.dataset.reductions :as ds-reduce]
    [tech.v3.datatype.functional :as dfn]
    [tech.v3.datatype :as dtype]
    [tech.v3.io :as tio])
  (:import (java.io File)
           (java.time Duration)))

(set! *warn-on-reflection* true)

(defn nanos->seconds [nanos]
  (/ nanos 1000000000))

(defn timestamps->duration
  [^long start ^long end]
  (let [diff (- start end)
        dur (Duration/ofNanos diff)]
    (str (double (/ (.toMillis dur) 1000)) "s" "//" diff "ns")))

(def haversine-json-file (File. "haversine-data.json"))
(def haversine-nippy-file (File. "haversine-data.nippy"))

(defn create-example-json [num-entries]
  (let [rand-lat (fn [] (+ -90.0 (* 180 (rand))))
        rand-lng (fn [] (+ -180.0 (* 360 (rand))))
        example-data (take num-entries (repeatedly (fn []
                                                     {:x0 (rand-lat)
                                                      :y0 (rand-lng)
                                                      :x1 (rand-lat)
                                                      :y1 (rand-lng)})))]
    example-data))

(defn write-example-json! [data]
  (jsonista/write-value haversine-json-file data))


(defn haversine-of-degrees [x0 y0 x1 y1 r]
  (let [dY (Math/toRadians (- y1 y0))
        dX (Math/toRadians (- x1 x0))
        y0 (Math/toRadians y0)
        y1 (Math/toRadians y1)

        root-term (+ (Math/pow (Math/sin (/ dY 2.0)) 2.0)
                     (* (Math/cos y0)
                        (Math/cos y1)
                        (Math/pow (Math/sin (/ dX 2.0)) 2.0)))
        result (* 2 r (Math/asin (Math/sqrt root-term)))]
    result))


(def ^:const earth-radius-km 6371)

(defn calc-throughput [cnt start-time end-time]
  (str (double (* (/ cnt (nanos->seconds (- end-time start-time))))) " haversines/second"))

(defn perfaware-01-interlude-haversine--single []
  (def start-time (crit/timestamp))
  (let [json-input (jsonista/read-value (slurp haversine-json-file))
        _ (def mid-time (crit/timestamp))
        [sum cnt] (reduce (fn [[sum cnt] x]
                            [(+ sum (haversine-of-degrees (get x "x0") (get x "y0") (get x "x1") (get x "y1") earth-radius-km))
                             (inc cnt)])
                          [0 0]
                          json-input)
        average (/ sum cnt)
        _ (def end-time (crit/timestamp))]
    {:result     average
     :input      (timestamps->duration mid-time start-time)
     :math       (timestamps->duration end-time mid-time)
     :total      (timestamps->duration end-time start-time)
     :throughput (calc-throughput cnt start-time end-time)}))

(defn perfaware-01-interlude-haversine--multi []
  (def start-time (crit/timestamp))
  (let [json-input (jsonista/read-value (slurp haversine-json-file))
        _ (def mid-time (crit/timestamp))

        cnt (count json-input)
        results (pmap
                  (fn [x] (haversine-of-degrees (get x "x0") (get x "y0") (get x "x1") (get x "y1") earth-radius-km))
                  json-input)
        sum (reduce + results)
        average (/ sum cnt)
        _ (def end-time (crit/timestamp))]
    {:result     average
     :input      (timestamps->duration mid-time start-time)
     :math       (timestamps->duration end-time mid-time)
     :total      (timestamps->duration end-time start-time)
     :throughput (calc-throughput cnt start-time end-time)}))

(defn perfaware-01-interlude-haversine--dtype []
  (def start-time (crit/timestamp))
  (let [json-input (ds/->dataset (io/as-relative-path haversine-json-file) {:file-type :json})
        _ (def mid-time (crit/timestamp))
        cnt (ds/row-count json-input)
        sum (-> json-input
                (ds/row-map (fn [{:strs [y1 x1 y0 x0]}]
                              {:haversine (haversine-of-degrees x0 y0 x1 y1 earth-radius-km)}))
                :haversine
                (dfn/sum))
        average (/ sum cnt)
        _ (def end-time (crit/timestamp))]
    {:result     average
     :input      (timestamps->duration mid-time start-time)
     :math       (timestamps->duration end-time mid-time)
     :total      (timestamps->duration end-time start-time)
     :throughput (calc-throughput cnt start-time end-time)}))

(defn perfaware-01-interlude-haversine--dtype-nippy []
  (def start-time (crit/timestamp))
  (let [input-ds (ds/->dataset (io/as-relative-path haversine-nippy-file))
        _ (def mid-time (crit/timestamp))
        cnt (ds/row-count input-ds)
        sum (-> input-ds
                (ds/row-map (fn [{:strs [y1 x1 y0 x0]}]
                              {:haversine (haversine-of-degrees x0 y0 x1 y1 earth-radius-km)}))
                :haversine
                (dfn/sum))
        average (/ sum cnt)
        _ (def end-time (crit/timestamp))]
    {:result     average
     :input      (timestamps->duration mid-time start-time)
     :math       (timestamps->duration end-time mid-time)
     :total      (timestamps->duration end-time start-time)
     :throughput (calc-throughput cnt start-time end-time)}))

(comment
  (perfaware-01-interlude-haversine--single)       ; 27.57s  362638hav/sec
  (perfaware-01-interlude-haversine--multi)        ; 41.62s  240247hav/sec
  (perfaware-01-interlude-haversine--dtype)        ; 25.47s  392542hav/sec
  (perfaware-01-interlude-haversine--dtype-nippy)  ;  1.51s 6581792hav/sec

  (crit/timestamp)

  (let [start 531508
        end 12451313135135]
    (timestamps->duration start end))

  (io/as-url haversine-json-file)

  (-> (create-example-json 10000000)
      (write-example-json!))

  (get (jsonista/read-value (slurp haversine-json-file)) "pairs")

  (double (* (/ 10000000 (nanos->seconds (- end-time start-time)))))

  (crit/quick-bench
    (-> (ds/->dataset (io/as-relative-path haversine-json-file) {:file-type :json})
        (ds/row-map (fn [{:strs [y1 x1 y0 x0]}]
                      {:haversine (haversine-of-degrees x0 y0 x1 y1 earth-radius-km)}))
        :haversine
        (dfn/sum)))
  ;; =>
  ;Evaluation count : 6 in 6 samples of 1 calls.
  ;             Execution time mean : 2,569583 sec
  ;    Execution time std-deviation : 128,853749 ms
  ;   Execution time lower quantile : 2,398952 sec ( 2,5%)
  ;   Execution time upper quantile : 2,719499 sec (97,5%)
  ;                   Overhead used : 5,498143 ns

  (crit/quick-bench
    (let [ds (-> (ds/->dataset (io/as-relative-path haversine-json-file) {:file-type :json})
                 (ds/row-map (fn [{:strs [y1 x1 y0 x0]}]
                               {:haversine (haversine-of-degrees x0 y0 x1 y1 earth-radius-km)})))]))



  ;; Write the example dataset out to a nippy serialized file.
  (->> (ds/->dataset (io/as-relative-path haversine-json-file) {:file-type :json})
       (tio/put-nippy! (io/as-relative-path haversine-nippy-file)))



  (crit/quick-bench (apply + (range 10000000)))
  ;; =>
  ;Evaluation count : 6 in 6 samples of 1 calls.
  ;             Execution time mean : 181,748862 ms
  ;    Execution time std-deviation : 28,864650 ms
  ;   Execution time lower quantile : 160,089195 ms ( 2,5%)
  ;   Execution time upper quantile : 216,466295 ms (97,5%)
  ;                   Overhead used : 5,340811 ns
  (crit/quick-bench (reduce + (range 10000000)))
  ;;=>
  ;Evaluation count : 6 in 6 samples of 1 calls.
  ;             Execution time mean : 162,545762 ms
  ;    Execution time std-deviation : 34,843321 ms
  ;   Execution time lower quantile : 134,938095 ms ( 2,5%)
  ;   Execution time upper quantile : 216,119383 ms (97,5%)
  ;                   Overhead used : 5,340811 ns
  (crit/quick-bench (r/reduce + (range 10000000)))
  ;;=>
  ;Evaluation count : 6 in 6 samples of 1 calls.
  ;             Execution time mean : 178,965578 ms
  ;    Execution time std-deviation : 39,438446 ms
  ;   Execution time lower quantile : 152,253195 ms ( 2,5%)
  ;   Execution time upper quantile : 232,970158 ms (97,5%)
  ;                   Overhead used : 5,340811 ns
  (crit/quick-bench (r/fold + (range 10000000)))
  ;;=>
  ;Evaluation count : 6 in 6 samples of 1 calls.
  ;             Execution time mean : 176,491012 ms
  ;    Execution time std-deviation : 28,367042 ms
  ;   Execution time lower quantile : 153,016895 ms ( 2,5%)
  ;   Execution time upper quantile : 208,302495 ms (97,5%)
  ;                   Overhead used : 5,340811 ns


  #_())
