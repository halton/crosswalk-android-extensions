// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ARDroneVideo extends XWalkExtensionClient {
    private static final String TAG = "ARDroneVideoExtension";

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
    }

    @Override
    public String onSyncMessage(int instanceID, String message) {
        return null;
    }
}
