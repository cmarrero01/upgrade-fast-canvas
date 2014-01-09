/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.json.JSONArray;

import android.app.Activity;
//import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES10;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;


public class UpgradeFastCanvasRenderer implements GLSurfaceView.Renderer {
	// ==========================================================================
	public UpgradeFastCanvasRenderer(UpgradeFastCanvasView view) {
		super();
		mView = view;
		// Frame limiter
		//startTime = System.currentTimeMillis();
	}

	// ==========================================================================
	private String mRenderCommands;
	private LinkedList<UpgradeFastCanvasMessage> mLocalQueue = new LinkedList<UpgradeFastCanvasMessage>();
	private List<UpgradeFastCanvasTexture> mTextures = new ArrayList<UpgradeFastCanvasTexture>();
	private List<UpgradeFastCanvasMessage> mCaptureQueue = new ArrayList<UpgradeFastCanvasMessage>();
	private UpgradeFastCanvasView mView;
	
	// Frame limiter
	//private long startTime;
	
	// ==========================================================================
	private void flushQueue() {
		synchronized( this ) {
			if (!UpgradeFastCanvas.copyMessageQueue(mLocalQueue)) {
				// Tear down. if any to be done.
				return;
			}
		}
		
		mRenderCommands = "";
		UpgradeFastCanvasMessage m;

		while ( mLocalQueue.size() > 0 ) {
			m = mLocalQueue.remove();
			if (m.type == UpgradeFastCanvasMessage.Type.LOAD) {
				Activity theActivity = UpgradeFastCanvas.getActivity();
				if ( theActivity != null ) {
					// If we are re-using a texture ID, unload the old texture
					for (int i = 0; i < mTextures.size(); i++) {
						if (mTextures.get(i).id == m.textureID) {
							unloadTexture(m.textureID);
							break;
						}
					}
					
					// Load and track the texture
					String path = "www/" + m.url;
					boolean success = false;
					UpgradeFastCanvasTextureDimension dim = new UpgradeFastCanvasTextureDimension();
					
					// See the following for why PNG files with premultiplied alpha and GLUtils don't get along
					// http://stackoverflow.com/questions/3921685/issues-with-glutils-teximage2d-and-alpha-in-textures
					if (path.toLowerCase(Locale.US).endsWith(".png")) {
						success = UpgradeFastCanvasJNI.addPngTexture(theActivity.getAssets(), path, m.textureID, dim);
						if (success == false) {
							Log.i("CANVAS", "CanvasRenderer loadTexture failed to load PNG in native code, falling back to GLUtils.");
						}
					} 

					if (success == false) {
						try {
							InputStream instream = theActivity.getAssets().open(path);
							final Bitmap bmp = BitmapFactory.decodeStream(instream);
							loadTexture(bmp, m.textureID);
							dim.width = bmp.getWidth();
							dim.height = bmp.getHeight();
							success = true;
						} catch (IOException e) {
							Log.i("CANVAS", "CanvasRenderer loadTexture error=", e);
							m.callbackContext.error( e.getMessage() );
						}
					}
					
					if (success == true) {
						UpgradeFastCanvasTexture t = new UpgradeFastCanvasTexture (m.url, m.textureID);
						mTextures.add(t);

						JSONArray args = new JSONArray();
						args.put(dim.width);
						args.put(dim.height);
						m.callbackContext.success(args);
					}
				}
			} else if (m.type == UpgradeFastCanvasMessage.Type.UNLOAD ) {
				// Stop tracking the texture
				for (int i = 0; i < mTextures.size(); i++) {
					if (mTextures.get(i).id == m.textureID) {
						mTextures.remove(i);
						break;
					}
				}
				unloadTexture(m.textureID);
			} else if (m.type == UpgradeFastCanvasMessage.Type.RELOAD ) {
				Activity theActivity = UpgradeFastCanvas.getActivity();
				if ( theActivity != null ) {
					// Reload the texture
					String path = "www/" + m.url;
					boolean success = false;
					
					if (path.toLowerCase(Locale.US).endsWith(".png")) {
						success = UpgradeFastCanvasJNI.addPngTexture(theActivity.getAssets(), path, m.textureID, null);
					} 

					if (success == false) {
						try {
							InputStream instream = theActivity.getAssets().open(path);
							final Bitmap bmp = BitmapFactory.decodeStream(instream);
							loadTexture(bmp, m.textureID);
						} catch (IOException e) {
							Log.i("CANVAS", "CanvasRenderer reloadTexture error=", e);
						}
					}
				}
			} else if (m.type == UpgradeFastCanvasMessage.Type.RENDER ) {
				mRenderCommands = m.drawCommands;
				while(!mCaptureQueue.isEmpty()) {
					UpgradeFastCanvasMessage captureMessage = mCaptureQueue.get(0);
					UpgradeFastCanvasJNI.captureGLLayer(captureMessage.callbackContext.getCallbackId(),captureMessage.x,
							captureMessage.y, captureMessage.width, captureMessage.height, captureMessage.url);
					mCaptureQueue.remove(0);
				}
			} else if (m.type == UpgradeFastCanvasMessage.Type.SET_ORTHO) {
				Log.i("CANVAS", "CanvasRenderer setOrtho width=" + m.width + ", height=" + m.height);
				UpgradeFastCanvasJNI.setOrtho(m.width, m.height);
			} else if(m.type == UpgradeFastCanvasMessage.Type.CAPTURE) {
				Log.i("CANVAS", "CanvasRenderer capture");
				mCaptureQueue.add(m);
			} else if (m.type == UpgradeFastCanvasMessage.Type.SET_BACKGROUND) {
				Log.i("CANVAS", "CanvasRenderer setBackground color=" + m.drawCommands);
				// Some validation of the background color string is
				// done in JS, but the format of m.drawCommands cannot
				// be fully validated so we're going to give this a shot
				// and simply fail silently if an error occurs in parsing
				try {
		            int red = Integer.valueOf( m.drawCommands.substring( 0, 2 ), 16 );
		            int green = Integer.valueOf( m.drawCommands.substring( 2, 4 ), 16 );
		            int blue = Integer.valueOf( m.drawCommands.substring( 4, 6 ), 16 );
		            UpgradeFastCanvasJNI.setBackgroundColor (red, green, blue);
				} catch(Exception e) {
					Log.e("CANVAS", "Parsing background color: \"" + m.drawCommands + "\"", e);
				}		
			}
		}
	}

