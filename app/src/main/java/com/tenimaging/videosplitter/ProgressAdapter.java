/*
 * Copyright (c) 2016. 10 Imaging Inc.
 */
package com.tenimaging.videosplitter;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Controls displays used to show background task progress
 */
public class ProgressAdapter extends BaseAdapter {
    private static final String TAG = "ProgressAdapter"; // Tag that marks all log messages from this class
    private static final int MAX_START = 10;            // Initial size of lists used

    /**
     * Contains the information for each task we show progress for.
     */
    private class TaskObject {
        public String name;     // Name of this task
        public int progress;    // Current progress value
        public int max;         // Max progress value
        public int id;          // ID of this task

        /**
         * Constructor
         * @param n name of this task
         * @param m max value for progress
         */
        public TaskObject ( String n, int m ) {
            name = n;
            max = m;
            progress = 0;
            id = -1;
        }
    }

    private List<TaskObject> mTasks;    // List of all task we display
    private Activity mActivity;         // Activity that created this adapter
    private List<Integer> mPositions;   // List used to handle where tasks of certain ID are in mTask list
    private Stack<Integer> mOpenIds;    // contains task IDs that can be reused to save memory
    private View parent = null;         // Parent ListView that uses this adapter

    /**
     * Constructor that just initializes defaults
     * @param activity Calling activity
     */
    public ProgressAdapter(Activity activity) {
        // Start with an empty list
        mTasks = new ArrayList<>(MAX_START);
        mPositions = new ArrayList<>(MAX_START);
        mActivity = activity;
        mOpenIds = new Stack<>();
    }

    /**
     * Adjust the locations each task ID points to when removing the given index
     * @param index - position in mTask that is removed
     */
    private void adjustPositions(int index) {

        // Make sure that all task IDs that have positions above the given index are
        // decremented so that they still point to the correct task.
        int size = mPositions.size();
        for ( int i = 0; i < size; i++ ) {
            int pos = mPositions.get(i);
            if ( pos > index ) {
                mPositions.set(i, pos - 1);
            }
        }
    }

    /**
     * Adds a new task to our list of tasks
     * @param name name of the task we are adding
     * @param max max value of progress bar
     * @return new Task ID to identify this task
     */
    public synchronized int addTask(String name, int max ) {
        TaskObject task = new TaskObject(name, max);

        int tid = mTasks.size();        // new task location inside mTasks
        int nid = mPositions.size();    // Default task ID

        // check if we have reusable task IDs
        if ( mOpenIds.empty() ) {
            // No reusable task ID so just add new one
            mPositions.add(tid);
        } else {
            // get the reusable task ID and set it to point to location of task in mTasks
            int oid = mOpenIds.pop();
            mPositions.set(oid,tid);
            nid = oid;
        }

        // add new task and return the corresponding ID
        task.id = nid;
        mTasks.add(task);
        notifyDataSetChanged();
        return nid;
    }

    /**
     * Used to update the progress of a task with the given id
     * @param id ID of the task we want to update progress for
     * @param progress Value of the new progress update
     * @return true if we successfully updated progress for this task ID otherwise false
     */
    public synchronized boolean updateTaskProgress(int id, int progress) {
        try {
            mTasks.get(mPositions.get(id)).progress = progress;
            return true;
        } catch (IndexOutOfBoundsException ex ) {
            // Task must have been removed or does not exist
            return false;
        }
    }

    /**
     * Remove the task that matches the given task ID
     * @param id ID of the task we want to remove
     */
    public synchronized void removeTask(int id) {
        if ( id < 0 ) {
            // invalid task ID
            return;
        }
        // Try to remove this position
        try {
            int posRemove = mPositions.get(id);
            if ( posRemove < 0 ) {
                // we have already removed this one so we are done
                return;
            }
            if (id == mPositions.size() - 1) {
                // we can safely remove this task ID without affecting any other in list
                mPositions.remove(id);
            } else {
                // We cannot remove this task ID because it will affect the other IDs so we
                // will add it to our list of reusable task IDs and mark the task as empty '-1'
                mPositions.set(id, -1);
                mOpenIds.push(id);
            }

            // Make sure all task IDs that point to tasks farther down our list are not affected by
            // the removal of this task
            adjustPositions(posRemove);
            mTasks.remove(posRemove);

            // Hide the corresponding ListView if there are not tasks to be shown.
            if (mTasks.size() == 0 && parent != null) {
                parent.setVisibility(View.GONE);
            }
        } catch(IndexOutOfBoundsException ex ) {
            // Task must have already been removed
            // We do not care
        }
        notifyDataSetChanged();
    }

    /**
     * Used to get number of tasks in this adapter
     * @return Number of tasks to be displayed by this adapter
     */
    @Override
    public synchronized int getCount() {
        return mTasks.size();
    }

    /**
     * Not used
     * @param i
     * @return null
     */
    @Override
    public Object getItem(int i) {
        return null;
    }

    /**
     * Not used
     * @param i
     * @return 0
     */
    @Override
    public long getItemId(int i) {
        return 0;
    }

    /**
     * Class used to hold all Views used to display the information for each task
     */
    public class ViewHolder {
        public TextView nameView;
        public ProgressBar progress;
        public Button close;
    }

    /**
     * Creates a View used to display the task content for the given position in our list
     * @param position location of the task in our list that we want
     * @param view Reusable view or null if we have to create a new one
     * @param viewGroup Parent view that our list of tasks is displayed in
     * @return View displaying content for the task at the given position
     */
    @Override
    public synchronized View getView(int position, View view, final ViewGroup viewGroup) {

        // Keep the parent view if we do not already have it
        if ( parent == null ) {
            parent = viewGroup;
        }

        // Make sure we are not trying to access a task that no longer exists
        TaskObject task;
        try {
            task = mTasks.get(position);
        } catch (IndexOutOfBoundsException ex ) {
            // Task must have been removed
            return null;
        }

        ProgressAdapter.ViewHolder row;
        LayoutInflater inflator = mActivity.getLayoutInflater();

        // Reuse or create new view to display our task content
        if (view == null) {
            // Create new viewHolder item for this task because it does not exist
            row = new ProgressAdapter.ViewHolder();
            view = inflator.inflate(R.layout.progress_row, viewGroup, false);

            row.nameView = (TextView) view.findViewById(R.id.fileLabel);
            row.progress = (ProgressBar) view.findViewById(R.id.progressBar);
            row.close = (Button) view.findViewById(R.id.closeButton);
            view.setTag(row);

        } else {
            // reuse existing viewHolder
            row = (ProgressAdapter.ViewHolder) view.getTag();
        }

        // Populate our View with information from this task
        row.nameView.setText(task.name);
        row.progress.setMax(task.max);
        row.progress.setProgress(task.progress);
        row.close.setTag(task.id);

        // Add onClick listener to close button so we can remove this task if user pusses the button
        row.close.setOnClickListener(new View.OnClickListener() {

            /**
             * Remove the corresponding task associated with this close button
             * @param view
             */
            @Override
            public void onClick(View view) {
                Integer id = (Integer)view.getTag();
                if ( id != null ) {
                    removeTask(id);
                    Log.i(TAG, "remove task: "+id);

                    if ( parent != null ) {
                        parent.invalidate();
                    } // else we do not have a prent View to invalidate
                } // else we do not have a task ID to remove
            }
        });

        return view;
    }
}
