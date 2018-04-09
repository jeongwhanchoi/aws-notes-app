package com.amazonaws.mobile.samples.mynotes.data;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@DynamoDBTable(tableName = "notestutorial-mobilehub-1906736448-Notes")

public class NotesDO {
    private String _userId;
    private String _noteId;
    private String _content;
    private Double _creationDate;
    private String _title;
    private Double _updatedDate;

    @DynamoDBHashKey(attributeName = "userId")
    @DynamoDBIndexHashKey(attributeName = "userId", globalSecondaryIndexNames = {"DateSorted","LastUpdated",})
    public String getUserId() {
        return _userId;
    }

    public void setUserId(final String _userId) {
        this._userId = _userId;
    }
    @DynamoDBRangeKey(attributeName = "noteId")
    @DynamoDBAttribute(attributeName = "noteId")
    public String getNoteId() {
        return _noteId;
    }

    public void setNoteId(final String _noteId) {
        this._noteId = _noteId;
    }
    @DynamoDBAttribute(attributeName = "content")
    public String getContent() {
        return _content;
    }

    public void setContent(final String _content) {
        this._content = _content;
    }
    @DynamoDBIndexRangeKey(attributeName = "creationDate", globalSecondaryIndexName = "DateSorted")
    public Double getCreationDate() {
        return _creationDate;
    }

    public void setCreationDate(final Double _creationDate) {
        this._creationDate = _creationDate;
    }
    @DynamoDBAttribute(attributeName = "title")
    public String getTitle() {
        return _title;
    }

    public void setTitle(final String _title) {
        this._title = _title;
    }
    @DynamoDBIndexRangeKey(attributeName = "updatedDate", globalSecondaryIndexName = "LastUpdated")
    public Double getUpdatedDate() {
        return _updatedDate;
    }

    public void setUpdatedDate(final Double _updatedDate) {
        this._updatedDate = _updatedDate;
    }

}
