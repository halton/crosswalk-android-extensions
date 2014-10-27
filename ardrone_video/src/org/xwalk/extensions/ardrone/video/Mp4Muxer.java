// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.os.Build;
import java.io.File;

public abstract class Mp4Muxer {

    public static Mp4Muxer createInstance(File cachedDir) {
        // TODO (halton): Switch to use AndroidMediaMuxer when it is not crashed on muxer.stop()
        //
        // return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ?
        //        new AndroidMediaMuxer(480, 320) : new Mp4parserMuxer(cachedDir);
        return new Mp4parserMuxer(cachedDir);
    }

    public abstract boolean h264FramesToMp4File(byte[] bytes, File mp4File);
}
