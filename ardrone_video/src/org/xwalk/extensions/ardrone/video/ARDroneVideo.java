// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.util.Log;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

public class ARDroneVideo extends XWalkExtensionClient {
    private static final String TAG = "ARDroneVideoExtension";

    private ARDroneVideoOption mOption;
    private InputStream mVideoStream;
    private Thread mParse2RawH264Thread;
    private FileOutputStream mH264OutputStream;

    private Context mContext;

    public ARDroneVideo(String name, String jsApiContent, XWalkExtensionContextClient xwalkContext) {
        super(name, jsApiContent, xwalkContext);
        mContext = xwalkContext.getContext();
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
        cleanUp();
    }

    @Override
    public void onStop() {
        cleanUp();
    }

    @Override
    public void onDestroy() {
        cleanUp();
    }

    @Override
    public void onMessage(int instanceID, String message) {
        if (message.isEmpty()) return;
        Log.i(TAG, "Receive message: " + message);

        try {
            JSONObject jsonInput = new JSONObject(message);
            String cmd = jsonInput.getString("cmd");

            JSONObject jsonOutput = new JSONObject();
            if (cmd.equals("play")) {
                jsonOutput.put("data", handlePlay(jsonInput.getJSONObject("option")));
            } else if (cmd.equals("stop")) {
                jsonOutput.put("data", handleStop());
            } else {
                jsonOutput.put("data", setErrorMessage("Unsupportted cmd " + cmd));
            }

            jsonOutput.put("asyncCallId", jsonInput.getString("asyncCallId"));
            this.postMessage(instanceID, jsonOutput.toString());
        } catch (JSONException e) {
            printErrorMessage(e);
        }
    }

    @Override
    public String onSyncMessage(int instanceID, String message) {
        return null;
    }

    private void printErrorMessage(JSONException e) {
        Log.e(TAG, e.toString());
    }

    private JSONObject setErrorMessage(String error) {
        JSONObject out = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        try {
            errorMessage.put("message", error);
            out.put("error", errorMessage);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return out;
    }

    private JSONObject handlePlay(JSONObject option) {
        mOption = new ARDroneVideoOption(option);
        if (mOption.codec() == ARDroneVideoCodec.UNKNOWN || mOption.channel() == ARDroneVideoChannel.UNKNOWN)
            return setErrorMessage("Wrong options passed in.");

        Log.i(TAG, "From handlePlay()");
        try {
            InetAddress address = null;
            // TODO(halton): use -java7 to support multiple catch
            //  catch (IOException | UnknownHostException e) {
            try {
                address = InetAddress.getByName(mOption.ipAddress());
            } catch (UnknownHostException e) {
                Log.e(TAG, e.toString());
            }

            Socket socket = new Socket(address, mOption.port());
            mVideoStream = socket.getInputStream();
            File cacheDir = mContext.getCacheDir();
            File file = new File(cacheDir, "test.h264");
            mH264OutputStream = new FileOutputStream(file, true);
            mParse2RawH264Thread = new Thread(new Runnable() {
                @Override 
                public void run() {
                    while (true) {
                        try {
                            int length = ParsePaVEHeader.parseHeader(mVideoStream);
                            byte[] bytes = ParsePaVEHeader.readPacket(mVideoStream, length);
                            mH264OutputStream.write(bytes);
                        } catch (IOException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                }
            });
            mParse2RawH264Thread.start();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        return new JSONObject();
    }

    private JSONObject handleStop() {
        cleanUp();
        return new JSONObject();
    }

    private void cleanUp() {
        if (mParse2RawH264Thread != null) {
            mParse2RawH264Thread.interrupt();
            mParse2RawH264Thread = null;
        }

        if (mH264OutputStream != null) {
            try {
                mH264OutputStream.flush();
                mH264OutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}
