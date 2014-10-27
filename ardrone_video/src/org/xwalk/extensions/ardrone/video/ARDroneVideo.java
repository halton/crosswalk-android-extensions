// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

public class ARDroneVideo extends XWalkExtensionClient {
    private static final String TAG = "ARDroneVideoExtension";

    private boolean mIsInitialized = false;
    private ARDroneVideoOption mOption;
    private InputStream mVideoStream;
    private Thread mParse2RawH264Thread;
    private DecodeRunnable mRunnable;

    private Context mContext;

    public ARDroneVideo(String name, String jsApiContent, XWalkExtensionContextClient xwalkContext) {
        super(name, jsApiContent, xwalkContext);
        mContext = xwalkContext.getContext();
    }

    @Override
    public void onResume() {
        if (mRunnable != null) mRunnable.onResume();
    }

    @Override
    public void onPause() {
        if (mRunnable != null) mRunnable.onPause();
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
            if (cmd.equals("init")) {
                jsonOutput.put("data", handleInit(jsonInput.getJSONObject("option")));
            } else if (cmd.equals("play")) {
                jsonOutput.put("data", handlePlay());
            } else if (cmd.equals("stop")) {
                jsonOutput.put("data", handleStop());
            } else if (cmd.equals("removeFile")) {
                jsonOutput.put("data", handleRemoveFile(jsonInput.getString("path")));
            } else {
                jsonOutput.put("data", setErrorMessage("Unsupportted command: " + cmd));
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

    private JSONObject handleInit(JSONObject option) {
        mOption = new ARDroneVideoOption(option);
        if (mOption.codec() == ARDroneVideoCodec.UNKNOWN || mOption.channel() == ARDroneVideoChannel.UNKNOWN)
            return setErrorMessage("Wrong options passed in.");

        try {
            InetAddress address = null;
            // TODO(halton): use -java7 to support multiple catch
            //  catch (IOException | UnknownHostException e) {
            try {
                address = InetAddress.getByName(mOption.ipAddress());
            } catch (UnknownHostException e) {
                Log.e(TAG, e.toString());
                return setErrorMessage("Unknown host: " + mOption.ipAddress());
            }

            Socket socket = new Socket(address, mOption.port());
            mVideoStream = socket.getInputStream();
            mRunnable = new DecodeRunnable();
            mParse2RawH264Thread = new Thread(mRunnable);
            mParse2RawH264Thread.start();

            // Send out 'deviceready' event
            JSONObject out = new JSONObject();
            try {
                out.put("eventName", "deviceready");
                out.put("data", new JSONObject());

                broadcastMessage(out.toString());
            } catch (JSONException e) {
                printErrorMessage(e);
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return setErrorMessage("Failed to start video streaming.");
        }

        mIsInitialized = true;
        return new JSONObject();
    }

    private JSONObject handlePlay() {
        if (!mIsInitialized) {
            return setErrorMessage("Please initialize first.");
        }

        return new JSONObject();
    }

    private JSONObject handleStop() {
        cleanUp();
        return new JSONObject();
    }

    private JSONObject handleRemoveFile(String path) {
        File f = new File(path);

        if (!f.isFile()) return setErrorMessage("Invalid path: " + path);
        if (!f.delete()) return setErrorMessage("Failed to delete path: " + path);

        Log.i(TAG, "Successfully remove file: " + path);
        return new JSONObject();
    }

    private void cleanUp() {
        if (mRunnable != null) mRunnable.cleanUp();

        if (mParse2RawH264Thread != null) {
            mParse2RawH264Thread.interrupt();
            mParse2RawH264Thread = null;
        }
    }

    private class DecodeRunnable implements Runnable {
        private Object mPauseLock;
        private boolean mPaused;
        private boolean mFinished;

        private int mVideoCounter;
        private Date startTime;

        private File mVideoCachedDir;

        public DecodeRunnable() {
            mPauseLock = new Object();
            mPaused = false;
            mFinished = false;

            mVideoCounter = 0;
            startTime = new Date();

            mVideoCachedDir = null;
        }

        @Override
        public void run() {
            P264Decoder p264Decoder = new P264Decoder();

            if (mVideoCachedDir == null) {
                mVideoCachedDir = new File(mContext.getCacheDir() + "/video");
            }

            // Clean and recreated cached dir 
            if (mVideoCachedDir != null) deleteDir(mVideoCachedDir);
            mVideoCachedDir.mkdir();

            Mp4Muxer muxer = Mp4Muxer.createInstance(mVideoCachedDir);

            while (!mFinished) {
                Date currentTime = new Date();
                ++mVideoCounter;

                File mp4File = new File(mVideoCachedDir, mVideoCounter + ".mp4");
                Log.i(TAG, "Current mp4 file is " + mp4File.getAbsolutePath());

                try {
                    byte[] bytes = p264Decoder.readFrames(mVideoStream, mOption.latency());
                    if (bytes == null) continue;

                    Log.i(TAG, "Duration of " + mVideoCounter + " is: " + (currentTime.getTime() - startTime.getTime()));
                    startTime = currentTime;
                    muxer.h264FramesToMp4File(bytes, mp4File);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }

                if (mp4File.exists()) {
                    // Send out 'newvideoready' event
                    JSONObject out = new JSONObject();
                    try {
                        out.put("eventName", "newvideoready");
                        JSONObject path = new JSONObject();
                        path.put("absolutePath", mp4File.getAbsolutePath());
                        out.put("data", path);

                        broadcastMessage(out.toString());
                    } catch (JSONException e) {
                        printErrorMessage(e);
                    }
                }

                synchronized (mPauseLock) {
                    while (mPaused) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                }
            }
        }

        public void onPause() {
            synchronized (mPauseLock) {
                mPaused = true;
            }
        }

        public void onResume() {
            synchronized (mPauseLock) {
                mPaused = false;
                mPauseLock.notifyAll();
            }
        }

        public void cleanUp() {
            if (mVideoCachedDir != null) deleteDir(mVideoCachedDir);
        }

        private boolean deleteDir(File dir) {
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++) {
                    boolean success = deleteDir(new File(dir, children[i]));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        }

    }
}
