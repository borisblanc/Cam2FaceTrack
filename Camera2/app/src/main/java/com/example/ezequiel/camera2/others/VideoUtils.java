
package com.example.ezequiel.camera2.others;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

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


    public static void mergeVideos(String outputfilepath, String FirstVideoPath, String SecondVideoPath) {

        Movie[] clips = new Movie[2];
        try {
        //location of the movie clip storage
        //File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TestMerge");

        //Build the two clips into movies

        Movie firstClip = MovieCreator.build(FirstVideoPath);
        Movie secondClip = MovieCreator.build(SecondVideoPath);

        //Add both movie clips
        clips[0] = firstClip;
        clips[1] = secondClip;

        //List for audio and video tracks
        List<Track> videoTracks = new LinkedList<>();
        List<Track> audioTracks = new LinkedList<>();

        //Iterate all the movie clips and find the audio and videos
        for (Movie movie: clips) {
            for (Track track : movie.getTracks()) {
                if (track.getHandler().equals("soun"))
                    audioTracks.add(track);
                if (track.getHandler().equals("vide"))
                    videoTracks.add(track);
            }
        }

        //Result movie from putting the audio and video together from the two clips
        Movie result = new Movie();

        //Append all audio and video
        if (videoTracks.size() > 0)
            result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));

        if (audioTracks.size() > 0)
            result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));

        //Output the resulting movie to a new mp4 file
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        String outputLocation = mediaStorageDir.getPath() + timeStamp;
        Container out = new DefaultMp4Builder().build(result);
        FileChannel fc = new RandomAccessFile(String.format(outputfilepath), "rw").getChannel();

        out.writeContainer(fc);

        fc.close();
        } catch (IOException e) {
            Log.d(LOGTAG, "Merge Error " + e.getMessage());
        }
    }


}