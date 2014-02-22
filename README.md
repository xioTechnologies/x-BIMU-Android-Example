x-BIMU-Android-Example
======================

A bare bones Android example for for the [x-BIMU](https://github.com/xioTechnologies/x-BIMU-Terminal) with [Bluetooth module](http://www.x-io.co.uk/products/x-bimu-accessories/x-bimu-bluetooth-module/).  Code by [Phill Tew](http://rugcode.com/) with serial decoding based on the [x-BIMU Terminal](https://github.com/xioTechnologies/x-BIMU-Terminal) source-code and Bluetooth interface based on [BlueTerm](http://pymasde.es/blueterm/).

Getting started guide
---------------------

You will need to install the ADB USB driver specific for your Android platform if you have not done so already.

1. [Download eclipse](https://www.eclipse.org/downloads/)

2. Install Android Development Tools (ADT) plug-in (follow [this guide](http://developer.android.com/sdk/installing/installing-adt.html))

3. In Eclipse, install the necessary SDK components for your Android device via *Window > Android SDK Manager*.  *Android 2.2 (API 8)* is the minimum target platform.

4. Import this code project via *File > Import > Android > Existing Android Code Into Workspace*.  Specify the root directory of the code project (e.g. C:\...\x-BIMU-Android-Example), click *Refresh* and then select project before clicking *Finish*.

5. To run, click *Debug* and then select *Android Application*.  Note that Bluetooth won't work in emulator so you'll have to use your Android platform.
