/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
 * except in compliance with the License. A copy of the License is located at
 *
 *    http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package com.amazonaws.mobile.samples.mynotes;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.amazonaws.mobile.samples.mynotes.data.Note;
import com.amazonaws.mobile.samples.mynotes.data.NotesContentContract;
import com.amazonaws.mobileconnectors.pinpoint.analytics.AnalyticsClient;
import com.amazonaws.mobileconnectors.pinpoint.analytics.AnalyticsEvent;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A fragment representing a single Note detail screen.
 * This fragment is either contained in a {@link NoteListActivity}
 * in two-pane mode (on tablets) or a {@link NoteDetailActivity}
 * on handsets.
 */
public class NoteDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "noteId";

    /**
     * The dummy content this fragment is presenting.
     */
    private Note mItem;
    private Uri itemUri;

    /**
     * Content Resolver
     */
    private ContentResolver contentResolver;

    /**
     * Is this an insert or an update?
     */
    private boolean isUpdate;

    /**
     * The component bindings
     */
    EditText editTitle;
    EditText editContent;

    /**
     * The timer for saving the record back to SQL
     */
    Handler timer = new Handler();
    Runnable timerTask = new Runnable() {
        @Override
        public void run() {
            saveData();                             // Save the data
            timer.postDelayed(timerTask, 5000);     // Every 5 seconds
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public NoteDetailFragment() {
    }

    /**
     * Lifecycle event handler - called when the fragment is created.
     * @param savedInstanceState the saved state
     */
    // Constants used for async data operations
    private static final int QUERY_TOKEN = 1001;
    private static final int UPDATE_TOKEN = 1002;
    private static final int INSERT_TOKEN = 1003;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the ContentResolver
        contentResolver = getContext().getContentResolver();

        // Unbundle the arguments if any.  If there is an argument, load the data from
        // the content resolver aka the content provider.
        Bundle arguments = getArguments();
        mItem = new Note();
        if (arguments != null && arguments.containsKey(ARG_ITEM_ID)) {
            String itemId = getArguments().getString(ARG_ITEM_ID);
            itemUri = NotesContentContract.Notes.uriBuilder(itemId);


            // Replace local cursor methods with async query handling
            AsyncQueryHandler queryHandler = new AsyncQueryHandler(contentResolver) {
                @Override
                protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                    super.onQueryComplete(token, cookie, cursor);
                    cursor.moveToFirst();
                    mItem = Note.fromCursor(cursor);
                    isUpdate = true;

                    editTitle.setText(mItem.getTitle());
                    editContent.setText(mItem.getContent());
                }
            };
            queryHandler.startQuery(QUERY_TOKEN, null, itemUri, NotesContentContract.Notes.PROJECTION_ALL, null, null, null);


        } else {
            isUpdate = false;
        }

        // Start the timer for the delayed start
        timer.postDelayed(timerTask, 5000);
    }

    /**
     * Lifecycle event handler - called when the fragment is paused.  Use this to do any
     * saving of data as it is the last opportunity to reliably do so.
     */
    @Override
    public void onPause() {
        super.onPause();
        timer.removeCallbacks(timerTask);
        saveData();
    }

    /**
     * Save the data from the form back into the database.
     */
    private void saveData() {
        // Save the edited text back to the item.
        boolean isUpdated = false;
        if (!mItem.getTitle().equals(editTitle.getText().toString().trim())) {
            mItem.setTitle(editTitle.getText().toString().trim());
            mItem.setUpdated(DateTime.now(DateTimeZone.UTC));
            isUpdated = true;
        }
        if (!mItem.getContent().equals(editContent.getText().toString().trim())) {
            mItem.setContent(editContent.getText().toString().trim());
            mItem.setUpdated(DateTime.now(DateTimeZone.UTC));
            isUpdated = true;
        }

        // Replace local cursor methods with an async query handler
        // Convert to ContentValues and store in the database.
        if (isUpdated) {
            ContentValues values = mItem.toContentValues();

            AsyncQueryHandler queryHandler = new AsyncQueryHandler(contentResolver) {
                @Override
                protected void onInsertComplete(int token, Object cookie, Uri uri) {
                    super.onInsertComplete(token, cookie, uri);
                    Log.d("NoteDetailFragment", "insert completed");
                }

                @Override
                protected void onUpdateComplete(int token, Object cookie, int result) {
                    super.onUpdateComplete(token, cookie, result);
                    Log.d("NoteDetailFragment", "update completed");
                }
            };
            if (isUpdate) {
                queryHandler.startUpdate(UPDATE_TOKEN, null, itemUri, values, null, null);
            } else {
                queryHandler.startInsert(INSERT_TOKEN, null, NotesContentContract.Notes.CONTENT_URI, values);
                isUpdate = true;    // Anything from now on is an update

                // Send Custom Event to Amazon Pinpoint
                final AnalyticsClient mgr = AWSProvider.getInstance()
                        .getPinpointManager()
                        .getAnalyticsClient();
                final AnalyticsEvent evt = mgr.createEvent("AddNote")
                        .withAttribute("noteId", mItem.getNoteId());
                mgr.recordEvent(evt);
                mgr.submitEvents();
            }


        }
    }

    /**
     * Returns the current note.
     * @return the current data
     */
    public Note getNote() {
        return mItem;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get a reference to the root view
        View rootView = inflater.inflate(R.layout.note_detail, container, false);

        // Update the text in the editor
        editTitle = (EditText) rootView.findViewById(R.id.edit_title);
        editContent = (EditText) rootView.findViewById(R.id.edit_content);

        editTitle.setText(mItem.getTitle());
        editContent.setText(mItem.getContent());

        return rootView;
    }
}
