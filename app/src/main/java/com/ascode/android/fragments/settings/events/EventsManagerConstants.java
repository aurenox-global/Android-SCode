package com.ascode.android.fragments.settings.events;

import android.os.Environment;

import java.io.File;

public class EventsManagerConstants {
    public static final File EVENT_EXPORT_LOCATION = new File(Environment.getExternalStorageDirectory(),
            ".ascode/data/system/export/events/");
    public static final File EVENTS_FILE = new File(Environment.getExternalStorageDirectory(),
            ".ascode/data/system/events.json");
    public static final File LISTENERS_FILE = new File(Environment.getExternalStorageDirectory(),
            ".ascode/data/system/listeners.json");
}