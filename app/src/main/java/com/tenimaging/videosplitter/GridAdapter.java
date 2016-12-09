/*
 * Copyright (c) 2016. 10 Imaging Inc.
 */
package com.tenimaging.videosplitter;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Stack;

/**
 * This class handles the video icons displayed in a list or grid view
 */
public class GridAdapter extends BaseAdapter {
    private static final String TAG = "GridAdapter";    // Tag that marks all log messages from this class
    private static final String EXTENSIONS = ".mp4";    // List of allowable video extensions seperated by ';'
    private int mCount;                                 // number of videos to display
    private Activity mActivity;                         // calling activity
    private ArrayList<File> mFiles;                     // Video files we found on SD card
    private int selectedID;                             // ID of the video file that is currently selected
    private ArrayList<Bitmap> mThumbnails;              // List of thumbnails one for each video
    private ViewHolder mLastSelected;                   // used to deselect last selected video

    /**
     * Constructor
     * @param activity calling activity that is creating this adapter
     */
    public GridAdapter(Activity activity) {
        super();
        this.mActivity = activity;

        // Get permissions to access external storage if we are using and sdk of 23 or greater
        if(Build.VERSION.SDK_INT >= 23 ) {
            int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if ( permission != PackageManager.PERMISSION_GRANTED ) {
                Log.i(TAG, "Do not have write external storage prermission");

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);
            } // else we already have permission
        } // else we got permissions at install

        // Find all videos on external storage and create the thumbnails for each one
        loadExternalVideoFiles();

        Log.i(TAG, "found "+mFiles.size() + " video files");

        mCount = mFiles.size();
        mLastSelected = null;
        selectedID = -1;
    }

    /**
     * Marks the video at the given at position as selected or deselect it if it was already selected
     * @param position index of the video we want to select
     * @param v - View of the selected video
     */
    public void selectItem(int position, View v) {
        Log.i(TAG, "selectItem");
        if ( selectedID == position ) {
            // deselect the video
            selectedID = -1;
            mLastSelected.imgView.setBackgroundColor(Color.WHITE);
        } else {
            // mark this video as selected
            selectedID = position;
            updateSelectedView(v);
        }
    }

    /**
     * Removes visual selection of previous video and add visual selection of current video
     * @param v current video to show as selected
     */
    private void updateSelectedView(View v) {
        if ( mLastSelected != null ) {
            mLastSelected.imgView.setBackgroundColor(Color.WHITE);
        }
        mLastSelected = (ViewHolder) v.getTag();
        mLastSelected.imgView.setBackgroundColor(Color.BLUE);
    }

    /**
     * Checks if the given file is a video file or not
     * @param file file to test if it is a video
     * @return true if the given file is a video otherwise false
     */
    private boolean checkIfVideo(File file) {
        String[] extensions = EXTENSIONS.split(";");
        boolean isVideo = false;
        for ( String extension: extensions ) {
            if ( file.getName().endsWith(extension) ) {
                isVideo = true;
                break;
            }
        }
        return isVideo;
    }

    /**
     * Looks for all video files with the given extension on external storage
     */
    private void loadExternalVideoFiles() {

        // Create new list of files and thumbnails
        ArrayList<File> nFiles = new ArrayList<>();
        ArrayList<Bitmap> thumbnails = new ArrayList<>();

        // This stack is used to store all folders we have not yet checked.
        Stack<File> stack = new Stack<>();
        File current = new File(MainActivity.getExternalDir());

        stack.add(current);


        // For each folder on the stack look for all video files and new folders
        while ( !stack.empty() ) {
            current = stack.pop();

            // Look at each file in this folder and grab the videos and subfolders
            File[] list = current.listFiles();
            for ( File file: list ) {

                if ( file.canRead() ) {

                    if (file.isDirectory()) {
                        // add subfolder to the stack
                        stack.push(file);

                    } else if ( checkIfVideo(file) ) {

                        // create new thumbnail for this video and save both thumbnail and file for later
                        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Video.Thumbnails.MICRO_KIND);
                        nFiles.add(file);
                        thumbnails.add(thumb);

                    } // else ignore files that do not match our given extension

                } // else ignore folders and files we cannot read
            }
        } // end while loop through all folders in stack

        // save all files and thumbnails found
        mFiles = nFiles;
        mThumbnails = thumbnails;
    }

    /**
     * Returns the number of videos in this list
     * @return Number of videos available
     */
    @Override
    public int getCount() {
        return mCount;
    }

    /**
     * Returns the video at the given position
     * @param position index of the video we want
     * @return Video file at the given position
     */
    @Override
    public Object getItem(int position) {
        return mFiles.get(position);
    }

    /**
     * Not used
     * @param position
     * @return
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Class containing views used to display thumbnail and file name for video
     */
    public class ViewHolder
    {
        public ImageView imgView;
        public TextView txtView;
    }

    /**
     * Used to create and manage all views used to display video content to user
     * @param position index of the video we want to get
     * @param convertView reusable view or null
     * @param parent parent of the view we are creating or updating
     * @return View to display this video content
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder view;
        LayoutInflater inflator = mActivity.getLayoutInflater();

        // check if we have to create (inflate) this video view
        if (convertView == null) {
            // Create new viewHolder item if because we don't have one
            view = new ViewHolder();

            // Create a new View used to display Video content
            convertView = inflator.inflate(R.layout.gridview_row, parent, false);

            view.txtView = (TextView) convertView.findViewById(R.id.textView);
            view.imgView = (ImageView) convertView.findViewById(R.id.imageView);
            convertView.setTag(view);

        } else {
            // reuse existing viewHolder
            view = (ViewHolder) convertView.getTag();
        }

        // add the video content for this position into the display view
        view.imgView.setImageBitmap(mThumbnails.get(position));
        view.txtView.setText(mFiles.get(position).getName()); // Update the value for the text

        // make sure to marke this view as selected if selectedID equals our position
        if ( selectedID == position ) {
            updateSelectedView(convertView);
        }

        return convertView;
    }
}
