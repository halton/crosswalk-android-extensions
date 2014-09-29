// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

public class ARDroneVideo extends XWalkExtensionClient {
    private static final String TAG = "ARDroneVideoExtension";

    private ARDroneVideoOption mOption;

    public ARDroneVideo(String name, String jsApiContent, XWalkExtensionContextClient xwalkContext) {
        super(name, jsApiContent, xwalkContext);
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onStop() {
    }

    @Override
    public void onDestroy() {
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
        if (mOption.mCodec == ARDroneVideoCodec.UNKNOWN || mOption.mChannel == ARDroneVideoChannel.UNKNOWN)
            return setErrorMessage("Wrong options passed in.");

        Log.i(TAG, "From handlePlay()");
        JSONObject out = new JSONObject();
        return out;
    }

    private JSONObject handleStop() {
        JSONObject out = new JSONObject();
        return out;
    }
}
