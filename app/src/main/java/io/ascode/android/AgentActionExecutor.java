package io.ascode.android;

import android.graphics.Color;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.wq;
import mod.agus.jcoderz.beans.ViewBeans;

public final class AgentActionExecutor {

    public static final class Result {
        private final String reply;
        private final List<String> appliedLines;
        private final List<String> skippedLines;

        Result(String reply, List<String> appliedLines, List<String> skippedLines) {
            this.reply = reply == null ? "" : reply;
            this.appliedLines = appliedLines == null ? new ArrayList<>() : appliedLines;
            this.skippedLines = skippedLines == null ? new ArrayList<>() : skippedLines;
        }

        public String getReply() {
            return reply;
        }

        /** Lines describing actions that were actually applied to the project. */
        public List<String> getAppliedLines() {
            return appliedLines;
        }

        /** Lines describing actions that were ignored or failed (nothing changed). */
        public List<String> getSkippedLines() {
            return skippedLines;
        }

        public int getAppliedCount() {
            return appliedLines.size();
        }

        public int getSkippedCount() {
            return skippedLines.size();
        }

        /** Applied + skipped lines, in that order. */
        public List<String> getSummaryLines() {
            List<String> all = new ArrayList<>(appliedLines);
            all.addAll(skippedLines);
            return all;
        }
    }

    /**
     * In-memory deep copy of the project data touched by the agent, used to revert
     * everything if an action throws halfway through (safety net: the project must
     * never be left half-modified).
     */
    private static final class DataSnapshot {
        final HashMap<String, ArrayList<ViewBean>> views;
        final HashMap<String, HashMap<String, ArrayList<BlockBean>>> logic;
        final HashMap<String, ArrayList<EventBean>> events;
        final HashMap<String, ArrayList<ComponentBean>> components;

        DataSnapshot(HashMap<String, ArrayList<ViewBean>> views,
                     HashMap<String, HashMap<String, ArrayList<BlockBean>>> logic,
                     HashMap<String, ArrayList<EventBean>> events,
                     HashMap<String, ArrayList<ComponentBean>> components) {
            this.views = views;
            this.logic = logic;
            this.events = events;
            this.components = components;
        }
    }

    private final String scId;
    private final eC data;
    private final Set<String> usedIds = new HashSet<>();
    private final List<String> appliedLines = new ArrayList<>();
    private final List<String> skippedLines = new ArrayList<>();

    public AgentActionExecutor(String scId) {
        this.scId = scId;
        this.data = jC.a(scId);
        collectUsedIds();
    }

