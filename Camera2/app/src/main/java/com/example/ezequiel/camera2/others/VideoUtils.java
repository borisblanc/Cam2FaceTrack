/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Modified example based on mp4parser google code open source project.
// http://code.google.com/p/mp4parser/source/browse/trunk/examples/src/main/java/com/googlecode/mp4parser/ShortenExample.java
package com.example.ezequiel.camera2.others;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class VideoUtils {


    private static final String LOGTAG = "VideoUtils";
    private static final int DEFAULT_BUFFER_SIZE = 1 * 1024 * 1024;

    /**
     * Remove the sound track.
     */
//    public static void startMute(String filePath, SaveVideoFileInfo dstFileInfo) throws IOException {
//            genVideoUsingMuxer(filePath, dstFileInfo.mFile.getPath(), -1.0, -1.0, false, true);
//    }




    /**
     * @param srcPath  the path of source video file.
     * @param dstPath  the path of destination video file.
     * @param startMs  starting time in milliseconds for trimming. Set to
     *                 negative if starting from beginning.
     * @param endMs    end time for trimming in milliseconds. Set to negative if
     *                 no trimming at the end.
     * @param useAudio true if keep the audio track from the source.
     * @param useVideo true if keep the video track from the source.
     * @throws IOException
     */
    public static void genTrimVideoUsingMuxer(String srcPath, String dstPath, Long startMs, Long endMs, boolean useAudio, boolean useVideo) throws IOException {
        // Set up MediaExtractor to read from the source.
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcPath);
        int trackCount = extractor.getTrackCount();
        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // Set up the tracks and retrieve the max buffer size for selected
        // tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        int bufferSize = -1;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            boolean selectCurrentTrack = false;
            if (mime.startsWith("audio/") && useAudio) {
                selectCurrentTrack = true;
            } else if (mime.startsWith("video/") && useVideo) {
                selectCurrentTrack = true;
            }
            if (selectCurrentTrack) {
                extractor.selectTrack(i);
                int dstIndex = muxer.addTrack(format);
                indexMap.put(i, dstIndex);
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    bufferSize = newSize > bufferSize ? newSize : bufferSize;
                }
            }
        }
        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE;
        }
        // Set up the orientation and starting time for extractor.
        MediaMetadataRetriever retrieverSrc = new MediaMetadataRetriever();
        retrieverSrc.setDataSource(srcPath);
        String degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (degreesString != null) {
            int degrees = Integer.parseInt(degreesString);
            if (degrees >= 0) {
                muxer.setOrientationHint(degrees);
            }
        }
        if (startMs > 0) {
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        int offset = 0;
        int trackIndex = -1;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        BufferInfo bufferInfo = new BufferInfo();
        muxer.start();
        while (true) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                Log.d(LOGTAG, "Saw input EOS.");
                bufferInfo.size = 0;
                break;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                if (endMs > 0 && bufferInfo.presentationTimeUs > (endMs * 1000)) {
                    Log.d(LOGTAG, "The current sample is over the trim end time.");
                    break;
                } else {
                    bufferInfo.flags = extractor.getSampleFlags();
                    trackIndex = extractor.getSampleTrackIndex();
                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    extractor.advance();
                }
            }
        }
        muxer.stop();
        muxer.release();
    }

    public static void muxVideos(String outputfilepath, String FirstVideoPath, String SecondVideoPath, Context mycontext) {

        try {


            MediaExtractor videoExtractor1 = new MediaExtractor();
            videoExtractor1.setDataSource(FirstVideoPath);

            MediaExtractor videoExtractor2 = new MediaExtractor();
            videoExtractor2.setDataSource(SecondVideoPath);

            Log.d(LOGTAG, "Video1 Extractor Track Count " + videoExtractor1.getTrackCount());
            Log.d(LOGTAG, "Video2 Extractor Track Count " + videoExtractor2.getTrackCount());

            MediaMuxer muxer = new MediaMuxer(outputfilepath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoExtractor1.selectTrack(0);
            MediaFormat videoFormat1 = videoExtractor1.getTrackFormat(0);
            int videoTrack1 = muxer.addTrack(videoFormat1);

            videoExtractor2.selectTrack(0);
            MediaFormat videoFormat2 = videoExtractor2.getTrackFormat(0);
            int videoTrack2 = muxer.addTrack(videoFormat2);

            Log.d(LOGTAG, "Video1 Format " + videoFormat1.toString());
            Log.d(LOGTAG, "Video2 Format " + videoFormat2.toString());

            boolean sawEOS = false;
            int frameCount = 0;
            int offset = 100;
            int sampleSize = 256 * 1024;
            ByteBuffer videoBuf1 = ByteBuffer.allocate(sampleSize);
            ByteBuffer videoBuf2 = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo1 = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo videoBufferInfo2 = new MediaCodec.BufferInfo();


            videoExtractor1.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            videoExtractor2.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            muxer.start();

            while (!sawEOS) {
                videoBufferInfo1.offset = offset;
                videoBufferInfo1.size = videoExtractor1.readSampleData(videoBuf1, offset);


                if (videoBufferInfo1.size < 0 || videoBufferInfo2.size < 0) {
                    Log.d(LOGTAG, "saw input EOS.");
                    sawEOS = true;
                    videoBufferInfo1.size = 0;

                } else {
                    videoBufferInfo1.presentationTimeUs = videoExtractor1.getSampleTime();
                    videoBufferInfo1.flags = videoExtractor1.getSampleFlags();
                    muxer.writeSampleData(videoTrack1, videoBuf1, videoBufferInfo1);
                    videoExtractor1.advance();


                    frameCount++;
                    Log.d(LOGTAG, "Frame (" + frameCount + ") Video PresentationTimeUs:" + videoBufferInfo1.presentationTimeUs + " Flags:" + videoBufferInfo1.flags + " Size(KB) " + videoBufferInfo1.size / 1024);
                    Log.d(LOGTAG, "Frame (" + frameCount + ") Audio PresentationTimeUs:" + videoBufferInfo2.presentationTimeUs + " Flags:" + videoBufferInfo2.flags + " Size(KB) " + videoBufferInfo2.size / 1024);

                }
            }

            Toast.makeText(mycontext.getApplicationContext(), "frame:" + frameCount, Toast.LENGTH_SHORT).show();


            boolean sawEOS2 = false;
            int frameCount2 = 0;
            while (!sawEOS2) {
                frameCount2++;

                videoBufferInfo2.offset = offset;
                videoBufferInfo2.size = videoExtractor2.readSampleData(videoBuf2, offset);

                if (videoBufferInfo1.size < 0 || videoBufferInfo2.size < 0) {
                    Log.d(LOGTAG, "saw input EOS.");
                    sawEOS2 = true;
                    videoBufferInfo2.size = 0;
                } else {
                    videoBufferInfo2.presentationTimeUs = videoExtractor2.getSampleTime();
                    videoBufferInfo2.flags = videoExtractor2.getSampleFlags();
                    muxer.writeSampleData(videoTrack2, videoBuf2, videoBufferInfo2);
                    videoExtractor2.advance();


                    Log.d(LOGTAG, "Frame (" + frameCount + ") Video PresentationTimeUs:" + videoBufferInfo1.presentationTimeUs + " Flags:" + videoBufferInfo1.flags + " Size(KB) " + videoBufferInfo1.size / 1024);
                    Log.d(LOGTAG, "Frame (" + frameCount + ") Audio PresentationTimeUs:" + videoBufferInfo2.presentationTimeUs + " Flags:" + videoBufferInfo2.flags + " Size(KB) " + videoBufferInfo2.size / 1024);

                }
            }

            Toast.makeText(mycontext.getApplicationContext(), "frame:" + frameCount2, Toast.LENGTH_SHORT).show();

            muxer.stop();
            muxer.release();


        } catch (IOException e) {
            Log.d(LOGTAG, "Mixer Error 1 " + e.getMessage());
        } catch (Exception e) {
            Log.d(LOGTAG, "Mixer Error 2 " + e.getMessage());
        }


    }


}