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
import java.nio.channels.FileChannel;
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
        private File mH264File;
        private File mMp4File;

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

                    ++mCounter;
                    try {
                        if (mH264OutputStream != null) {
                            mH264OutputStream.flush();
                            mH264OutputStream.close();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }

                    mutexToMp4();
                    mVideoFileOdd = !mVideoFileOdd;
                    startTime = currentTime;
                    if (mCounter > 1) {
                        synchronized (mPauseLock) {
                            mPaused = true;
                        }
                    } else {
                        updateOutputStream();
                    }
                }

                try {
                    if (mH264OutputStream != null) {
                        int length = ParsePaVEHeader.parseHeader(mVideoStream);
                        byte[] bytes = ParsePaVEHeader.readPacket(mVideoStream, length);
                        mH264OutputStream.write(bytes);
                    }
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
            String fileNumber = mVideoFileOdd ? "1" : "2";
            mH264File = new File(mContext.getCacheDir(), fileNumber + ".h264");
            try {
                Log.i(TAG, "Current file is: " + mH264File.getAbsolutePath());
                mH264OutputStream = new FileOutputStream(mH264File, false);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        private void mutexToMp4() {
            Log.i(TAG, "Current h264 file is " + mH264File.getAbsolutePath());

            try {
                H264TrackImpl h264Track = new H264TrackImpl(new FileDataSourceImpl(mH264File.getAbsolutePath()));
                Movie m = new Movie();
                m.addTrack(h264Track);

                Container out = new DefaultMp4Builder().build(m);

                String fileNumber = mVideoFileOdd ? "1" : "2";
                mMp4File = new File(mH264File.getParent(), fileNumber + ".mp4");
                Log.i(TAG, "Current mp4 file is " + mMp4File.getAbsolutePath());

                FileOutputStream fos = new FileOutputStream(mMp4File);
                FileChannel fc = fos.getChannel();
                out.writeContainer(fc);
                fos.close();

            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

}
