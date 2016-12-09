/*
 * Copyright (c) 2016. 10 Imaging Inc.
 */
package com.tenimaging.videosplitter;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.ArrayList;

/**
 * Main activity that displays our application
 */
public class MainActivity extends AppCompatActivity {
    private static final String EXTERNAL_DIR = Environment.getExternalStorageDirectory()+"/DCIM/"; // Baseline folder for output
    private static final String BASE_DIR = EXTERNAL_DIR+"camera0/";
    static final String TAG = "MainActivity";   // TAG that marks log messages from this class
    private int mSelected;                      // id of the video that is selected
    private ListView mListView;                 // ListView that displays progress of VideoSplitTasks
    private EditText mPathView;                 // View that holds the output path
    private EditText mFolderView;               // View that holds the output directory name
    private GridAdapter vidAdapter;             // View used to display all available Video files we can split

    /**
     * Used to get the Baseline output directory
     * @return String path to the baseline output directory on the SD card
     */
    static public String getExternalDir() {
        return EXTERNAL_DIR;
    }

    /**
     * Loads in a library
     * @param name name of the library we want to load
     * @return true if successfully loaded the library
     */
    public boolean loadLib(String name) {
        try {
            System.loadLibrary(name);
            Log.i("", "Library " + name + " loaded");
            return true;
        }
        catch (UnsatisfiedLinkError e)
        {
            Log.d("", "Cannot load library \"" + name + "\"");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sets up the defaults for this activity
     * @param savedInstanceState content used to setup this activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Get the path and folder Views
        mPathView = (EditText) findViewById(R.id.addressText);
        mPathView.setHint(BASE_DIR);
        mFolderView = (EditText) findViewById(R.id.folderText);

        // Set default video selected as -1 so we do not accidentally split a video we do not have selected
        mSelected = -1;

        // Load in video thumbnails into our GridView
        vidAdapter = new GridAdapter(this);
        final GridView gridView = (GridView) findViewById(R.id.gridView1);

        gridView.setAdapter(vidAdapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            /**
             * Keep track of selected Video and highlight that video
             * @param parent    parent view that contains this item view
             * @param v         Item view that was selected
             * @param position  Position of this item in our GridView
             * @param id        id of the item selected
             */
            @Override
            public void onItemClick(AdapterView parent, View v, int position, long id) {
                vidAdapter.selectItem(position, v);
                if ( mSelected == position ) {
                    // Unselect this video because we clicked it twice
                    mSelected = -1;
                    mFolderView.setHint("file name");
                } else {
                    // select this video
                    mSelected = position;
                    mFolderView.setHint(FileParser.getBaseName((File)vidAdapter.getItem(position)));
                }
                gridView.invalidateViews();

            }
        });

        // Added our progress adapter to the log ListView and hid the ListView
        mListView = (ListView) findViewById(R.id.logList);
        ProgressAdapter progAdapter = new ProgressAdapter(this);
        mListView.setAdapter(progAdapter);
        mListView.setVisibility(View.GONE);

        // Try to load in OpenCV library
        boolean openCVready = loadLib("opencv_java3");
        openCVready &= OpenCVLoader.initDebug();
        if ( !openCVready ) {
            Log.i(TAG, "Failed to load in OpenCV library");

        }
    }


    /**
     * Called by Brows button and opens FileDialog to get new output path
     * @param view button pressed to run this method
     */
    public void onBrowse(View view) {

        Log.i(TAG, "onBrowse");

        // Create a new FileDialog in order to search for a new parent folder for our output directory
        // to be saved into
        File mPath = new File(BASE_DIR);
        FileDialog fileDialog = new FileDialog(this, mPath);
        fileDialog.setSelectDirectoryOption(true);

        fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {

            /**
             * updates the PathView to show the new selected path
             * @param file
             */
            public void directorySelected(File file) {

                Log.d(getClass().getName(), "selected folder path " + file.toString());
                String selectedFileName = file.getAbsolutePath();
                EditText edit = (EditText)findViewById(R.id.addressText);
                edit.setText(selectedFileName);
            }
        });

        // Display the FileDialog
        fileDialog.showDialog();
    }

    /**
     * Starts the SplitVideoTask for the selected video file
     * @param view View that calls this method
     */
    public void onSplit(View view) {

        // Check that we have a video selected first
        if ( mSelected < 0 ) {
            Toast.makeText(getApplicationContext(), "Please Select a video File to split", Toast.LENGTH_SHORT).show();
            return;
        } // else continue

        // Get the output path and folder
        String path = mPathView.getText().toString();
        String fileName = mFolderView.getText().toString();

        if( path.length() == 0 ) {
            // use the hint text
            path = mPathView.getHint().toString();
        }
        if ( fileName.length() == 0 ) {
            // use the hint text
            fileName = mFolderView.getHint().toString();
        }

        File dir = new File(path, fileName);
        File vidFile = (File)vidAdapter.getItem(mSelected);

        /* Get the frame skip ratio. The ratio is used to tell how far to move from one frame to the
         * next frame we want. examples:
         * skipRatio = 0.5 or 1.5 - skips about every third frame
         * skipRatio = 1 - skips no frames
         * skipRatio = 2 - skips every other frame
         * skipRatio = 3 - only grabs every third frame
         */
        EditText ratioView = (EditText)findViewById(R.id.skipRatio);
        String ratio = ratioView.getText().toString();
        if ( ratio.length() == 0 ) {
            ratio = ratioView.getHint().toString();
        }
        double skipRatio = Double.valueOf(ratio);

        Toast.makeText(getApplicationContext(), "Splitting ("+vidFile.getName()+") into "+dir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        Log.i(TAG, "onSplit, skip ratio: "+skipRatio);

        // Create new SplitVideoTask and run it in parallel thread
        SplitVideoTask task = new SplitVideoTask(vidFile, dir, skipRatio, mListView );
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // Display the log ListView so the user can see progress of the split video task
        mListView.setVisibility(View.VISIBLE);

    }

}
