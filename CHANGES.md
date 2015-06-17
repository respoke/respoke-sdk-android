Respoke Android SDK Change Log
==============================

v1.0.4
------
* Added a new "push" parameter to `RespokeGroup.sendMessage()` and `RespokeEndpoint.sendMessage()` that allows you to indicate if a specific message should be considered (true) or ignored (false) by the push notification service. Use this to explicitly ignore certain messages (like meta data) that should never be sent to mobile devices as a push notification.

* Added a new `Respoke.unregisterPushServices()` method that allows you to explicitly unregister a device from receiving push notifications in the future.

* Updated the AndroidAsync, Google Play Services, and App Support dependency libraries to the latest available versions

* Improved the instructions in the README on how to run the SDK test cases

* Miscellaneous test case fixes