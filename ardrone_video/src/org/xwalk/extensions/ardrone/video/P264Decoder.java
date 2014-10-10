// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.extensions.ardrone.video;

import java.io.IOException;
import java.io.InputStream;

/*
 * Usage:
 * int length = ParsePaVEHeader.parseHeader(inputStream);
 * byte[] bytes = ParsePaVEHeader.readPacket(inputStream, length);
 */

public class ParsePaVEHeader {
    private static final String TAG = "ParsePaVEHeader";

    public static final int unsignedIntBytes2Int(byte[] bytes) {
        int res = 0;
        if (bytes == null) {
            return res;
        }

        for (int i = 0; i < bytes.length; i++) {
            res = res | ((bytes[i] & 0xff) << i * 8);
        }
        return res;
    }

    public static final int parseHeader(InputStream inputStream) throws IOException {
        int length = 0;
        byte[] bytes = new byte[4];
        inputStream.read(bytes, 0, 4); // "PaVE".
        bytes = new byte[2];
        inputStream.read(bytes, 0, 2); // version and video_codec.
        bytes = new byte[2];
        inputStream.read(bytes, 0, 2); // header size.
        bytes = new byte[4];
        inputStream.read(bytes, 0, 4); // payload size. store as length.
        length = unsignedIntBytes2Int(bytes);
        bytes = new byte[4];
        inputStream.read(bytes, 0, 4); // stream width and height.
        bytes = new byte[2];
        inputStream.read(bytes, 0, 2); // display width.
        bytes = new byte[2];
        inputStream.read(bytes, 0, 2); // display height.
        bytes = new byte[56];
        inputStream.read(bytes, 0, 56); // ignored bytes for header. total 76 bytes.
        return length;
    }

    public static final byte[] readPacket(InputStream inputStream, int length) throws IOException {
        byte[] bytes = new byte[length];
        int index = 0;
        int position = 0;

        while (index < length) {
            position = inputStream.read(bytes, index, length - index);
            if (position <= 0) {
                break;
            }
            index = index + position;
        }

        return bytes;
    }
}
