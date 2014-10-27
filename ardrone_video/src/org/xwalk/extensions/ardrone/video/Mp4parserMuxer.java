// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.H264TrackImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;

public class Mp4parserMuxer extends Mp4Muxer {
    private static final String TAG = "Mp4parserMuxer";

    private File mVideoCachedDir;

    public Mp4parserMuxer(File cachedDir) {
        mVideoCachedDir = cachedDir;
    }

    @Override
    public boolean h264FramesToMp4File(byte[] bytes, File mp4File) {
        File h264File = new File(mVideoCachedDir,
                                 removeExtension(mp4File.getAbsolutePath()) + ".h264");
        Log.i(TAG, "Current h264 file is: " + h264File.getAbsolutePath() + "buffer size:" + bytes.length);

        FileOutputStream h264OutputStream = null;
        try {
            h264OutputStream = new FileOutputStream(h264File, false);
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.toString());
        }

        try {
            h264OutputStream.write(bytes);
            h264OutputStream.flush();
            h264OutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        H264TrackImpl h264Track = null;
        try {
            h264Track = new H264TrackImpl(new FileDataSourceImpl(h264File.getAbsolutePath()));
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        Movie m = new Movie();
        m.addTrack(h264Track);
        Container out = new DefaultMp4Builder().build(m);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mp4File);
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.toString());
        }

        FileChannel fc = fos.getChannel();
        try {
            out.writeContainer(fc);
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        h264File.delete();
        return true;
    }

    // This function is to strip path and suffix of a filename.
    // For eg: /tmp/test1.mp4 -> test1
    private String removeExtension(String s) {
        // Remove the path upto the filename.
        int lastSeparatorIndex = s.lastIndexOf(System.getProperty("file.separator"));
        String filename = (lastSeparatorIndex == -1) ? s : s.substring(lastSeparatorIndex + 1);

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1)
            return filename;

        return filename.substring(0, extensionIndex);
    }
}