    public Result execute(JSONObject agentResponse) {
        appliedLines.clear();
        skippedLines.clear();
        String reply = agentResponse == null ? "" : agentResponse.optString("reply", "");
        JSONArray actions = agentResponse == null ? null : agentResponse.optJSONArray("actions");
        if (actions != null && actions.length() > 0) {
            DataSnapshot snapshot = null;
            File fileBackup = null;
            try {
                // Safety net: snapshot the project data (in memory + the project data
                // files under .AndroidSCode/bak/<scId>/, which is where eC.k() writes)
                // so a mid-way exception can be reverted instead of leaving the project
                // half-modified.
                snapshot = takeSnapshot();
                fileBackup = snapshotDataFiles();

                // Two passes: create views first, then update/delete/move and events/code,
                // so the rest of the actions always find the views they reference (small
                // models often emit actions out of order).
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < actions.length(); i++) {
                        JSONObject action = actions.optJSONObject(i);
                        if (action == null) {
                            continue;
                        }
                        boolean isCreateView = isCreateViewAction(action);
                        if (pass == 0 && !isCreateView) {
                            continue;
                        }
                        if (pass == 1 && isCreateView) {
                            continue;
                        }
                        executeAction(action);
                    }
                }
            } catch (Throwable throwable) {
                revertAfterFailure(snapshot, fileBackup, throwable);
            } finally {
                deleteQuietly(fileBackup);
            }
        }
        data.k();
        return new Result(reply, new ArrayList<>(appliedLines), new ArrayList<>(skippedLines));
    }

    private boolean isCreateViewAction(JSONObject action) {
        String type = normalizeActionType(action.optString("type", ""));
        return "add_view".equals(type);
    }

    private void executeAction(JSONObject action) {
        String type = normalizeActionType(action.optString("type", ""));
        switch (type) {
            case "inject_code":
            case "inject":
            case "add_block":
                injectCode(action);
                return;
            case "add_event":
                addEvent(action);
                return;
            case "add_view":
            case "create_view":
                addView(action);
                return;
            case "update_view":
                updateView(action);
                return;
            case "delete_view":
                deleteView(action);
                return;
            case "move_view":
                moveView(action);
                return;
            default:
                skippedLines.add("Accion desconocida ignorada: " + type);
        }
    }

    /**
     * Small models often confuse the action type with the view type (e.g. they
     * emit {"type":"button", ...} meaning add_view with a button). Normalize the
     * action type: if it looks like a view type, treat it as an add_view action.
     */
    private static String normalizeActionType(String raw) {
        String type = raw == null ? "" : raw.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        switch (type) {
            case "inject_code":
            case "inject":
            case "add_block":
            case "add_code":
                return "inject_code";
            case "add_event":
            case "set_event":
            case "onclick":
                return "add_event";
            case "add_view":
            case "create_view":
            case "add_ui":
            case "create_ui":
            case "add_component":
                return "add_view";
            case "update_view":
            case "modify_view":
            case "edit_view":
            case "set_view":
            case "change_view":
            case "update_view_props":
            case "update":
            case "modify":
                return "update_view";
            case "delete_view":
            case "remove_view":
            case "delete":
            case "remove":
            case "delete_ui":
            case "remove_ui":
                return "delete_view";
            case "move_view":
            case "order_view":
            case "reorder_view":
            case "reorder":
            case "move":
            case "set_parent":
            case "set_index":
                return "move_view";
            case "linear":
            case "linear_layout":
            case "vertical":
            case "horizontal":
            case "row":
            case "column":
            case "scroll":
            case "scrollview":
            case "vscroll":
            case "card":
            case "capsule":
            case "button":
            case "text":
            case "textview":
            case "label":
            case "input":
            case "edit":
            case "edittext":
            case "search":
            case "image":
            case "imageview":
            case "webview":
            case "web":
            case "progress":
            case "progressbar":
            case "list":
            case "listview":
            case "spinner":
            case "checkbox":
            case "switch":
            case "seekbar":
            case "seek_bar":
                return "add_view";
            default:
                return type;
        }
    }

    private void injectCode(JSONObject action) {
        String target = normalizeFileName(action.optString("target", "main"));
        String javaName = ProjectFileBean.getJavaName(target);
        String xmlName = ProjectFileBean.getXmlName(target);
        String event = action.optString("event", "initializeLogic").trim();
        String code = action.optString("code", "").trim();

        if (code.isEmpty()) {
            skippedLines.add("inject_code ignorado (sin codigo).");
            return;
        }

        String eventKey;
        if ("initializeLogic".equalsIgnoreCase(event) || "oncreate".equalsIgnoreCase(event)) {
            // The onCreate/initializeLogic activity event is always implicit in
            // Android SCode (it is rendered unconditionally by the Event tab), so it
            // must NOT be stored in the project data: doing so duplicates onCreate.
            eventKey = ProjectCodeInjector.INITIALIZE_LOGIC_EVENT;
        } else {
            String viewId = normalizeViewId(event);
            ViewBean view = data.c(xmlName, viewId);
            if (view == null) {
                skippedLines.add("inject_code ignorado: la vista '" + viewId + "' no existe en " + xmlName + ".");
                return;
            }
            ensureViewEvent(view, javaName, xmlName);
            eventKey = viewId + "_onClick";
        }

        if (ProjectCodeInjector.injectIntoEvent(scId, javaName, eventKey, code)) {
            appliedLines.add("Codigo inyectado en " + javaName + " (" + eventKey + ").");
        } else {
            skippedLines.add("No se pudo inyectar codigo en " + javaName + " (" + eventKey + ").");
        }
    }

    private void addEvent(JSONObject action) {
        String target = normalizeFileName(action.optString("target", "main"));
        String javaName = ProjectFileBean.getJavaName(target);
        String xmlName = ProjectFileBean.getXmlName(target);
        String viewId = normalizeViewId(action.optString("view_id", ""));
        String code = action.optString("code", "").trim();

        if (viewId.isEmpty()) {
            skippedLines.add("add_event ignorado (falta view_id).");
            return;
        }

        ViewBean view = data.c(xmlName, viewId);
        if (view == null) {
            skippedLines.add("add_event ignorado: la vista '" + viewId + "' no existe en " + xmlName + ".");
            return;
        }
        ensureViewEvent(view, javaName, xmlName);
        if (!code.isEmpty()) {
            ProjectCodeInjector.injectIntoEvent(scId, javaName, viewId + "_onClick", code);
        }
        appliedLines.add("Evento " + viewId + "_onClick listo en " + javaName + ".");
    }

    private void addView(JSONObject action) {
        String screen = normalizeFileName(action.optString("screen", "main"));
        String xmlName = ProjectFileBean.getXmlName(screen);
        String parentId = normalizeViewId(action.optString("parent", "root"));
        // Accept both "view_type" (preferred, unambiguous) and legacy "type".
        String type = action.optString("view_type", action.optString("type", "text"))
                .toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');

        int[] typeInfo = resolveViewType(type);
        if (typeInfo == null) {
            skippedLines.add("add_view ignorado: tipo '" + type + "' no soportado.");
            return;
        }
        int viewType = typeInfo[0];
        String convert = typeInfo[1] == 1 ? "androidx.cardview.widget.CardView" : defaultConvert(viewType);

        ViewBean view = new ViewBean();
        view.type = viewType;
        view.convert = convert;
        view.id = nextId(action.optString("id", ""), viewType);
        view.name = view.id;
        view.parent = parentId;
        view.parentType = parentId.equals("root") ? ViewBean.VIEW_TYPE_LAYOUT_LINEAR : parentTypeOf(parentId, xmlName);
        view.index = nextIndex(parentId, xmlName);

        String width = action.optString("width", "match_parent").trim().toLowerCase(Locale.US);
        String height = action.optString("height", "wrap_content").trim().toLowerCase(Locale.US);
        view.layout.width = parseDimension(width, LayoutBean.LAYOUT_MATCH_PARENT);
        view.layout.height = parseDimension(height, LayoutBean.LAYOUT_WRAP_CONTENT);

        if (viewType == ViewBean.VIEW_TYPE_LAYOUT_LINEAR) {
            String orientation = action.optString("orientation", "vertical").toLowerCase(Locale.US);
            view.layout.orientation = orientation.contains("horizontal") || orientation.contains("row")
                    ? LayoutBean.ORIENTATION_HORIZONTAL
                    : LayoutBean.ORIENTATION_VERTICAL;
        }

        String text = action.optString("text", "");
        if (!text.isEmpty()) {
            view.text.text = text;
        }
        int textSize = action.optInt("text_size", action.optInt("textSize", 0));
        if (textSize > 0) {
            view.text.textSize = textSize;
        }
        String hint = action.optString("hint", "");
        if (!hint.isEmpty()) {
            view.text.hint = hint;
        }

        view.layout.marginLeft = action.optInt("margin_left", action.optInt("marginLeft", 0));
        view.layout.marginTop = action.optInt("margin_top", action.optInt("marginTop", 0));
        view.layout.marginRight = action.optInt("margin_right", action.optInt("marginRight", 0));
        view.layout.marginBottom = action.optInt("margin_bottom", action.optInt("marginBottom", 0));

        String backgroundColor = action.optString("background_color", action.optString("backgroundColor", ""));
        if (!backgroundColor.isEmpty()) {
            try {
                view.layout.backgroundColor = Color.parseColor(backgroundColor);
            } catch (IllegalArgumentException ignored) {
                android.util.Log.d("Ascode", "AgentActionExecutor: IllegalArgumentException ignored", ignored);
            }
        }
        // Optional extras (text_color, padding, gravity, weight...) supported on create too.
        applyOptionalViewFields(view, action);

        data.a(xmlName, view);
        usedIds.add(view.id);
        appliedLines.add(viewName(view) + " '" + view.id + "' agregado en " + xmlName + ".");

        if (viewType == ViewBean.VIEW_TYPE_WIDGET_BUTTON) {
            String javaName = ProjectFileBean.getJavaName(screen);
            ensureViewEvent(view, javaName, xmlName);
        }
    }

    /**
     * Modifies an existing view; only the fields present in the JSON are touched.
     * Returns the list of human-readable changes (empty if nothing was requested).
     */
    private void updateView(JSONObject action) {
        String screen = normalizeFileName(firstNonEmpty(action, "screen", "target", "file"));
        String xmlName = ProjectFileBean.getXmlName(screen);
        String viewId = normalizeViewId(firstNonEmpty(action, "id", "view_id", "target_id", "view"));

        if (viewId.isEmpty()) {
            skippedLines.add("update_view ignorado (falta id).");
            return;
        }
        ViewBean view = data.c(xmlName, viewId);
        if (view == null) {
            skippedLines.add("update_view omitido: la vista '" + viewId + "' no existe en " + xmlName + ".");
            return;
        }
        List<String> changes = applyOptionalViewFields(view, action);
        if (changes.isEmpty()) {
            skippedLines.add("update_view omitido: no se indico ningun campo a cambiar en '" + viewId + "'.");
            return;
        }
        appliedLines.add("Vista '" + viewId + "' actualizada en " + xmlName
                + " (" + join(changes) + ").");
    }

    /**
     * Deletes an existing view, its descendants and every event (plus its logic
     * blocks) attached to any of them. Uses the same removal path as the visual
     * editor (eC.a(ProjectFileBean, ViewBean)) so nothing dangles.
     */
    private void deleteView(JSONObject action) {
        String screen = normalizeFileName(firstNonEmpty(action, "screen", "target", "file"));
        String xmlName = ProjectFileBean.getXmlName(screen);
        String javaName = ProjectFileBean.getJavaName(screen);
        String viewId = normalizeViewId(firstNonEmpty(action, "id", "view_id", "target_id", "view"));

        if (viewId.isEmpty()) {
            skippedLines.add("delete_view ignorado (falta id).");
            return;
        }
        if ("root".equals(viewId)) {
            skippedLines.add("delete_view ignorado: no se puede borrar 'root'.");
            return;
        }
        ViewBean view = data.c(xmlName, viewId);
        if (view == null) {
            skippedLines.add("delete_view omitido: la vista '" + viewId + "' no existe en " + xmlName + ".");
            return;
        }

        ProjectFileBean projectFile = projectFileFor(screen, xmlName, javaName);
        ArrayList<ViewBean> subtree = data.b(xmlName, view);
        if (subtree == null || subtree.isEmpty()) {
            subtree = new ArrayList<>(Collections.singletonList(view));
        }
        String oldParent = view.parent;
        int removed = 0;
        for (int i = subtree.size() - 1; i >= 0; i--) {
            ViewBean current = subtree.get(i);
            if (current == null) {
                continue;
            }
            removeView(current, projectFile, xmlName, javaName);
            removed++;
        }
        normalizeIndices(oldParent, xmlName);
        usedIds.remove(viewId);
        appliedLines.add("Vista '" + viewId + "' (" + removed + " con hijas) eliminada de " + xmlName
                + "; eventos asociados borrados.");
    }

    /**
     * Moves a view to another parent and/or position (order) inside its screen.
     */
    private void moveView(JSONObject action) {
        String screen = normalizeFileName(firstNonEmpty(action, "screen", "target", "file"));
        String xmlName = ProjectFileBean.getXmlName(screen);
        String viewId = normalizeViewId(firstNonEmpty(action, "id", "view_id", "target_id", "view"));

        if (viewId.isEmpty()) {
            skippedLines.add("move_view ignorado (falta id).");
            return;
        }
        if ("root".equals(viewId)) {
            skippedLines.add("move_view ignorado: 'root' no se puede mover.");
            return;
        }
        ViewBean view = data.c(xmlName, viewId);
        if (view == null) {
            skippedLines.add("move_view omitido: la vista '" + viewId + "' no existe en " + xmlName + ".");
            return;
        }

        String oldParent = view.parent == null ? "root" : view.parent;
        String requestedParent = normalizeViewId(action.optString("parent", action.optString("new_parent", "")));
        String targetParent = requestedParent.isEmpty() ? oldParent : requestedParent;

        if (targetParent.equals(viewId)) {
            skippedLines.add("move_view omitido: '" + viewId + "' no puede ser su propio padre.");
            return;
        }
        if (!targetParent.equals("root") && data.c(xmlName, targetParent) == null) {
            skippedLines.add("move_view omitido: el padre '" + targetParent + "' no existe en " + xmlName + ".");
            return;
        }
        if (!targetParent.equals("root") && isDescendant(viewId, targetParent, xmlName)) {
            skippedLines.add("move_view omitido: '" + targetParent + "' es una vista hija de '" + viewId
                    + "' (se crearia un ciclo).");
            return;
        }

        boolean hasIndex = action.has("index") || action.has("position")
                || action.has("order") || action.has("to_index") || action.has("new_index");
        int requestedIndex = action.optInt("index", action.optInt("position",
                action.optInt("order", action.optInt("to_index", action.optInt("new_index", -1)))));

        ArrayList<ViewBean> targetChildren = childrenOf(targetParent, xmlName, viewId);
        int position = hasIndex
                ? clamp(requestedIndex, 0, targetChildren.size())
                : targetChildren.size();
        targetChildren.add(position, view);

        view.parent = targetParent;
        view.parentType = "root".equals(targetParent)
                ? ViewBean.VIEW_TYPE_LAYOUT_LINEAR
                : parentTypeOf(targetParent, xmlName);
        for (int i = 0; i < targetChildren.size(); i++) {
            targetChildren.get(i).index = i;
        }
        if (!targetParent.equals(oldParent)) {
            normalizeIndices(oldParent, xmlName);
        }
        appliedLines.add("Vista '" + viewId + "' movida a '" + targetParent + "' (indice "
                + view.index + ") en " + xmlName + ".");
    }

    /**
     * Applies only the fields actually present in the JSON object. Returns the list
     * of changed fields so callers can report "aplicado" vs "omitido" honestly.
     */
    private List<String> applyOptionalViewFields(ViewBean view, JSONObject action) {
        List<String> changes = new ArrayList<>();
        if (view == null || action == null) {
            return changes;
        }
        if (view.layout == null) {
            view.layout = new LayoutBean();
        }
        if (view.text == null) {
            view.text = new TextBean();
        }

        if (action.has("text")) {
            String value = action.optString("text", "");
            view.text.text = value;
            changes.add("text=\"" + value + "\"");
        }
        if (action.has("text_size") || action.has("textSize")) {
            int value = action.optInt("text_size", action.optInt("textSize", view.text.textSize));
            if (value > 0) {
                view.text.textSize = value;
                changes.add("text_size=" + value);
            }
        }
        if (action.has("hint")) {
            String value = action.optString("hint", "");
            view.text.hint = value;
            changes.add("hint=\"" + value + "\"");
        }
        Integer textColor = firstColor(action, "text_color", "textColor");
        if (textColor != null) {
            view.text.textColor = textColor;
            changes.add("text_color");
        }
        if (action.has("width")) {
            view.layout.width = parseDimension(action.optString("width", ""), view.layout.width);
            changes.add("width");
        }
        if (action.has("height")) {
            view.layout.height = parseDimension(action.optString("height", ""), view.layout.height);
            changes.add("height");
        }
        if (action.has("orientation")) {
            view.layout.orientation = parseOrientation(action.optString("orientation", ""), view.layout.orientation);
            changes.add("orientation");
        }
        if (action.has("margin")) {
            int value = action.optInt("margin", 0);
            view.layout.marginLeft = value;
            view.layout.marginTop = value;
            view.layout.marginRight = value;
            view.layout.marginBottom = value;
            changes.add("margin=" + value);
        }
        if (applyMargin(action, "margin_left", "marginLeft", view, 0)) {
            changes.add("margin_left");
        }
        if (applyMargin(action, "margin_top", "marginTop", view, 1)) {
            changes.add("margin_top");
        }
        if (applyMargin(action, "margin_right", "marginRight", view, 2)) {
            changes.add("margin_right");
        }
        if (applyMargin(action, "margin_bottom", "marginBottom", view, 3)) {
            changes.add("margin_bottom");
        }
        if (action.has("padding")) {
            int value = action.optInt("padding", 0);
            view.layout.paddingLeft = value;
            view.layout.paddingTop = value;
            view.layout.paddingRight = value;
            view.layout.paddingBottom = value;
            changes.add("padding=" + value);
        }
        if (applyPadding(action, "padding_left", "paddingLeft", view, 0)) {
            changes.add("padding_left");
        }
        if (applyPadding(action, "padding_top", "paddingTop", view, 1)) {
            changes.add("padding_top");
        }
        if (applyPadding(action, "padding_right", "paddingRight", view, 2)) {
            changes.add("padding_right");
        }
        if (applyPadding(action, "padding_bottom", "paddingBottom", view, 3)) {
            changes.add("padding_bottom");
        }
        Integer background = firstColor(action, "background_color", "backgroundColor");
        if (background != null) {
            view.layout.backgroundColor = background;
            changes.add("background_color");
        }
        if (action.has("gravity")) {
            view.layout.gravity = parseGravity(action.optString("gravity", ""), view.layout.gravity);
            changes.add("gravity");
        }
        if (action.has("layout_gravity") || action.has("layoutGravity")) {
            view.layout.layoutGravity = parseGravity(
                    action.optString("layout_gravity", action.optString("layoutGravity", "")),
                    view.layout.layoutGravity);
            changes.add("layout_gravity");
        }
        if (action.has("weight")) {
            view.layout.weight = action.optInt("weight", view.layout.weight);
            changes.add("weight");
        }
        return changes;
    }

    private boolean applyMargin(JSONObject action, String snake, String camel, ViewBean view, int side) {
        if (!action.has(snake) && !action.has(camel)) {
            return false;
        }
        int value = action.optInt(snake, action.optInt(camel, 0));
        switch (side) {
            case 0 -> view.layout.marginLeft = value;
            case 1 -> view.layout.marginTop = value;
            case 2 -> view.layout.marginRight = value;
            default -> view.layout.marginBottom = value;
        }
        return true;
    }

    private boolean applyPadding(JSONObject action, String snake, String camel, ViewBean view, int side) {
        if (!action.has(snake) && !action.has(camel)) {
            return false;
        }
        int value = action.optInt(snake, action.optInt(camel, 0));
        switch (side) {
            case 0 -> view.layout.paddingLeft = value;
            case 1 -> view.layout.paddingTop = value;
            case 2 -> view.layout.paddingRight = value;
            default -> view.layout.paddingBottom = value;
        }
        return true;
    }

    /**
     * Removes one view from the project using the same path as the visual editor,
     * and guarantees its events (and the blocks they held) are gone.
     */
    private void removeView(ViewBean view, ProjectFileBean projectFile, String xmlName, String javaName) {
        if (view == null || view.id == null) {
            return;
        }
        if (projectFile != null) {
            data.a(projectFile, view);
        } else {
            ArrayList<ViewBean> views = data.c.get(xmlName);
            if (views != null) {
                views.removeIf(candidate -> candidate != null && view.id.equals(candidate.id));
            }
        }
        // Idempotent: also make sure there are no dangling events/blocks for this id.
        data.m(javaName, view.id);
    }

    private void ensureViewEvent(ViewBean view, String javaName, String xmlName) {
        if (hasOnClickEvent(javaName, view.id)) {
            return;
        }
        ViewBean existing = data.c(xmlName, view.id);
        if (existing != null) {
            data.a(javaName, EventBean.EVENT_TYPE_VIEW, existing.type, existing.id, "onClick");
        } else {
            data.a(javaName, EventBean.EVENT_TYPE_VIEW, view.type, view.id, "onClick");
        }
    }

    private boolean hasOnClickEvent(String javaName, String viewId) {
        ArrayList<EventBean> events = data.g(javaName);
        if (events == null) {
            return false;
        }
        for (EventBean event : events) {
            if (event != null
                    && event.eventType == EventBean.EVENT_TYPE_VIEW
                    && "onClick".equals(event.eventName)
                    && viewId.equals(event.targetId)) {
                return true;
            }
        }
        return false;
    }

    private ProjectFileBean projectFileFor(String screen, String xmlName, String javaName) {
        try {
            hC projectFiles = jC.b(scId);
            if (projectFiles == null) {
                return null;
            }
            ProjectFileBean byXml = projectFiles.b(xmlName);
            if (byXml != null) {
                return byXml;
            }
            return projectFiles.a(javaName);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private ArrayList<ViewBean> childrenOf(String parentId, String xmlName, String excludeId) {
        ArrayList<ViewBean> children = new ArrayList<>();
        ArrayList<ViewBean> views = data.d(xmlName);
        if (views == null) {
            return children;
        }
        for (ViewBean view : views) {
            if (view == null) {
                continue;
            }
            if (parentId.equals(view.parent) && (excludeId == null || !excludeId.equals(view.id))) {
                children.add(view);
            }
        }
        children.sort(Comparator.comparingInt((ViewBean v) -> v.index));
        return children;
    }

    private void normalizeIndices(String parentId, String xmlName) {
        if (parentId == null) {
            return;
        }
        ArrayList<ViewBean> children = childrenOf(parentId, xmlName, null);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).index = i;
        }
    }

    private boolean isDescendant(String ancestorId, String candidateId, String xmlName) {
        if (ancestorId == null || candidateId == null) {
            return false;
        }
        String current = candidateId;
        int guard = 0;
        while (current != null && !"root".equals(current) && guard++ < 1000) {
            if (current.equals(ancestorId)) {
                return true;
            }
            ViewBean parent = data.c(xmlName, current);
            if (parent == null) {
                return false;
            }
            current = parent.parent;
        }
        return false;
    }

    private int parentTypeOf(String parentId, String xmlName) {
        if ("root".equals(parentId)) {
            return ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        }
        ViewBean parent = data.c(xmlName, parentId);
        return parent == null ? ViewBean.VIEW_TYPE_LAYOUT_LINEAR : parent.type;
    }

    private int nextIndex(String parentId, String xmlName) {
        int max = -1;
        ArrayList<ViewBean> views = data.d(xmlName);
        if (views != null) {
            for (ViewBean view : views) {
                if (view != null && parentId.equals(view.parent) && view.index > max) {
                    max = view.index;
                }
            }
        }
        return max + 1;
    }

    private String nextId(String hint, int viewType) {
        String base;
        if (hint == null || hint.trim().isEmpty()) {
            base = defaultIdBase(viewType);
        } else {
            base = normalizeViewId(hint);
        }
        String candidate = base;
        int counter = 1;
        while (usedIds.contains(candidate)) {
            candidate = base + (counter++);
        }
        usedIds.add(candidate);
        return candidate;
    }

    private void collectUsedIds() {
        if (data.c == null) {
            return;
        }
        for (ArrayList<ViewBean> views : data.c.values()) {
            if (views == null) {
                continue;
            }
            for (ViewBean view : views) {
                if (view != null && view.id != null) {
                    usedIds.add(view.id);
                }
            }
        }
        usedIds.add("root");
    }

    // ------------------------------------------------------------------
    // Safety net: snapshot / revert
    // ------------------------------------------------------------------

    private DataSnapshot takeSnapshot() {
        HashMap<String, ArrayList<ViewBean>> views = new HashMap<>();
        if (data.c != null) {
            for (Map.Entry<String, ArrayList<ViewBean>> entry : data.c.entrySet()) {
                ArrayList<ViewBean> copy = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (ViewBean view : entry.getValue()) {
                        copy.add(view == null ? null : view.clone());
                    }
                }
                views.put(entry.getKey(), copy);
            }
        }
        HashMap<String, HashMap<String, ArrayList<BlockBean>>> logic = new HashMap<>();
        if (data.d != null) {
            for (Map.Entry<String, HashMap<String, ArrayList<BlockBean>>> entry : data.d.entrySet()) {
                HashMap<String, ArrayList<BlockBean>> inner = new HashMap<>();
                if (entry.getValue() != null) {
                    for (Map.Entry<String, ArrayList<BlockBean>> blockEntry : entry.getValue().entrySet()) {
                        inner.put(blockEntry.getKey(), blockEntry.getValue() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(blockEntry.getValue()));
                    }
                }
                logic.put(entry.getKey(), inner);
            }
        }
        HashMap<String, ArrayList<EventBean>> events = new HashMap<>();
        if (data.i != null) {
            for (Map.Entry<String, ArrayList<EventBean>> entry : data.i.entrySet()) {
                events.put(entry.getKey(), entry.getValue() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(entry.getValue()));
            }
        }
        HashMap<String, ArrayList<ComponentBean>> components = new HashMap<>();
        if (data.h != null) {
            for (Map.Entry<String, ArrayList<ComponentBean>> entry : data.h.entrySet()) {
                components.put(entry.getKey(), entry.getValue() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(entry.getValue()));
            }
        }
        return new DataSnapshot(views, logic, events, components);
    }

    private void restoreSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("no snapshot");
        }
        if (data.c != null && snapshot.views != null) {
            data.c.clear();
            data.c.putAll(snapshot.views);
        }
        if (data.d != null && snapshot.logic != null) {
            data.d.clear();
            data.d.putAll(snapshot.logic);
        }
        if (data.i != null && snapshot.events != null) {
            data.i.clear();
            data.i.putAll(snapshot.events);
        }
        if (data.h != null && snapshot.components != null) {
            data.h.clear();
            data.h.putAll(snapshot.components);
        }
        data.k();
    }

    /**
     * Copies the project data files (the ones eC.k() writes under
     * .AndroidSCode/bak/<scId>/view and /logic) before touching anything, as a
     * second, file-level safety net.
     */
    private File snapshotDataFiles() {
        try {
            File source = new File(wq.a(scId));
            if (!source.exists()) {
                return null;
            }
            File root = new File(wq.getAbsolutePathOf(wq.e + File.separator + "agent_backup"));
            File target = new File(root, scId + "_" + System.currentTimeMillis());
            copyDirectory(source, target);
            return target;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void restoreDataFiles(File backup) {
        if (backup == null || !backup.exists()) {
            throw new IllegalStateException("no file backup");
        }
        File target = new File(wq.a(scId));
        deleteRecursively(target);
        try {
            copyDirectory(backup, target);
        } catch (IOException ioException) {
            throw new IllegalStateException(ioException);
        }
    }

    private void revertAfterFailure(DataSnapshot snapshot, File fileBackup, Throwable throwable) {
        boolean restored = false;
        try {
            restoreSnapshot(snapshot);
            restored = true;
        } catch (Throwable inMemoryFailure) {
            try {
                restoreDataFiles(fileBackup);
                // Reload the data so the (still referenced) in-memory manager matches
                // the restored files as closely as possible.
                restored = true;
            } catch (Throwable fileFailure) {
                android.util.Log.w("Ascode", "AgentActionExecutor: revert failed", fileFailure);
            }
        }

        if (!appliedLines.isEmpty()) {
            List<String> reverted = new ArrayList<>();
            for (String line : appliedLines) {
                reverted.add("Revertido: " + line);
            }
            appliedLines.clear();
            skippedLines.addAll(0, reverted);
        }
        String message = throwable == null || throwable.getMessage() == null
                ? String.valueOf(throwable)
                : throwable.getMessage();
        skippedLines.add("Error al aplicar acciones (" + message + "). "
                + (restored ? "El proyecto se restauro al estado previo." : "No se pudo restaurar el proyecto."));
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (source == null || !source.exists()) {
            return;
        }
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("No se pudo crear " + target.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(target, child.getName()));
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent.getAbsolutePath());
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        deleteRecursively(file);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int[] resolveViewType(String type) {
        if (type.contains("linear") || type.contains("column") || type.contains("vertical") || type.contains("horizontal") || type.contains("row")) {
            return new int[]{ViewBean.VIEW_TYPE_LAYOUT_LINEAR, 0};
        }
        if (type.contains("scroll")) {
            return new int[]{ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW, 0};
        }
        if (type.contains("card") || type.contains("capsule")) {
            return new int[]{ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW, 1};
        }
        if (type.contains("button")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_BUTTON, 0};
        }
        if (type.contains("input") || type.contains("edit") || type.contains("search")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_EDITTEXT, 0};
        }
        if (type.contains("image")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW, 0};
        }
        if (type.contains("webview") || type.contains("web")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_WEBVIEW, 0};
        }
        if (type.contains("progress")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR, 0};
        }
        if (type.contains("list")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_LISTVIEW, 0};
        }
        if (type.contains("spinner")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SPINNER, 0};
        }
        if (type.contains("checkbox")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_CHECKBOX, 0};
        }
        if (type.contains("switch")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SWITCH, 0};
        }
        if (type.contains("seekbar")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SEEKBAR, 0};
        }
        if (type.contains("text") || type.contains("label")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, 0};
        }
        return null;
    }

    private static String defaultConvert(int viewType) {
        switch (viewType) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "LinearLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "ScrollView";
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON:
                return "Button";
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW:
                return "TextView";
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT:
                return "EditText";
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW:
                return "ImageView";
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW:
                return "WebView";
            case ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR:
                return "ProgressBar";
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW:
                return "ListView";
            case ViewBean.VIEW_TYPE_WIDGET_SPINNER:
                return "Spinner";
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX:
                return "CheckBox";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "Switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "SeekBar";
            default:
                return null;
        }
    }

    private static String defaultIdBase(int viewType) {
        switch (viewType) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "linear";
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "scroll";
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON:
                return "button";
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW:
                return "text";
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT:
                return "input";
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW:
                return "image";
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW:
                return "webview";
            case ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR:
                return "progress";
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW:
                return "list";
            case ViewBean.VIEW_TYPE_WIDGET_SPINNER:
                return "spinner";
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX:
                return "checkbox";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "seekbar";
            default:
                return "view";
        }
    }

    private static int parseDimension(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        if (value.contains("match")) {
            return LayoutBean.LAYOUT_MATCH_PARENT;
        }
        if (value.contains("wrap")) {
            return LayoutBean.LAYOUT_WRAP_CONTENT;
        }
        try {
            return (int) Double.parseDouble(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseOrientation(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.US);
        if (normalized.contains("horizontal") || normalized.contains("row")) {
            return LayoutBean.ORIENTATION_HORIZONTAL;
        }
        if (normalized.contains("vertical") || normalized.contains("column")) {
            return LayoutBean.ORIENTATION_VERTICAL;
        }
        return fallback;
    }

    private static int parseGravity(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.US).replace('-', '_').trim();
        if (normalized.contains("center")) {
            return LayoutBean.GRAVITY_CENTER;
        }
        int gravity = LayoutBean.GRAVITY_NONE;
        boolean matched = false;
        if (normalized.contains("left") || normalized.contains("start")) {
            gravity |= LayoutBean.GRAVITY_LEFT;
            matched = true;
        }
        if (normalized.contains("right") || normalized.contains("end")) {
            gravity |= LayoutBean.GRAVITY_RIGHT;
            matched = true;
        }
        if (normalized.contains("top")) {
            gravity |= LayoutBean.GRAVITY_TOP;
            matched = true;
        }
        if (normalized.contains("bottom")) {
            gravity |= LayoutBean.GRAVITY_BOTTOM;
            matched = true;
        }
        return matched ? gravity : fallback;
    }

    private static Integer parseColorOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Integer firstColor(JSONObject action, String... keys) {
        for (String key : keys) {
            if (action.has(key)) {
                return parseColorOrNull(action.optString(key, ""));
            }
        }
        return null;
    }

    private static String firstNonEmpty(JSONObject action, String... keys) {
        for (String key : keys) {
            if (action.has(key)) {
                String value = action.optString(key, "");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String viewName(ViewBean view) {
        String base = defaultConvert(view.type);
        return base == null ? "Vista" : base;
    }

    private static String normalizeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "main";
        }
        String normalized = value.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.toLowerCase(Locale.US).endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.toLowerCase(Locale.US).endsWith(".xml")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.isEmpty() ? "main" : normalized;
    }

    private static String normalizeViewId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_]", "").replaceAll("^_+|_+$", "");
    }
}
