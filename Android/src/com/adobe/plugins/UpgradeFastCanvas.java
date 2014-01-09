/*
 Copyright 2013 Adobe Systems Inc.;
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.adobe.plugins;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

public class UpgradeFastCanvas extends CordovaPlugin {
	// These go null at shutdown. Access with null check and synchronized()
	private BlockingQueue<UpgradeFastCanvasMessage> mMessageQueue;
	private Activity mActivity;
	private CordovaWebView mCordovaView;
	private UpgradeFastCanvasView mCanvasView;
    
	private static UpgradeFastCanvas theCanvas = null;
	
	@Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		Log.i("CANVAS", "UpgradeFastCanvas initialize");
		super.initialize(cordova, webView);
		mMessageQueue = new LinkedBlockingQueue<UpgradeFastCanvasMessage>();
		mActivity = cordova.getActivity();
		mCanvasView = new UpgradeFastCanvasView(mActivity);
		theCanvas = this;
		mCordovaView = webView;
		initView();
	}
	
	@Override
    public void onDestroy() {
		Log.i("CANVAS", "UpgradeFastCanvas onDestroy" );
		UpgradeFastCanvasJNI.release();
		
		mMessageQueue = null;
		mActivity = null;
		mCordovaView = null;
		mCanvasView = null;
		theCanvas = null;
    }
	
	@Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		//Log.i("CANVAS", "UpgradeFastCanvas execute: " + action);
		if ( mMessageQueue == null ) {
			Log.i("CANVAS", "UpgradeFastCanvas messageQueue is NULL in execute.");
			return true;
		}
		
		try {
			
		if (action.equals("setBackgroundColor")) {
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.SET_BACKGROUND);
			m.drawCommands = args.getString(0);
			Log.i("CANVAS", "UpgradeFastCanvas queueing set background color " + m.drawCommands);
			mMessageQueue.add(m);
			return true;
				
		} else if (action.equals("loadTexture")) {
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.LOAD);
			m.url = args.getString(0);
			m.textureID = args.getInt(1);
			assert callbackContext != null;
			m.callbackContext = callbackContext;
			Log.i("CANVAS", "UpgradeFastCanvas queueing load texture " + m.textureID + ", " + m.url);
			mMessageQueue.add(m);
			return true;
				
		} else if (action.equals("unloadTexture")) {
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.UNLOAD);
			m.textureID = args.getInt(0);
			Log.i("CANVAS", "UpgradeFastCanvas queueing unload texture " + m.textureID);
			mMessageQueue.add(m);
			return true;
				
		} else if (action.equals("render")) {
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.RENDER);
			m.drawCommands = args.getString(0);
			mMessageQueue.add(m);
			return true;
				
		} else if (action.equals("setOrtho")) {
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.SET_ORTHO);
			int width = args.getInt(0);
			int height = args.getInt(1);
			m.width = width;
			m.height = height;
			Log.i("CANVAS", "UpgradeFastCanvas queueing setOrtho, width=" + m.width + ", height=" + m.height);
			mMessageQueue.add(m);
			return true;
				
		} else if(action.equals("capture")) {
                //set the root path to /mnt/sdcard/
                String fileLocation = Environment.getExternalStorageDirectory() +args.getString(4);
                String justPath = fileLocation.substring(0, fileLocation.lastIndexOf('/'));
                File directory = new File(justPath);
                if(!directory.isDirectory()) {
                    //doesn't exist try to make it
                    if(!directory.mkdirs()) {
                        //failed to make directory, callback with error
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Could not create directory");
                        callbackContext.sendPluginResult(result);
                        return true;
                    }
                }
                
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.CAPTURE);
			m.x = args.optInt(0, 0);
			m.y = args.optInt(1,0);
			m.width = args.optInt(2,-1);
			m.height = args.optInt(3,-1);
                m.url = fileLocation;
			
			Log.i("CANVAS","UpgradeFastCanvas queueing capture");
			if(callbackContext != null)
				m.callbackContext = callbackContext;
			mMessageQueue.add(m);
			return true;

		} else if (action.equals("isAvailable")) {
			// user is checking to see if we exist
			// simply reply with a successful success callback
			// if we're not installed, cordova will call
			// the error callback for us
			PluginResult result = new PluginResult(PluginResult.Status.OK, true);
            callbackContext.sendPluginResult(result);
			// if for some other reason we are installed but 
			// cannot function, the result of OK above can be sent
			// but with a value of false which indicates unavailability
			// (may require not disabling the view if that's the case)
			return true;
			
		} else {
			Log.i("CANVAS", "UpgradeFastCanvas unknown execute action " + action);
		}
			
		}catch(Exception e){
			String argStr = "";
			try {
				argStr = args.join(",");
			}catch(Exception ignore){}
			
			Log.e("CANVAS", "Unexpected error parsing execute parameters for action " + action + "(" + argStr + ")", e);
		}
		
		return false;
    }

	public void initView() {
		Log.i("CANVAS", "UpgradeFastCanvas initView");
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final RelativeLayout top = new RelativeLayout(mActivity);
				top.setLayoutParams(new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT,
						RelativeLayout.LayoutParams.MATCH_PARENT));
				top.addView(mCanvasView);
				mActivity.setContentView(top);
			} // end run
		}); // end runnable
	} // initView
	
	public static void executeCallback(String callbackID, boolean isError, String result) {
		if (theCanvas == null) {
			return;
		}
		
		PluginResult res;
		
		if(isError) 
			 res = new PluginResult(PluginResult.Status.ERROR,result);
		else
			 res = new PluginResult(PluginResult.Status.OK,result);
		
		theCanvas.mCordovaView.sendPluginResult(res, callbackID);
	}

	public static boolean dispatchTouchEvent (MotionEvent event) {
		if ( theCanvas == null || theCanvas.mCanvasView == null ) {
			return false;				
		}
		else {
			theCanvas.mCordovaView.dispatchTouchEvent(event);
		}

		return true;
	}
	
	public static boolean dispatchKeyDown (int keyCode, KeyEvent event) {
		if ( theCanvas == null || theCanvas.mCordovaView == null ) {
			return false;				
		}
		else {
			return theCanvas.mCordovaView.onKeyDown(keyCode, event);
		}
	}
	
	public static boolean dispatchKeyUp (int keyCode, KeyEvent event) {
		if ( theCanvas == null || theCanvas.mCordovaView == null ) {
			return false;				
		}
		else {
			return theCanvas.mCordovaView.onKeyUp(keyCode, event);
		}
	}
	
	public static Activity getActivity() {
		Activity theActivity = null;
		if (theCanvas != null) {
			theActivity = theCanvas.mActivity;
		}
		return theActivity;
	}
	
	public static boolean copyMessageQueue(LinkedList<UpgradeFastCanvasMessage> targetQueue) {
		if (theCanvas == null || theCanvas.mMessageQueue == null) {
			return false;
		}
		
		UpgradeFastCanvasMessage m;
		while (( m = theCanvas.mMessageQueue.poll()) != null ) {
			targetQueue.add( m );
		}

		return true;
	}
}
