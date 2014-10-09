// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.util.Log;
import android.content.Context;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.H264TrackImpl;

import java.io.File;
import java.io.FileOutputStream;
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
            // ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            // executor.scheduleAtFixedRate(mVideoRunnable, 0, mOption.latency(), TimeUnit.MILLISECONDS);
            mRunnable = new DecodeRunnable();
            mParse2RawH264Thread = new Thread(mRunnable);
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
        private boolean mVideoFileOdd;
        private Date startTime;
        private int mCounter;

        private FileOutputStream mH264OutputStream;

        public DecodeRunnable() {
            mPauseLock = new Object();
            mPaused = false;
            mFinished = false;
            mVideoFileOdd = true;
            startTime = new Date();
            mCounter = 0;
        }

        @Override
        public void run() {
            updateOutputStream();

            while (!mFinished) {
                // Change filename in every mOption.latency() milliseconds.
                Date currentTime = new Date();
                if (currentTime.getTime() - startTime.getTime() >= mOption.latency()) {
                    try {
                        mH264OutputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }

                    mVideoFileOdd = !mVideoFileOdd;
                    startTime = currentTime;
                    updateOutputStream();
                }

                try {
                    int length = ParsePaVEHeader.parseHeader(mVideoStream);
                    byte[] bytes = ParsePaVEHeader.readPacket(mVideoStream, length);
                    mH264OutputStream.write(bytes);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }

                synchronized (mPauseLock) {
                    while (mPaused) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) {
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
          if (mH264OutputStream != null) {
              try {
                  mH264OutputStream.flush();
                  mH264OutputStream.close();
              } catch (IOException e) {
                  Log.e(TAG, e.toString());
              }
          }
        }

        private void updateOutputStream() {
            if (mCounter > 1) return;

            ++mCounter;
            String fileNumber = mVideoFileOdd ? "1" : "2";
            File file = new File(mContext.getCacheDir(), "raw" + fileNumber + ".h264");
            try {
                Log.i(TAG, "Current file is: " + file.getAbsolutePath());
                mH264OutputStream = new FileOutputStream(file, false);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

}
