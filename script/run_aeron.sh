#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

APP_NAME=$(echo "onyx-benchmark" | sed s/"-"/"_"/g)

exec java $MEDIA_DRIVER_JAVA_OPTS -cp /srv/onyx-benchmark.jar "$APP_NAME.aeron_media_driver" >>/var/log/aeron.log 2>&1
