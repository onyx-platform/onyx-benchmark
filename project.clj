(defproject onyx-benchmark "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :java-opts ^:replace ["-server" 
                        "-Xmx20g"
                        ;"-XX:BiasedLockingStartupDelay=0" 
                        ;"-Daeron.mtu.length=16384" 
                        ;"-Daeron.socket.so_sndbuf=2097152" 
                        ;"-Daeron.socket.so_rcvbuf=2097152" 
                        ;"-Daeron.rcv.buffer.length=16384" 
                        ;"-Daeron.rcv.initial.window.length=2097152" 
                        ;"-Dagrona.disable.bounds.checks=true"

                        "-XX:+UnlockCommercialFeatures" "-XX:+FlightRecorder" 
                        "-XX:+UnlockDiagnosticVMOptions" 
                        "-XX:StartFlightRecording=duration=1080s,filename=myrecording.jfr" 
                        ; "-XX:+TraceClassLoading" "-XX:+LogCompilation" "-XX:+PrintAssembly"
                        ]

  :profiles {:bench {:dependencies [[org.onyxplatform/onyx "0.0.0-BENCH"]]
                     :java-opts ^:replace ["-server" 
                                           "-Xmx6g"
                                           "-XX:BiasedLockingStartupDelay=0" 
                                           "-Daeron.mtu.length=16384" 
                                           "-Daeron.socket.so_sndbuf=2097152" 
                                           "-Daeron.socket.so_rcvbuf=2097152" 
                                           "-Daeron.rcv.buffer.length=16384" 
                                           "-Daeron.rcv.initial.window.length=2097152" 
                                           "-Dagrona.disable.bounds.checks=true"

                                           "-XX:+UnlockCommercialFeatures" "-XX:+FlightRecorder" 
                                           "-XX:+UnlockDiagnosticVMOptions" 
                                           "-XX:StartFlightRecording=duration=1080s,filename=localrecording.jfr"]}}


  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars  {*warn-on-reflection* true 
                 *assert* false
                 *unchecked-math* :warn-on-boxed}
  :java-source-paths ["src/java"]
  :dependencies [[org.clojure/clojure "1.7.0"]
		 ;[org.onyxplatform/onyx "0.8.2"]
		 [org.onyxplatform/onyx "0.8.3-SNAPSHOT"]
		 [org.onyxplatform/onyx-metrics "0.8.2.8-20151204.182937-8"]
		 [riemann-clojure-client "0.4.1"]
                 [gnuplot "0.1.0"]
		 [cheshire "5.4.0"]])
