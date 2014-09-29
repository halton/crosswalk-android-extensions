// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ARDroneVideoOption {
    private static final String TAG = "ARDroneVideoOption";

    public String mIpAddress;
    public long mPort;
    public long mLatency;
    public long mBitrate;
    public ARDroneVideoCodec mCodec;
    public ARDroneVideoChannel mChannel;

    public ARDroneVideoOption(JSONObject option) {
        try {
            mIpAddress = option.getString("ipAddress");
            mPort = option.getLong("port");
            mLatency = option.getLong("latency");
            mBitrate = option.getLong("bitrate");

            long codec = option.getLong("codec");
            boolean found = false;
            for (ARDroneVideoCodec c : ARDroneVideoCodec.values()) {
                if (codec == c.getValue()) {
                    mCodec = c;
                    found = true;
                    break;
                }
            }
            if (!found) mCodec = ARDroneVideoCodec.UNKNOWN;

            long channel = option.getLong("channel");
            found = false;
            for (ARDroneVideoChannel c : ARDroneVideoChannel.values()) {
                if (channel == c.getValue()) {
                    mChannel = c;
                    found = true;
                    break;
                }
            }
            if (!found) mChannel = ARDroneVideoChannel.UNKNOWN;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }
}
