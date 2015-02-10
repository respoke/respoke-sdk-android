#!/bin/sh

#
# Test runner for the CI server
#

trap "on_exit" EXIT

# Ensure cleanup runs on exit
function on_exit()
{
  if test -f chrome.pid; then
    kill $(cat chrome.pid)
    rm -f chrome.pid
  fi
}

set -ex

# Kill lingering instances of Google Chrome
killall -9 "Google Chrome" || true # ignore errors
while test $(ps aux | grep ^bamboo | grep -i [c]hrome | wc -l) -ne 0; do
  sleep 1
done

# Launch Google Chrome
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    --use-fake-ui-for-media-stream \
    --use-fake-device-for-media-stream \
    --allow-file-access-from-files \
    ./WebTestBot/index.html &
echo $! > chrome.pid

# Run the tests
./gradlew --stacktrace --debug clean connectedAndroidTest
