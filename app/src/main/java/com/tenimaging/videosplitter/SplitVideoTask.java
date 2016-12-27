/*
 * Copyright (c) 2016. 10 Imaging Inc.
 */
package com.tenimaging.videosplitter;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.Locale;

import wseemann.media.FFmpegMediaMetadataRetriever;


/**
 * Asynchronous task used to split a video into its individual frames
 */
public class SplitVideoTask extends AsyncTask<Void,Void,Void> {
    private static final String TAG = "SplitVideoTask";     // Marks log messages made by this class
    private static final long MICRO_ONE_SECOND = 1000000;   // number of microseconds in one second
    private static final long MILLI_ONE_SECOND = 1000;      // number of milliseconds in one second
    private File mFile = null;                      // Video file we are going to split
    private File mOutDir = null;                    // Output dir where frames are saved
    private ListView mListView;                     // displays progress of this task
    private ProgressAdapter mProgAdapter;           // used to update this tasks progress
    private FFmpegMediaMetadataRetriever mVideo;    // used to read in video frames
    private int mNumFrames;                         // max number of frames in video
    private long mFrameLength;                      // time length in microseconds between frames we want
    private int mFrame;                             // current frame number we are working on
    private int mId;                                // ID that identifies this task and the associated progress UI element
    private boolean mCanceled = false;              // Defines if we have canceled this tasks
    private double mSkipRatio;                      // Ratio of frames to skip


    /**
     * constructor for this class
     * @param videoFile File of the video we want to split
     * @param outDir    Directory location to save frames into
     * @param skipRatio Ratio of frames we are going to skip
     * @param listView  View that displays the progress of these tasks
     */
    public SplitVideoTask(File videoFile, File outDir, double skipRatio, ListView listView) {
        super();

        // Make sure we can access the video file and output directory
        if ( videoFile.canRead() && videoFile.isFile() ) {
            mFile = videoFile;
        } // else we cannot work with this video file

        if ( outDir.isDirectory() ) {
            if ( outDir.canWrite() ) {
                cleanDirectory(outDir);
                mOutDir = outDir;
            } // else we cannot use this output directory
        } else {
            // try to create this directory
            if ( outDir.mkdirs() ) {
                mOutDir = outDir;
            }
        }

        // If the skip ratio is negative we must make it positive and make sure it is not less than 1
        if ( skipRatio < 0 ) {
            skipRatio *= -1;
        }
        if ( skipRatio < 1 ) {
            skipRatio+=1;
        }

        //Open video and get basic information
        mVideo = new FFmpegMediaMetadataRetriever();

        // Use FFmpeg media receiver instead of OpenCV
        mVideo.setDataSource(videoFile.getAbsolutePath());

        mNumFrames = 0;
        mFrame = 0;


        // Checks if there is a video codec
        if ( mVideo.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_CODEC) != null ) {

            // get the duration of the video and frame rate
            String data = mVideo.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
            double duration = Double.valueOf(data);
            data = mVideo.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE);
            double frameRate = Double.valueOf(data);

            Log.i(TAG, "frameRate = "+frameRate);

            double millisecondsPerFrame = MILLI_ONE_SECOND/frameRate;

            // get number of frames and number of microseconds between the frames we want
            mNumFrames = (int)(duration/millisecondsPerFrame);

            mFrameLength = (long)((MICRO_ONE_SECOND/frameRate)*skipRatio);

        } else {
            Log.i(TAG, "failed to open Video file: "+mFile.getAbsolutePath());
            // we do not have a video file to work with
            mFile = null;
        }

        // Add this task to our ListView so the progress is displayed
        mSkipRatio = skipRatio;
        mListView = listView;
        mProgAdapter = (ProgressAdapter) listView.getAdapter();

        mId = mProgAdapter.addTask(videoFile.getName(), (int)(mNumFrames/skipRatio) );
        Log.i(TAG, "Constructor ("+toString()+")");
    }

    /**
     * Cleans out all files in the given directory
     * @param folder directory to clean out
     */
    private void cleanDirectory(File folder) {
        File[] files = folder.listFiles();
        for ( File file: files ) {
            file.delete();
        }
    }
    /**
     * Override finalize to release the video file
     * @throws Throwable
     */
    @Override
    public void finalize() throws Throwable {
        super.finalize();
        mVideo.release();
    }

    /**
     * Method used to pull out frames from video in background
     * @param voids inputs are not used
     * @return null
     */
    @Override
    protected Void doInBackground(Void... voids) {
        Log.i(TAG, "Started ("+mId+")");

        // make sure we are setup correctly
        if ( mFile == null || mOutDir == null ) {
            Log.i(TAG, "Video file or output director were not working\nvideo: "+mFile+"\noutput directory: "+mOutDir);
            // either the input or output locations will not work
            return null;
        }

        // grab all frames except the ones we skip and save them to the output directory
        int frameCount = (int)(mNumFrames/mSkipRatio);
        for ( int i = 0; i < frameCount && !mCanceled; i++ ) {

            Bitmap map = mVideo.getFrameAtTime(i*mFrameLength, FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
            if ( map != null ) {
                // we have a frame to save. Convert the frame into Mat and save it.
                Mat frame = new Mat();
                Utils.bitmapToMat(map, frame); // Bitmap to RGBA

                // Convert RGBA to BGR
                Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2BGR);

                String fileName = String.format(Locale.ENGLISH,"%1$s/%2$s_%3$06d.jpg",mOutDir.getAbsolutePath(),
                        FileParser.getBaseName(mFile), i);
                Imgcodecs.imwrite(fileName,frame);

                //update our progress to the UI
                mFrame = i;
                publishProgress();
                frame.release();
                map.recycle();
            } // else we cannot save an empty frame
        } // end for loop through all frames in video

        return null;
    }

    /**
     * updates the UI displaying the progress of this task
     * @param voids not used.
     */
    @Override
    protected void onProgressUpdate(Void... voids) {
        if ( mFrame%10 == 0 ) {
            Log.i(TAG, "Progress update ("+mId+"): "+mFrame);
        }
        mCanceled=!mProgAdapter.updateTaskProgress(mId,mFrame);
        mListView.invalidateViews();
    }

    /**
     * Remove this task from the ListView so use knows we are done
     * @param v
     */
    @Override
    protected void onPostExecute(Void v) {
        Log.i(TAG, "stopped ("+mId+")");
        mProgAdapter.removeTask(mId);
        mListView.invalidateViews();
    }

    /**
     * Used to display the contents of this class
     * @return Information about this class
     */
    @Override
    public String toString() {
        return "{ input="+mFile+", output="+
                mOutDir+", # frames="+mNumFrames+ ", frame length="+mFrameLength + ", ID="+mId +", skip ratio="+ mSkipRatio+ "}";
    }
}
