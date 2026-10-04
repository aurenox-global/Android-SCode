package com.besome.sketch.editor.event;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.EventBean;

import com.ascode.android.R;

/**
 * Provides short human-readable descriptions for events shown in the redesigned
 * event section. Activity events get a specific description; any other event
 * falls back to a generic description for its category. When nothing suitable
 * is known the line is left empty instead of inventing dubious technical text.
 */
public final class EventDescriptions {

    private EventDescriptions() {
    }

    @Nullable
    public static String get(Context context, EventBean event) {
        if (context == null || event == null) {
            return null;
        }
        String specific = getForEvent(context, event.eventName);
        if (specific != null) {
            return specific;
        }
        return getForCategory(context, event.eventType);
    }

    @Nullable
    private static String getForEvent(Context context, String eventName) {
        if (eventName == null) {
            return null;
        }
        int resId = switch (eventName) {
            case "onCreate" -> R.string.auto9_event_desc_oncreate;
            case "onStart" -> R.string.auto9_event_desc_onstart;
            case "onResume" -> R.string.auto9_event_desc_onresume;
            case "onPause" -> R.string.auto9_event_desc_onpause;
            case "onStop" -> R.string.auto9_event_desc_onstop;
            case "onDestroy" -> R.string.auto9_event_desc_ondestroy;
            case "onRestart" -> R.string.auto9_event_desc_onrestart;
            case "onBackPressed" -> R.string.auto9_event_desc_onbackpressed;
            case "onOptionsItemSelected" -> R.string.auto9_event_desc_onoptionsitemselected;
            case "onActivityResult" -> R.string.auto9_event_desc_onactivityresult;
            case "onPostCreate" -> R.string.auto9_event_desc_onpostcreate;
            case "onSaveInstanceState" -> R.string.auto9_event_desc_onsaveinstancestate;
            case "onRestoreInstanceState" -> R.string.auto9_event_desc_onrestoreinstancestate;
            case "onCreateOptionsMenu" -> R.string.auto9_event_desc_oncreateoptionsmenu;
            case "onCreateContextMenu" -> R.string.auto9_event_desc_oncreatecontextmenu;
            case "onContextItemSelected" -> R.string.auto9_event_desc_oncontextitemselected;
            case "onClick" -> R.string.auto9_event_desc_onclick;
            case "onCheckedChange" -> R.string.auto9_event_desc_oncheckedchange;
            case "onTextChanged" -> R.string.auto9_event_desc_ontextchanged;
            case "onItemSelected" -> R.string.auto9_event_desc_onitemselected;
            case "onItemClicked" -> R.string.auto9_event_desc_onitemclicked;
            case "onItemLongClicked" -> R.string.auto9_event_desc_onitemlongclicked;
            default -> 0;
        };
        return resId == 0 ? null : context.getString(resId);
    }

    @Nullable
    private static String getForCategory(Context context, int eventType) {
        int resId = switch (eventType) {
            case EventBean.EVENT_TYPE_ACTIVITY -> R.string.auto9_event_desc_cat_activity;
            case EventBean.EVENT_TYPE_VIEW -> R.string.auto9_event_desc_cat_view;
            case EventBean.EVENT_TYPE_COMPONENT -> R.string.auto9_event_desc_cat_component;
            case EventBean.EVENT_TYPE_DRAWER_VIEW -> R.string.auto9_event_desc_cat_drawer;
            case EventBean.EVENT_TYPE_ETC -> R.string.auto9_event_desc_cat_moreblock;
            default -> 0;
        };
        return resId == 0 ? null : context.getString(resId);
    }
}
