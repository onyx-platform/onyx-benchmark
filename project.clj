(defproject onyx-benchmark "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :java-opts ^:replace ["-server" 
                        "-Xmx12g"
                        "-XX:BiasedLockingStartupDelay=0" 
                        "-Daeron.mtu.length=16384" 
                        "-Daeron.socket.so_sndbuf=2097152" 
                        "-Daeron.socket.so_rcvbuf=2097152" 
                        "-Daeron.rcv.buffer.length=16384" 
                        "-Daeron.rcv.initial.window.length=2097152" 
                        "-Dagrona.disable.bounds.checks=true"

                        "-XX:+UnlockCommercialFeatures" "-XX:+FlightRecorder" "-XX:StartFlightRecording=duration=1080s,filename=myrecording.jfr" "-XX:+UnlockDiagnosticVMOptions" 
                        ; "-XX:+TraceClassLoading" "-XX:+LogCompilation" "-XX:+PrintAssembly"
                        ]

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars  {*warn-on-reflection* true 
                 *assert* false
                 *unchecked-math* :warn-on-boxed}
  :java-source-paths ["src/java"]
  :dependencies [[org.clojure/clojure "1.7.0"]
		 [org.onyxplatform/onyx "0.8.1-SNAPSHOT"]
		 [org.onyxplatform/onyx-metrics "0.8.0.4-20151116.130304-2"]
                 ;; TODO, bump to 0.4.1
		 [riemann-clojure-client "0.3.2" :exclusions [io.netty/netty]]
		 [cheshire "5.4.0"]])
