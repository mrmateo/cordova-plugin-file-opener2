/*
The MIT License (MIT)

Copyright (c) 2013 pwlin - pwlin05@gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package io.github.pwlin.cordova.plugins.fileopener2;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.Runnable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaResourceApi;

public class FileOpener2 extends CordovaPlugin {

	/**
	 * Executes the request and returns a boolean.
	 * 
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackContext
	 *            The callback context used when calling back into JavaScript.
	 * @return boolean.
	 */
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("open")) {
			this._open(args.getString(0), args.getString(1), callbackContext);
		} 
		else if (action.equals("uninstall")) {
			this._uninstall(args.getString(0), callbackContext);
		}
		else if (action.equals("appIsInstalled")) {
			JSONObject successObj = new JSONObject();
			if (this._appIsInstalled(args.getString(0))) {
				successObj.put("status", PluginResult.Status.OK.ordinal());
				successObj.put("message", "Installed");
			}
			else {
				successObj.put("status", PluginResult.Status.NO_RESULT.ordinal());
				successObj.put("message", "Not installed");
			}
			callbackContext.success(successObj);
		}
		else {
			JSONObject errorObj = new JSONObject();
			errorObj.put("status", PluginResult.Status.INVALID_ACTION.ordinal());
			errorObj.put("message", "Invalid action");
			callbackContext.error(errorObj);
		}
		return true;
	}

	private void _open(final String fileArg, final String contentType, final CallbackContext callbackContext) throws JSONException {
		if (fileArg == null || fileArg.isEmpty()) {
		    JSONObject errorObj = new JSONObject();
		    errorObj.put("status", PluginResult.Status.ERROR.ordinal());
		    errorObj.put("message", "File passed in was empty");
		    callbackContext.error(errorObj);
            return;
		}
        final CordovaResourceApi resourceApi = webView.getResourceApi();
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Uri fileUri = Uri.parse(fileArg);
                    try {
                        fileUri = resourceApi.remapUri(fileUri);
                        Log.w(FileOpener2.class.getName(), "Parsed url, using this to find file: " + fileArg);
                    } catch (Exception e) {
                        Log.w(FileOpener2.class.getName(), "Error while trying to parse url.", e);
                    }
                    int uriType = resourceApi.getUriType(fileUri);
                    File file;
                    if (uriType == CordovaResourceApi.URI_TYPE_ASSET || uriType == CordovaResourceApi.URI_TYPE_HTTPS) {
                        // copy to local storage
                        try {
                            String fileName = new File(fileUri.getPath()).getName();
                            Uri cacheFileUri = Uri.parse("file://" + cordova.getActivity().getExternalCacheDir().getAbsolutePath() + "/" + fileName);
                            Log.w(FileOpener2.class.getName(), "Caching file to: " + cacheFileUri.getPath());
                            resourceApi.copyResource(fileUri, cacheFileUri);
                            fileUri = cacheFileUri;
                            Log.w(FileOpener2.class.getName(), "File propertly cached.");
                        } catch (Exception e) {
                            Log.w(FileOpener2.class.getName(), "Error while trying to cache file.", e);
                        }

                    }
                    file = resourceApi.mapUriToFile(fileUri);
                    //File file = new File(fileName);
                    if (file != null && file.exists()) {
                        try {
                            Uri path = Uri.fromFile(file);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(path, contentType);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            /*
                                * @see
                                * http://stackoverflow.com/questions/14321376/open-an-activity-from-a-cordovaplugin
                                */
                                cordova.getActivity().startActivity(intent);
                            //cordova.getActivity().startActivity(Intent.createChooser(intent,"Open File in..."));
                            callbackContext.success();
                        } catch (android.content.ActivityNotFoundException e) {
                            JSONObject errorObj = new JSONObject();
                            errorObj.put("status", PluginResult.Status.ERROR.ordinal());
                            errorObj.put("message", "Activity not found: " + e.getMessage());
                            callbackContext.error(errorObj);
                        }
                    } else {
                        JSONObject errorObj = new JSONObject();
                        errorObj.put("status", PluginResult.Status.ERROR.ordinal());
                        errorObj.put("message", "File not found");
                        callbackContext.error(errorObj);
                    }
                } catch (JSONException je) {
                    Log.w(FileOpener2.class.getName(), "Error while trying to generate an error.", je);
                }
            }
        });
	}
	
	private void _uninstall(String packageId, CallbackContext callbackContext) throws JSONException {
		if (this._appIsInstalled(packageId)) {
			Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
			intent.setData(Uri.parse("package:" + packageId));
			cordova.getActivity().startActivity(intent);
			callbackContext.success();
		}
		else {
			JSONObject errorObj = new JSONObject();
			errorObj.put("status", PluginResult.Status.ERROR.ordinal());
			errorObj.put("message", "This package is not installed");
			callbackContext.error(errorObj);
		}
	}
	
	private boolean _appIsInstalled(String packageId) {
		PackageManager pm = cordova.getActivity().getPackageManager();
        boolean appInstalled = false;
        try {
            pm.getPackageInfo(packageId, PackageManager.GET_ACTIVITIES);
            appInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            appInstalled = false;
        }
        return appInstalled;
	}

	private String stripFileProtocol(String uriString) {
		if (uriString.startsWith("file://")) {
			uriString = uriString.substring(7);
		}
		return uriString;
	}

}
