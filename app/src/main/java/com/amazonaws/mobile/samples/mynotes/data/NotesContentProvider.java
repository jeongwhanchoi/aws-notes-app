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
package com.amazonaws.mobile.samples.mynotes.data;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.amazonaws.mobile.samples.mynotes.AWSProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The Content Provider for the internal Notes database
 */
public class NotesContentProvider extends ContentProvider {
    /**
     * Creates a UriMatcher for matching the path elements for this content provider
     */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /**
     * The code for the UriMatch matching all notes
     */
    private static final int ALL_ITEMS = 10;

    /**
     * The code for the UriMatch matching a single note
     */
    private static final int ONE_ITEM = 20;

    /**
     * The database helper for this content provider
     */
    private DatabaseHelper databaseHelper;

    /*
     * Initialize the UriMatcher with the URIs that this content provider handles
     */
    static {
        sUriMatcher.addURI(
                NotesContentContract.AUTHORITY,
                NotesContentContract.Notes.DIR_BASEPATH,
                ALL_ITEMS);
        sUriMatcher.addURI(
                NotesContentContract.AUTHORITY,
                NotesContentContract.Notes.ITEM_BASEPATH,
                ONE_ITEM);
    }

    /**
     * Part of the Content Provider interface.  The system calls onCreate() when it starts up
     * the provider.  You should only perform fast-running initialization tasks in this method.
     * Defer database creation and data loading until the provider actually receives a request
     * for the data.  This runs on the UI thread.
     *
     * @return true if the provider was successfully loaded; false otherwise
     */
    @Override
    public boolean onCreate() {
        databaseHelper = new DatabaseHelper(getContext());
        return true;
    }

    /**
     * Query for a (number of) records.
     *
     * @param uri The URI to query
     * @param projection The fields to return
     * @param selection The WHERE clause
     * @param selectionArgs Any arguments to the WHERE clause
     * @param sortOrder the sort order for the returned records
     * @return a Cursor that can iterate over the results
     */
    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        int uriType = sUriMatcher.match(uri);

        DynamoDBMapper dbMapper = AWSProvider.getInstance().getDynamoDBMapper();
        MatrixCursor cursor = new MatrixCursor(NotesContentContract.Notes.PROJECTION_ALL);
        String userId = AWSProvider.getInstance().getIdentityManager().getCachedUserID();

