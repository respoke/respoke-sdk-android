Respoke SDK for Android
=======================

The Respoke SDK for Android makes it easy to add live voice, video, text, and data features to your mobile app. For information on how to use the SDK, take a look at our developer documentation and sample apps here:

[https://docs.respoke.io/](https://docs.respoke.io/)

Installing the SDK
=============

The Respoke Android SDK is available to install via the Maven Central repository.
You can [download the latest JAR directly](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.digium.respoke%22%20AND%20a%3A%22respoke-sdk%22), or install via Maven:

```
<dependency>
    <groupId>com.digium.respoke</groupId>
    <artifactId>respoke-sdk</artifactId>
    <version>(insert latest version)</version>
</dependency>
```

Gradle:

```
dependencies {
    compile 'com.digium.respoke:respoke-sdk:1.+'
}
```

Contributing
============

We welcome pull requests to improve the SDK for everyone. When submitting changes, please make sure you have run the SDK test cases before submitting and added/modified any tests that are affected by your improvements.

Running the SDK test cases
==========================

The SDK test cases require UI elements and so are contained in a test application named "RespokeSDKTest" in this repo. To run the test cases, do the following:

1) Clone this repo onto your development machine.

2) Open Android Studio, choose "Import Project" and select the root directory of the repo.

3) Start the web TestBot in either Chrome or Firefox as described in the section "Starting the Web TestBot" below

4) In the project navigator, expand the RespokeSDKTest module and control-click (or right-click) on the Java -> "com.digium.respokesdktest (androidtest)" group. Select "Run 'All Tests' with coverage"

5) The test cases will run, displaying the results inside of Android Studio. You will also see debug messages and video displayed in the web browser running the TestBot.

Starting the Web TestBot
========================

The functional test cases that use RespokeCall require a specific Web application based on Respoke.js that is set up to automatically respond to certain actions that the SDK test cases perform. Because the web application will use audio and video, it requires special user permissions from browsers that support WebRTC and typically requires user interaction. Therefore it must run from either the context of a web server, or by loading the html file from the file system with specific command line parameters for Chrome. 

Additionally, the Android Studio test project has been set up to expect that the web application will connect to Respoke with a specific endpoint ID in the following format:

testbot-username

This username is the user that you are logged into your development computer with when you run the tests. This is done to avoid conflicts that can occur when multiple developers are running multiple instances of the test web application simultaneously. 

To set up your system to perform these tests, do one of the following:

#### A) Load the html from a file with Chrome.


1) You can use command line parameters to load the test bot with Chrome tell it to use a fake audio and video source during testing. On Mac OS, the command would look like this:

    $ "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    --use-fake-ui-for-media-stream \
    --use-fake-device-for-media-stream \
    --allow-file-access-from-files \
    ./WebTestBot/index.html &

2) Once the file has loaded, append your local username to the URL to match what Android Studio will search for as the tests run:

    file:///respoke-android-sdk/WebTestBot/index.html#?un=mymacusername

3) Run the SDK test cases



#### B) Run with a local web server.


1) Install http-server

    $ sudo npm i -g http-server

2) Start http-server from the testbot directory:

    $ cd WebTestBot/
    $ http-server

3) Start Chrome using command line parameters to use fake audio/video and auto accept media permissions so that no human interaction is required:

    $ /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --use-fake-ui-for-media-stream --use-fake-device-for-media-stream

This can alternately be done with Firefox by navigating to "about:config" and then setting the "media.navigator.permission.disabled" option to TRUE

4) Open the TestBot in a Chrome tab by loading http://localhost:8080/#?un=mymacusername

5) Run the SDK test cases



License
=======

The Respoke SDK and demo applications are licensed under the MIT license. Please see the [LICENSE](LICENSE) file for details.
