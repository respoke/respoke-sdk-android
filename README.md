respoke-android-sdk
===================

This repository uses git submodules! Make sure to clone this repo using the --recursive flag in order to automatically clone the SDK submodule as well.


```
git clone --recursive https://<USER_NAME>@stash.digium.com/stash/scm/scl/respoke-sdk-android.git
```

Prerequisites:

Android Studio (I used v1.1)
Android NDK (I used r10b, Mac 64-bit)

https://developer.android.com/tools/sdk/ndk/index.html



Running the SDK test cases
==========================

The functional test cases that use RespokeCall require a specific Web application based on Respoke.js that is set up to automatically respond to certain actions that the SDK test cases perform. Because the web application will use audio and video, it requires special user permissions from browsers that support WebRTC and typically requires user interaction. Therefore it must run from either the context of a web server, or by loading the html file from the file system with specific command line parameters for Chrome. 

Additionally, the Android Studio test project has been set up to expect that the web application will connect to Respoke with a specific endpoint ID in the following format:

testbot-username

This username is the user that you are logged into your development computer with when you run the tests. This is done to avoid conflicts that can occur when multiple developers are running multiple instances of the test web application simultaneously. 

To set up your system to perform these tests, do one of the following:

A) Load the html from a file with Chrome. 

1) Run the following command to command Chrome to start the test bot and use a fake audio and video source during testing

"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    --use-fake-ui-for-media-stream \
    --use-fake-device-for-media-stream \
    --allow-file-access-from-files \
    ./RespokeSDKTests/WebTestBot/index.html &

2) Once the file has loaded, append your local username to the URL to match what Android Studio will search for as the tests run:

file:///projects/respoke-ios/RespokeSDK/RespokeSDKTests/WebTestBot/index.html#?un=jasonadams

3) Run the SDK test cases



B) Run with a local web server.


1) Install http-server

$ sudo npm i -g http-server

2) Start http-server from the testbot directory:

$ cd respokeSDK/WebTestBot/

$ http-server

3) Start Chrome using command line parameters to use fake audio/video and auto accept media permissions so that no human interaction is required:

$ /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --use-fake-ui-for-media-stream --use-fake-device-for-media-stream

This can alternately be done with Firefox by navigating to "about:config" and then setting the "media.navigator.permission.disabled" option to TRUE

4) Open the testbot in a Chrome tab by loading http://localhost:8080/#?un=username

5) Run the SDK test cases


Building the WebRTC libaries
============================

Unlike iOS, the WebRTC libraries for Android can ONLY be built on Linux. It WILL NOT work on Mac OS X. If, like me, you are only working with a Mac then one easy-ish approach is to create an Ubuntu virtual machine and perform the build there. Once the library has been built, it may be used inside of the Android Studio project on Mac again without issues.

The following steps are what was required for me to build the libraries using a trial version of VMWare and Ubuntu 14.04 LTS.

1) Download and install VMWare Fusion (I used v7.0)

https://www.vmware.com/products/fusion/features.html


2) Download the Ubuntu 14.04 LTS install image. Get the "64-bit Mac (AMD64)" image

http://www.ubuntu.com/download/desktop


3) Create a new virtual machine using the Ubuntu image. Give it at least 30GB of space to work with. I choose not to let it share home directories with my Mac user so it was easier to clean up afterwards.


4) Login to the new Ubuntu virtual machine desktop and open a terminal (Ctrl-Alt-T)


5) Install git

sudo apt-get install git


6) Configure Git for your Github credentials, and then clone the Respoke repository into your home directory

username@ubuntu:~$  git clone https://github.com/Ninjanetic/respoke-android.git


7) Install dependenices part 1

A series of scripts have been provided in the Respoke repository to make it easier to set up and run the build. It requires several steps and takes several hours to finish, so be prepared.

cd respoke-android
./build_webrtc_libs.sh install_dependencies
./build_webrtc_libs.sh install_jdk1_6
./build_webrtc_libs.sh pull_depot_tools


8) Close your terminal and reopen it so that you have the new JDK environment variables, or run:

source ~/.bashrc


9) Pull WebRTC source part 1

Due to a chicken-and-egg problem, we will attempt to pull the massive WebRTC source code and at some point it will fail. By that time, it will have grabbed another script with the commands necessary to install the dependencies that are missing.

./pull_webrtc_source.sh

This took me ~4 hours to finish. Eventually it will fail, complaining about missing Ubuntu packages.


10) Install dependencies part 2

A script now exists that will fortunately install the remaining dependencies for us, so run it:

src/build/install-build-deps-android.sh


11) Pull WebRTC source part 2. It should actually finish successfully this time!

./pull_webrtc_source.sh


12) Install last dependencies & build

The following command will install some last dependency packages and start the actual build in release mode. 

./build_webrtc_libs.sh build_apprtc



License
=======

The Respoke SDK and demo applications are licensed under the MIT license. Please see the [LICENSE](LICENSE) file for details.