	// ==========================================================================
	private void checkError() {
		int error = GLES10.glGetError();
		if (error != GLES10.GL_NO_ERROR) {
			Log.i("CANVAS", "CanvasRenderer glError=" + error);
		}
		assert error == GLES10.GL_NO_ERROR;
	}

	private boolean mDebugTextureChecked = false;
	private void debugTexture()
	{
		if ( !mDebugTextureChecked ) {/* Not for PGBuild
			synchronized( this ) {
				Activity theActivity = UpgradeFastCanvas.getActivity();
				if ( theActivity != null ) { 
					try {
					
						// grabbing debug font image from the library
						// project's resource folder rather than the
						// main projects assets directory
						Log.i("CANVAS", "Loading debug texture" );
						Resources res = UpgradeFastCanvas.getActivity().getResources();
						InputStream instream = res.openRawResource(R.drawable.debugfont);
						Bitmap tmp = BitmapFactory.decodeStream(instream);
						
						Bitmap bmp = Bitmap.createBitmap(tmp.getWidth(), tmp.getHeight(), Bitmap.Config.ARGB_8888);
						android.graphics.Canvas c = new android.graphics.Canvas();
						c.setBitmap(bmp);
						android.graphics.Paint p = new android.graphics.Paint();
						p.setFilterBitmap(true);
						c.drawBitmap(tmp, 0, 0, p);
						tmp.recycle();
						loadTexture(bmp, -1);
						bmp.recycle();
					} catch(Exception e) {
						Log.i("CANVAS", "Debug texture unavailable to load: " + e.getMessage());
					}
				}
			}*/
			mDebugTextureChecked = true;
		}
	}
	
	// ==========================================================================
	public void onDrawFrame(GL10 gl) {
		if (!mView.isPaused) {
			/*
			// Frame limiter.
			try {
				long endTime = System.currentTimeMillis();
			    long dt = endTime - startTime;
			    if (dt < 33)
			        Thread.sleep(33 - dt);
			    startTime = System.currentTimeMillis();
			} catch ( Exception e ) {
			}
			*/
			
			flushQueue();
			debugTexture();
		
			UpgradeFastCanvasJNI.render(mRenderCommands);
			checkError();
		}
	}