        switch (uriType) {
            case ALL_ITEMS:
                // In this (simplified) version of a content provider, we only allow searching
                // for all records that the user owns.  The first step to this is establishing
                // a template record that has the partition key pre-populated.
                NotesDO template = new NotesDO();
                template.setUserId(userId);
                // Now create a query expression that is based on the template record.
                DynamoDBQueryExpression<NotesDO> queryExpression;
                queryExpression = new DynamoDBQueryExpression<NotesDO>()
                        .withHashKeyValues(template);
                // Finally, do the query with that query expression.
                List<NotesDO> result = dbMapper.query(NotesDO.class, queryExpression);
                Iterator<NotesDO> iterator = result.iterator();
                while (iterator.hasNext()) {
                    final NotesDO note = iterator.next();
                    Object[] columnValues = fromNotesDO(note);
                    cursor.addRow(columnValues);
                }

                break;
            case ONE_ITEM:
                // In this (simplified) version of a content provider, we only allow searching
                // for the specific record that was requested
                final NotesDO note = dbMapper.load(NotesDO.class, userId, uri.getLastPathSegment());
                if (note != null) {
                    Object[] columnValues = fromNotesDO(note);
                    cursor.addRow(columnValues);
                }
                break;
        }

        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    /**
     * The content provider must return the content type for its supported URIs.  The supported
     * URIs are defined in the UriMatcher and the types are stored in the NotesContentContract.
     *
     * @param uri the URI for typing
     * @return the type of the URI
     */
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case ALL_ITEMS:
                return NotesContentContract.Notes.CONTENT_DIR_TYPE;
            case ONE_ITEM:
                return NotesContentContract.Notes.CONTENT_ITEM_TYPE;
            default:
                return null;
        }
    }

    /**
     * Insert a new record into the database.
     *
     * @param uri the base URI to insert at (must be a directory-based URI)
     * @param values the values to be inserted
     * @return the URI of the inserted item
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case ALL_ITEMS:
                DynamoDBMapper dbMapper = AWSProvider.getInstance().getDynamoDBMapper();
                final NotesDO newNote = toNotesDO(values);
                dbMapper.save(newNote);
                Uri item = NotesContentContract.Notes.uriBuilder(newNote.getNoteId());
                notifyAllListeners(item);
                return item;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }


    /**
     * Delete one or more records from the SQLite database.
     *
     * @param uri the URI of the record(s) to delete
     * @param selection A WHERE clause to use for the deletion
     * @param selectionArgs Any arguments to replace the ? in the selection
     * @return the number of rows deleted.
     */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        int rows;

        switch (uriType) {
            case ONE_ITEM:
                DynamoDBMapper dbMapper = AWSProvider.getInstance().getDynamoDBMapper();
                final NotesDO note = new NotesDO();
                note.setNoteId(uri.getLastPathSegment());
                note.setUserId(AWSProvider.getInstance().getIdentityManager().getCachedUserID());
                dbMapper.delete(note);
                rows = 1;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (rows > 0) {
            notifyAllListeners(uri);
        }
        return rows;
    }


    /**
     * Part of the ContentProvider implementation.  Updates the record (based on the record URI)
     * with the specified ContentValues
     *
     * @param uri The URI of the record(s)
     * @param values The new values for the record(s)
     * @param selection If the URI is a directory, the WHERE clause
     * @param selectionArgs Arguments for the WHERE clause
     * @return the number of rows updated
     */
    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        int rows;

        switch (uriType) {
            case ONE_ITEM:
                DynamoDBMapper dbMapper = AWSProvider.getInstance().getDynamoDBMapper();
                final NotesDO updatedNote = toNotesDO(values);
                dbMapper.save(updatedNote);
                rows = 1;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (rows > 0) {
            notifyAllListeners(uri);
        }
        return rows;
    }

    /**
     * Notify all listeners that the specified URI has changed
     * @param uri the URI that changed
     */
    private void notifyAllListeners(Uri uri) {
        ContentResolver resolver = getContext().getContentResolver();
        if (resolver != null) {
            resolver.notifyChange(uri, null);
        }
    }

    private String getOneItemClause(String id) {
        return String.format("%s = \"%s\"", NotesContentContract.Notes.NOTEID, id);
    }

    private NotesDO toNotesDO(ContentValues values) {
        final NotesDO note = new NotesDO();
        note.setContent(values.getAsString(NotesContentContract.Notes.CONTENT));
        note.setCreationDate(values.getAsDouble(NotesContentContract.Notes.CREATED));
        note.setNoteId(values.getAsString(NotesContentContract.Notes.NOTEID));
        note.setTitle(values.getAsString(NotesContentContract.Notes.TITLE));
        note.setUpdatedDate(values.getAsDouble(NotesContentContract.Notes.UPDATED));
        note.setUserId(AWSProvider.getInstance().getIdentityManager().getCachedUserID());
        return note;
    }

    private Object[] fromNotesDO(NotesDO note) {
        String[] fields = NotesContentContract.Notes.PROJECTION_ALL;
        Object[] r = new Object[fields.length];
        for (int i = 0 ; i < fields.length ; i++) {
            if (fields[i].equals(NotesContentContract.Notes.CONTENT)) {
                r[i] = note.getContent();
            } else if (fields[i].equals(NotesContentContract.Notes.CREATED)) {
                r[i] = note.getCreationDate();
            } else if (fields[i].equals(NotesContentContract.Notes.NOTEID)) {
                r[i] = note.getNoteId();
            } else if (fields[i].equals(NotesContentContract.Notes.TITLE)) {
                r[i] = note.getTitle();
            } else if (fields[i].equals(NotesContentContract.Notes.UPDATED)) {
                r[i] = note.getUpdatedDate();
            } else {
                r[i] = new Integer(0);
            }
        }
        return r;
    }
}
