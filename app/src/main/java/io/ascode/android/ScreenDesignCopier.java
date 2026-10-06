package io.ascode.android;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import a.a.a.eC;
import a.a.a.jC;

/**
 * Copies the <b>design</b> (every widget with its properties/positions) and the
 * <b>logic</b> (blocks, events, components, variables and lists) of one screen
 * onto another one, mutating the in-memory project data managed by {@link eC}.
 *
 * <p>The copy is a deep copy, so the destination screen can be edited afterwards
 * without touching the source. Widget ids are kept <b>identical on purpose</b>:
 * the logic blocks reference those ids and ids are scoped per screen, so keeping
 * them is what makes the copied blocks keep working on the destination.</p>
 *
 * <p>This class only touches the in-memory project data (the same maps the normal
 * editor uses). Persisting and reloading the UI is the caller's job, using the
 * activity's normal save/refresh path (e.g. {@code jC.a(scId).j()},
 * {@code jC.b(scId).m()} and {@code refresh()}).</p>
 */
public final class ScreenDesignCopier {

    private ScreenDesignCopier() {
    }

    /** Human-readable summary of a copy, for the success toast. */
    public static final class Result {
        public int views;
        public int blocks;
        public int events;
        public int components;
        public int variables;
        public int lists;
        public boolean fab;

        public boolean hasLogic() {
            return blocks > 0 || events > 0 || components > 0;
        }
    }

    /**
     * Copies {@code source} onto {@code target}. Both must belong to {@code scId}.
     * Throws only for programming/state errors (missing project data / screens).
     */
    public static Result copy(String scId, ProjectFileBean source, ProjectFileBean target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source/target screen must not be null");
        }
        if (source.getXmlName().equals(target.getXmlName())) {
            throw new IllegalArgumentException("source and target screen must differ");
        }
        eC data = jC.a(scId);
        if (data == null) {
            throw new IllegalStateException("project data not loaded: " + scId);
        }
        // Defensive: these maps are always initialized by eC's constructor, but a
        // half-loaded project must never make the copy crash with a NPE.
        if (data.c == null) data.c = new HashMap<>();
        if (data.d == null) data.d = new HashMap<>();
        if (data.e == null) data.e = new HashMap<>();
        if (data.f == null) data.f = new HashMap<>();
        if (data.g == null) data.g = new HashMap<>();
        if (data.h == null) data.h = new HashMap<>();
        if (data.i == null) data.i = new HashMap<>();
        if (data.j == null) data.j = new HashMap<>();

        Result result = new Result();
        String srcXml = source.getXmlName();
        String dstXml = target.getXmlName();
        String srcJava = source.getJavaName();
        String dstJava = target.getJavaName();

        // --- Structure (design) ---
        result.views = replaceViews(data, srcXml, dstXml);
        result.fab = replaceFab(data, source, target, srcXml, dstXml);

        // --- Logic (only activities have a Java name / logic map) ---
        if (!srcJava.isEmpty() && !dstJava.isEmpty()) {
            result.blocks = replaceLogic(data, srcJava, dstJava);
            result.events = replaceEvents(data, srcJava, dstJava);
            result.components = replaceComponents(data, srcJava, dstJava);
            result.variables = replaceIntegerPairs(data.e, srcJava, dstJava);
            result.lists = replaceIntegerPairs(data.f, srcJava, dstJava);
            replaceStringPairs(data.g, srcJava, dstJava); // custom "more blocks"
        }
        return result;
    }

    /** Replaces the destination's widget list with a deep copy of the source's. */
    private static int replaceViews(eC data, String srcXml, String dstXml) {
        ArrayList<ViewBean> cloned = new ArrayList<>();
        ArrayList<ViewBean> source = data.c.get(srcXml);
        if (source != null) {
            for (ViewBean view : source) {
                if (view == null) {
                    continue;
                }
                ViewBean copy = view.clone();
                // ViewBean#copy shares the parentAttributes map with the original;
                // give the copy its own so later edits don't leak into the source.
                if (view.parentAttributes != null) {
                    copy.parentAttributes = new HashMap<>(view.parentAttributes);
                }
                cloned.add(copy);
            }
        }
        data.c.put(dstXml, cloned);
        return cloned.size();
    }

    /**
     * Copies the Floating Action Button. The FAB lives in its own map (not in the
     * widget list) and is only rendered when the screen has the FAB option, so it
     * is only copied when both screens support it.
     */
    private static boolean replaceFab(eC data, ProjectFileBean source, ProjectFileBean target,
                                      String srcXml, String dstXml) {
        boolean sourceHasFab = source.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                && source.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB);
        boolean targetHasFab = target.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                && target.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB);
        if (sourceHasFab && targetHasFab) {
            ViewBean fab = data.h(srcXml); // lazily creates the default FAB if needed
            if (fab != null) {
                data.j.put(dstXml, fab.clone());
                return true;
            }
        }
        data.j.remove(dstXml);
        return false;
    }

    private static int replaceLogic(eC data, String srcJava, String dstJava) {
        HashMap<String, ArrayList<BlockBean>> cloned = new HashMap<>();
        int count = 0;
        HashMap<String, ArrayList<BlockBean>> source = data.d.get(srcJava);
        if (source != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : source.entrySet()) {
                ArrayList<BlockBean> copy = new ArrayList<>();
                ArrayList<BlockBean> blocks = entry.getValue();
                if (blocks != null) {
                    for (BlockBean block : blocks) {
                        if (block != null) {
                            copy.add(block.clone());
                            count++;
                        }
                    }
                }
                cloned.put(entry.getKey(), copy);
            }
        }
        data.d.put(dstJava, cloned);
        return count;
    }

    private static int replaceEvents(eC data, String srcJava, String dstJava) {
        ArrayList<EventBean> cloned = new ArrayList<>();
        ArrayList<EventBean> source = data.i.get(srcJava);
        if (source != null) {
            for (EventBean event : source) {
                if (event != null) {
                    cloned.add(new EventBean(event.eventType, event.targetType,
                            event.targetId, event.eventName));
                }
            }
        }
        data.i.put(dstJava, cloned);
        return cloned.size();
    }

    private static int replaceComponents(eC data, String srcJava, String dstJava) {
        ArrayList<ComponentBean> cloned = new ArrayList<>();
        ArrayList<ComponentBean> source = data.h.get(srcJava);
        if (source != null) {
            for (ComponentBean component : source) {
                if (component != null) {
                    ComponentBean copy = new ComponentBean(component.type);
                    copy.copy(component);
                    cloned.add(copy);
                }
            }
        }
        data.h.put(dstJava, cloned);
        return cloned.size();
    }

    /** Variables (eC.e) and lists (eC.f) are {@code Pair<Integer, String>}, immutable. */
    private static int replaceIntegerPairs(
            HashMap<String, ArrayList<Pair<Integer, String>>> map, String srcJava, String dstJava) {
        ArrayList<Pair<Integer, String>> source = map.get(srcJava);
        ArrayList<Pair<Integer, String>> cloned =
                source == null ? new ArrayList<>() : new ArrayList<>(source);
        map.put(dstJava, cloned);
        return cloned.size();
    }

    /** Custom "more blocks" definitions (eC.g), {@code Pair<String, String>}. */
    private static void replaceStringPairs(
            HashMap<String, ArrayList<Pair<String, String>>> map, String srcJava, String dstJava) {
        ArrayList<Pair<String, String>> source = map.get(srcJava);
        map.put(dstJava, source == null ? new ArrayList<>() : new ArrayList<>(source));
    }
}
