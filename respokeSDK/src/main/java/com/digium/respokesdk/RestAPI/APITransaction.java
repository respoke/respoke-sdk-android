/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdk.RestAPI;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;


public class APITransaction {

	private static final String TAG = "ApiTransaction";
    public static final String RESPOKE_BASE_URL = "https://api.respoke.io";

    /**
     * Rejects a message if the body size is greater than this. It is enforced server side, so changing this
     * won't make the bodySizeLimit any bigger, this just gives you a sensible error if it's too big.
     */
    public static final long bodySizeLimit = 20000;

	public boolean abort;
    public boolean success;
    public String errorMessage;
    public JSONObject jsonResult;
    public String baseURL;
    public String contentType;
    Context context;
    
    private HttpURLConnection connection;
    protected String httpMethod;
    protected String params;
    protected int serverResponseCode;
    private AsyncTransaction asyncTrans;
    
    
    public APITransaction(Context context, String baseURL) {
    	this.context = context;
    	abort = false;
        httpMethod = "POST";
        params = null;
        this.baseURL = baseURL;
        contentType = "application/x-www-form-urlencoded";
    }


    public void go() {
    	asyncTrans = new AsyncTransaction();
    	asyncTrans.execute(this.httpMethod);    	
    }

	
    public void transactionComplete() {
		// This method is overridden by child classes
	}

	
	public void cancel() {
	    abort = true;
	    if (asyncTrans != null) {
	    	asyncTrans.cancel();
	    }
	}

	
	private class AsyncTransaction extends AsyncTask<String, Integer, Object> {
		
		public void cancel() {
			if (connection != null) {
				Log.e(TAG, "connection cancelled!");
				connection.disconnect();
			}
			this.cancel(true);
		}
		
		@Override
		protected Object doInBackground(String... args) {
			
			String httpMethod = args[0];
			
			// clear any previous received data
            jsonResult = null;

            try {
                if (params.getBytes("UTF-8").length <= bodySizeLimit) {
                    try {
                        //accept no cookies
                        CookieManager cookieManager = new CookieManager();
                        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_NONE);
                        CookieHandler.setDefault(cookieManager);

                        System.setProperty("http.keepAlive", "false");

                        URI uri = new URI(baseURL.replace(" ", "%20"));
                        URL url = new URL(uri.toASCIIString());
                        connection = (HttpURLConnection) url.openConnection();

                        // Allow Inputs & Outputs
                        connection.setRequestMethod(httpMethod);
                        connection.setDoInput(true);
                        connection.setUseCaches(false);
                        if (httpMethod.equals("POST")) {
                            connection.setDoOutput(true);
                        }

                        //Headers
                        connection.setRequestProperty("Content-Type", contentType);
                        connection.setRequestProperty("Accept", "application/xml");

                        if (httpMethod.equals("POST")) {
                            //open stream and start writing
                            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                            outputStream.writeBytes(params);
                            outputStream.flush();
                            outputStream.close();
                        }

                        serverResponseCode = connection.getResponseCode();

                        if (serverResponseCode == 200) {
                            success = true;

                            // Parse the response data into a string
                            String receivedString = readResponse(connection.getInputStream());

                            if (receivedString != null) {
                                try {
                                    // Parse the response string into JSON objects
                                    jsonResult = new JSONObject(receivedString);
                                } catch (JSONException e) {
                                    errorMessage = "Error deserializing response";
                                    success = false;
                                }
                            }
                        } else {
                            throw new IOException(Integer.toString(serverResponseCode));
                        }

                    } catch (IOException e) {
                        success = false;
                        errorMessage = e.getLocalizedMessage();

                        try {
                            Log.e(TAG, "serverResponseCode = " + connection.getResponseCode());
                            Log.e(TAG, "serverResponseMessage = " + connection.getResponseMessage());
                            serverResponseCode = connection.getResponseCode();
                            String serverResponseMessage = connection.getResponseMessage();

                            if (serverResponseCode == 401) {
                                errorMessage = "API authentication error";
                            } else if (serverResponseCode == 429) {
                                errorMessage = "API rate limit was exceeded";
                            } else if (serverResponseCode == 503) {
                                errorMessage = "Server is down for maintenance";
                            } else if (serverResponseCode >= 400) {
                                errorMessage = "Failed with server error = " + serverResponseCode + " message = " + serverResponseMessage;
                            } else {
                                errorMessage = "Unknown Error. http status = " + serverResponseCode + " message = " + serverResponseMessage;
                            }
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                    } catch (URISyntaxException e) {
                        Log.e(TAG, "Bad URI!");
                        errorMessage = "An invalid server URL was specified";
                        success = false;
                    } catch (Exception e) {
                        Log.e(TAG, "Unknown exception");
                        errorMessage = "An unknown problem occurred";
                        success = false;
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                } else {
                    errorMessage = "Request body is too big";
                    success = false;
                }
            } catch (UnsupportedEncodingException e) {
                errorMessage = "Unable to encode message";
                success = false;
            }

			return jsonResult;
		}

	    
		//Process after transaction is complete
		@Override
		protected void onPostExecute(Object result)
        {
			transactionComplete();
		}

		
		private String readResponse(InputStream stream) {
            String receivedString = null;

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                br.close();
                receivedString = sb.toString();
            } catch (IOException ioe) {
                // Do nothing
            }

			return receivedString;
		}
	}
}
