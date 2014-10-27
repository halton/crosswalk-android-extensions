// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AndroidMediaMuxer extends Mp4Muxer {
    private static final String TAG = "AndroidMediaMuxer";

    // parameters for the muxer
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private int mFrameWidth;
    private int mFrameHeight;

    public AndroidMediaMuxer(int width, int height) {
        mFrameHeight = height;
        mFrameWidth = width;
    }

    @Override
    public boolean h264FramesToMp4File(byte[] bytes, File mp4File) {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(mp4File.getAbsolutePath(),
                                   MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mFrameWidth, mFrameHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        int videoTrackIndex = muxer.addTrack(format);

        ByteBuffer inputBuffer = ByteBuffer.wrap(bytes);
        BufferInfo bufferInfo = new BufferInfo();

        muxer.start();
        muxer.writeSampleData(videoTrackIndex, inputBuffer, bufferInfo);
        // FIXME(Halton): crash on stop()
        muxer.stop();
        muxer.release();

        return true;
    }
}