	// ==========================================================================
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Log.i("CANVAS", "CanvasRenderer onSurfaceCreated. config:" + config.toString()	+ " gl:" + gl.toString());

	    IntBuffer ib = IntBuffer.allocate(100);
	    ib.position(0);
	    GLES10.glGetIntegerv( GLES10.GL_RED_BITS, ib );
	    int red = ib.get(0);
	    GLES10.glGetIntegerv( GLES10.GL_GREEN_BITS, ib );
	    int green = ib.get(0);
	    GLES10.glGetIntegerv( GLES10.GL_BLUE_BITS, ib );
	    int blue = ib.get(0);
	    GLES10.glGetIntegerv( GLES10.GL_STENCIL_BITS, ib );
	    int stencil = ib.get(0);
	    GLES10.glGetIntegerv( GLES10.GL_DEPTH_BITS, ib );
	    int depth = ib.get(0);
	    
	    Log.i( "CANVAS", "CanvasRenderer R=" + red + " G=" + green + " B=" + blue + " DEPETH=" + depth + " STENCIL=" + stencil );
	}

	// ==========================================================================
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		Log.i("CANVAS", "CanvasRenderer onSurfaceChanged. width:" + width + " height:" + height + " gl:" + gl.toString());
		
		UpgradeFastCanvasJNI.surfaceChanged(width, height);
	}

	// ==========================================================================
	// Not an override - this is a way for the view to tell the renderer when the context has been destroyed
	public void onSurfaceDestroyed() {
		Log.i("CANVAS", "CanvasRenderer onSurfaceDestroyed");
		
		mDebugTextureChecked = false;
	}

	// ==========================================================================
	public void loadTexture(Bitmap bmp, int id) {
		if (bmp == null) {
			Log.i("CANVAS", "CanvasRenderer Aborting loadtexture " + id);
			return;
		}
		
		int[] glID = new int[1];
	    GLES10.glGenTextures(1, glID, 0);
	    GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, glID[0]);
	    GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_LINEAR);
	    GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_LINEAR);

		int width = bmp.getWidth();
		int height = bmp.getHeight();
		int p2Width = 2;
		while (p2Width < width) {
			p2Width *= 2;
		}

		int p2Height = 2;
		while (p2Height < height) {
			p2Height *= 2;
		}

        if (width == p2Width && height == p2Height) {
    		GLUtils.texImage2D(GLES10.GL_TEXTURE_2D, 0, bmp, 0);
        } else {
			Log.i( "Canvas", "Canvas::AddTexture scaling texture " + id + " to power of 2" );
			GLES10.glTexImage2D(GLES10.GL_TEXTURE_2D, 0, GLES10.GL_RGBA, p2Width, p2Height, 0, GLES10.GL_RGBA, GLES10.GL_UNSIGNED_BYTE, null);
			GLUtils.texSubImage2D(GLES10.GL_TEXTURE_2D, 0, 0, 0, bmp);
			width = p2Width;
			height = p2Height;
        }

		checkError();
	    
		UpgradeFastCanvasJNI.addTexture(id, glID[0], width, height);
		Log.i("CANVAS", "CanvasRenderer Leaving loadtexture " + id);
	}

	// ==========================================================================
	public void unloadTexture( int id) {
		UpgradeFastCanvasJNI.removeTexture(id);
		Log.i("CANVAS", "CanvasRenderer unloadtexture");
		checkError();
	}

	// ==========================================================================
	public void reloadTextures() {
		Log.i("CANVAS", "CanvasRenderer reloadtextures");
		Iterator<UpgradeFastCanvasTexture> ti = mTextures.iterator();
		while (ti.hasNext()) {
			UpgradeFastCanvasTexture t = ti.next();
			UpgradeFastCanvasMessage m = new UpgradeFastCanvasMessage(UpgradeFastCanvasMessage.Type.RELOAD);
			m.url = t.url;
			m.textureID = t.id;
			Log.i("CANVAS", "CanvasRenderer queueing reload texture " + m.textureID + ", " + m.url);
			mLocalQueue.add(m);
		}
	}
}
