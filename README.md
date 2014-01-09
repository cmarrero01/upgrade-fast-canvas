UPAGRADE FAST Canvas
==========

This is a copy of https://github.com/phonegap-build/UpgradeFastCanvas/, but with some changes for make compatibilities
with phonegap 3.+ and other stuff for fatest render and bug fixes.


UpgradeFastCanvas
==========

UpgradeFastCanvas is a Cordova/PhoneGap plugin which implements a very fast, 2D,
mostly-canvas-compatible rendering surface for Android. It focuses on moving 
graphics around on the screen quickly and efficiently using hardware 
acceleration.

Unlike the HTML5 canvas, UpgradeFastCanvas will encompass your entire screen and cannot be
integrated with other elements in the DOM.  It lives outside of the DOM in a separate 
rendering surface that fully covers all HTML content.  More on how UpgradeFastCanvas is displayed
to the screen is available in the [Architecture](#architecture) section. 
If you already have an application which uses a full screen DOM canvas, switching over 
to UpgradeFastCanvas could be an easy way to provide a boost in performance, and consistency within
that performance, to your mobile application or game.

While UpgradeFastCanvas attempts to look and behave very similar to the HTML5 canvas, it
only supports a subset of the HTML5 canvas API, focusing on what benefits most from
hardware acceleration.  More information about API support is described in the 
[UpgradeFastCanvas API](#UpgradeFastCanvas-api) section.


Getting Started
===============

UpgradeFastCanvas is supported on Android devices.  Additionally, your application must
be designed so that:

1. Your application runs a full screen canvas
2. Use of the 2D canvas API is limited to transforms and drawing images (see: 
[UpgradeFastCanvas API](#UpgradeFastCanvas-api))

For devices that don't support UpgradeFastCanvas, the API will fallback to using the standard
HTML canvas.  This is handled for you seamlessly making it easy to write cross-platform 
Canvas applications the use UpgradeFastCanvas on Android.

Prerequisites:
* [ADT for Eclipse](http://developer.android.com/sdk/index.html)
* [Cordova](http://cordova.apache.org/#download) or [PhoneGap](http://phonegap.com/download/) for Android (Getting started guides: [Cordova](http://cordova.apache.org/docs/en/2.7.0/guide_getting-started_android_index.md.html#Getting%20Started%20with%20Android), [PhoneGap](http://docs.phonegap.com/en/2.7.0/guide_getting-started_android_index.md.html#Getting%20Started%20with%20Android))
* [UpgradeFastCanvas](https://github.com/phonegap/phonegap-fast-canvas-plugin)


## Adding UpgradeFastCanvas to Your Application ##

1. Clone the UpgradeFastCanvas tree down from github (if you haven't already).
5. Copy `UpgradeFastCanvas\UpgradeFastCanvas.js` to your Java project's `assets\www` folder and include a reference to it in your `index.html`.
3. Copy the `UpgradeFastCanvas\Android\src\com` folder to your Java project's `src` folder.
4. Copy the `UpgradeFastCanvas\Android\libs\armeabi` folder to your Java project's `libs` folder.
5. In your `res/xml/config.xml` file add the following line to the end of the <plugins> section: 
   
```
<plugin name="UpgradeFastCanvas" value="com.adobe.plugins.UpgradeFastCanvas"/>
```

### Adding UpgradeFastCanvas using the PlugMan script ###

Additional prerequisites:
* [nodejs](http://nodejs.org/)
* [plugman](https://github.com/apache/cordova-plugman)

```
	npm install -g plugman
```

This is an alternate method, as used by the PhoneGap Build system.
After creating your Cordova app with `create`, run the PlugMan script as follows to install the UpgradeFastCanvas plugin:

```
	plugman --platform android --project <your project folder> --plugin https://github.com/UpgradeFastCanvas/UpgradeFastCanvas.git
```

#### If you have scale issues with your content ####
Adding the following to your `index.html` will often help:

```
	<meta name="viewport" content="user-scalable=no, initial-scale=1, maximum-scale=1, minimum-scale=1, width=device-width, height=device-height, target-densitydpi=device-dpi" />
```

#### Changing the Java interface ####

Additional prerequisites:
* Android NDK - the [Android NDK](http://developer.android.com/tools/sdk/ndk/index.html) is a separate download.
The native interface used by UpgradeFastCanvas is defined in `UpgradeFastCanvasJNI.java`.
If at any point in time you change `UpgradeFastCanvasJNI.java`, you'll also need to regenerate
`UpgradeFastCanvasJNI.h` and then recompile the native code.

These instructions are for Windows. If working on a Mac, use / instead of \, mv instead of move, etc.

1. Compile UpgradeFastCanvasJNI.java:
```
	\UpgradeFastCanvas\Android>javac -d . src\com\adobe\plugins\UpgradeFastCanvasJNI.java src\com\adobe\plugins\UpgradeFastCanvasTextureDimension.java
```

2. Create UpgradeFastCanvasJNI.h
```
	\UpgradeFastCanvas\Android>javah -jni com.adobe.plugins.UpgradeFastCanvasJNI
```
	
3. Move it to the correct location
```
	\UpgradeFastCanvas\Android>move com_adobe_plugins_UpgradeFastCanvasJNI.h jni\UpgradeFastCanvasJNI.h
```
	
4. Clean up
```
	\UpgradeFastCanvas\Android>rmdir /s com
```
	
5. Build the JNI library from the command line (Command Prompt or Terminal window): 
```
	\UpgradeFastCanvas\Android>[path to NDK]\ndk-build
```
	
	Should produce output similar to: 
```
		Compile++ thumb  : UpgradeFastCanvasJNI <= UpgradeFastCanvasJNI.cpp
		Compile++ thumb  : UpgradeFastCanvasJNI <= Canvas.cpp
		StaticLibrary    : libstdc++.a
		SharedLibrary    : libUpgradeFastCanvasJNI.so
		Install          : libUpgradeFastCanvasJNI.so => libs/armeabi/libUpgradeFastCanvasJNI.so
```


Using UpgradeFastCanvas in Your Project
================================

UpgradeFastCanvas was designed to mimic the standard HTML 2D Canvas to help make it easy to use
or implement in your existing canvas-based applications.  Like the HTML canvas, UpgradeFastCanvas
consists of both a canvas object and a context object.  The API is very similar with a few
exceptions:

* Canvas objects are created with `UpgradeFastCanvas.create()`
* Image objects are created with `UpgradeFastCanvas.createImage()`
* At the end of each frame a call to `UpgradeFastCanvas.render()` is required

These commands work with both UpgradeFastCanvas and HTML canvas applications.  Once you have your
application working with UpgradeFastCanvas, it will also work with an HTML canvas.

The following is a basic usage example of UpgradeFastCanvas shifting and rotating an
image drawn to the screen:

```javascript
var canvas = UpgradeFastCanvas.create(); // specific to UpgradeFastCanvas
var context = canvas.getContext("2d");
var myImage = UpgradeFastCanvas.createImage(); // specific to UpgradeFastCanvas
myImage.onload = function(){
   context.translate(100, 100);
   context.rotate(Math.PI);
   context.drawImage(myImage, 0, 0);
   UpgradeFastCanvas.render(); // specific to UpgradeFastCanvas
}
myImage.src = "images/myImage.jpg";
```

You can see much of the code matches usage of the HTML canvas.  `UpgradeFastCanvas.create()` will even create
an HTML canvas is UpgradeFastCanvas is not supported.  This is also the case
with `UpgradeFastCanvas.createImage()` which, depending on the result from `UpgradeFastCanvas.create()` will create
a standard HTML Image if an HTML canvas was created and a UpgradeFastCanvasImage object (images used by UpgradeFastCanvas)
when UpgradeFastCanvas is being used.  Usage of these image objects are largely the same - at least as far as
canvases are concerned - as you can see from the code used to load the image above.  Similarly, the API of 
the context returned from `UpgradeFastCanvas.getContext("2d")` matches that of HTML's CanvasRenderingContext2D object,
at least as far as UpgradeFastCanvas supports it. `UpgradeFastCanvas.render()` is an extra step specific
to UpgradeFastCanvas but will only do the necessary render step when a UpgradeFastCanvas is being used and will be a
no op (doing nothing) when an HTML canvas has been created.

In addition to the code changes, because UpgradeFastCanvas applications are full screen, your HTML should also include
the following meta tag to be assured that window metrics are reported accurately and consistently:

```html
   <meta name="viewport" content="user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, 
		width=device-width, height=device-height, target-densitydpi=device-dpi" />
```



Example Using UpgradeFastCanvas
========================

The game "Hungry Hero" has been re-implemented in JavaScript (from
AS3). It is a simple touch based flying game based on the Starling API.
We are currently using an approximation of Starling - a simple 
display list API - to wrap calls to the UpgradeFastCanvas plugin to do rendering.

https://github.com/phonegap/phonegap-fast-canvas-example


UpgradeFastCanvas API
==============

The bulk of the UpgradeFastCanvas API consists a subset of the methods within the
standard HTML5 canvas implementation of 
[CanvasRenderingContext2D](http://www.whatwg.org/specs/web-apps/current-work/multipage/the-canvas-element.html#canvasrenderingcontext2d) - 
the 2D context obtained when using `canvas.getContext("2d");`.  A goal in
designing UpgradeFastCanvas was to make it as close as possible to the existing
HTML5 canvas to make its easy and intuitive to use, and to make the 
process of porting an existing application to UpgradeFastCanvas as painless as possible.

The subset supported in UpgradeFastCanvas for its context includes:

| Member | Notes |
| ------ | ----- |
| context.clearRect(); | Not supported, but available (NOOP) |
| context.drawImage(image, sx, sy, sw, sh, dx, dy, dw, dh); | Supported |
| context.globalAlpha; | Supported |
| context.resetTransform(); | Supported |
| context.restore(); | Supported |
| context.rotate(angle); | Supported |
| context.save(); | Supported |
| context.scale(x, y); | Supported |
| context.setTransform(a, b, c, d, e, f); | Supported |
| context.transform(a, b, c, d, e, f); | Supported |
| context.translate(x, y); | Supported |

Additionally, UpgradeFastCanvas includes the following:

| Member | Notes |
| ------ | ----- |
| UpgradeFastCanvas.create(); | Creates a canvas object for you, UpgradeFastCanvas if available, otherwise a standard HTML canvas. |
| UpgradeFastCanvas.createImage(); | Creates an image object for you, UpgradeFastCanvasImage if a UpgradeFastCanvas was created in UpgradeFastCanvas.create(), otherwise a standard HTML Image. |
| UpgradeFastCanvas.render(); | To be called after all context calls are finished to commit the drawing to the screen. |
| UpgradeFastCanvas.setBackgroundColor(color); | Sets the canvas background (automatic for first time calling getContext()) |
| FastContext2D.capture(x,y,w,h,fileName, successCallback, errorCallback); | Saves the current state of the canvas as an image |


Architecture
============

UpgradeFastCanvas runs a Canvas(-like) implementation.

### On A Top Surface

UpgradeFastCanvas output will cover any HTML output - so you should generally avoid
HTML rendering as it will only consume performance. The UpgradeFastCanvas plugin
creates an OpenGL surface that sits on top of the browser.

### OpenGL renderer in C++

The renderer itself is OpenGL ES1.1 command streams, and the code
is written in C++. The advantage of C++ is both portability and
control of memory management. 

### Separate Thread

Your JS code runs in the browser thread, while most of the work
UpgradeFastCanvas does in in the UI thread. A tight stream of rendering commands
is sent between the threads. This allows some load balancing between
the threads, separation of the game from the renderer, and (in the 
future) downclocking the render thread.


Using UpgradeFastCanvas Efficiently
----------------------------

For best performance, minimize the number of draw calls per fram in the GL layer.

What that means at the JavaScript level is:
* Use sprite sheets
* Use as few textures as possible
* Avoid swapping textures in and out, and preload if possible.
* Try to batch drawImage calls that use the same texture. It is vastly more efficient to make ten drawImage calls in a row using one texture, and then make ten more using a second texture, than to switch back and forth twenty times.

