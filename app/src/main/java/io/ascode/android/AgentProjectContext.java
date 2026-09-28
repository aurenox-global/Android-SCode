package io.ascode.android;

import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;

public final class AgentProjectContext {

    private AgentProjectContext() {
    }

    public static String build(String scId) {
        if (scId == null || scId.trim().isEmpty()) {
            return "";
        }
        try {
            eC data = jC.a(scId);
            StringBuilder sb = new StringBuilder();
            sb.append("- Scope: ").append(scId).append('\n');
            appendScreens(sb, scId, data);
            appendEvents(sb, data);
            appendVariables(sb, scId, data);
            appendPermissions(sb, scId);
            String files = ProjectCodeInjector.buildProjectContext(scId);
            if (!files.isEmpty()) {
                sb.append("- Archivos:\n").append(files);
            }
            return sb.toString();
        } catch (Throwable throwable) {
            return "";
        }
    }

    private static void appendScreens(StringBuilder sb, String scId, eC data) {
        sb.append("- Pantallas (screen = nombre de la pantalla; main es la principal):\n");
        List<String> xmlNames = new ArrayList<>();
        List<String> emptyScreens = new ArrayList<>();
        try {
            hC projectFiles = jC.b(scId);
            if (projectFiles != null) {
                for (ProjectFileBean projectFile : projectFiles.b()) {
                    if (projectFile != null && projectFile.fileName != null) {
                        xmlNames.add(projectFile.getXmlName());
                        if (data.c.get(projectFile.getXmlName()) == null || data.c.get(projectFile.getXmlName()).isEmpty()) {
                            emptyScreens.add(projectFile.fileName);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through to the raw data map
        }
        Map<String, ArrayList<ViewBean>> screens = data.c;
        if (screens != null) {
            for (String xmlName : screens.keySet()) {
                if (!xmlNames.contains(xmlName)) {
                    xmlNames.add(xmlName);
                }
            }
        }
        if (xmlNames.isEmpty()) {
            sb.append("  (sin layouts registrados)\n");
            return;
        }
        int totalViews = 0;
        for (String xmlName : xmlNames) {
            ArrayList<ViewBean> views = screens == null ? null : screens.get(xmlName);
            if (views == null || views.isEmpty()) {
                sb.append("  ").append(xmlName).append(" (vacia)\n");
                continue;
            }
            sb.append("  ").append(xmlName).append(":\n");
            int listed = 0;
            for (ViewBean view : views) {
                if (view == null || totalViews >= 40) {
                    continue;
                }
                sb.append("    - id=").append(view.id)
                        .append(" tipo=").append(typeName(view.type))
                        .append(" padre=").append(view.parent == null ? "?" : view.parent)
                        .append('\n');
                totalViews++;
                listed++;
            }
            if (listed == 0 && totalViews >= 40) {
                sb.append("    (más views omitidas por límite)\n");
            }
        }
        if (!emptyScreens.isEmpty()) {
            sb.append("  Pantallas vacias (sin views todavia): ").append(String.join(", ", emptyScreens)).append('\n');
        }
        if (totalViews >= 40) {
            sb.append("  (limited to 40 views to keep the prompt light)\n");
        }
    }

    private static void appendEvents(StringBuilder sb, eC data) {
        sb.append("- Eventos con bloques:\n");
        Map<String, ArrayList<EventBean>> events = data.i;
        boolean any = false;
        if (events != null) {
            for (Map.Entry<String, ArrayList<EventBean>> entry : events.entrySet()) {
                ArrayList<EventBean> list = entry.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }
                for (EventBean event : list) {
                    if (event == null) {
                        continue;
                    }
                    any = true;
                    sb.append("  ").append(entry.getKey()).append(" -> ")
                            .append(event.targetId).append('_').append(event.eventName).append('\n');
                }
            }
        }
        if (!any) {
            sb.append("  (sin eventos)\n");
        }
    }

    private static void appendVariables(StringBuilder sb, String scId, eC data) {
        sb.append("- Variables del proyecto (por archivo java):\n");
        int listed = 0;
        try {
            hC projectFiles = jC.b(scId);
            if (projectFiles != null) {
                for (ProjectFileBean projectFile : projectFiles.b()) {
                    if (projectFile == null) {
                        continue;
                    }
                    String javaName = projectFile.getJavaName();
                    listed += appendVariableGroup(sb, javaName, data.k(javaName), false);
                    listed += appendVariableGroup(sb, javaName, data.j(javaName), true);
                }
            }
        } catch (Throwable ignored) {
            // fall back to the whole maps below
        }
        if (listed == 0) {
            sb.append("  (sin variables todavia)\n");
        }
    }

    private static int appendVariableGroup(StringBuilder sb,
                                           String javaName,
                                           ArrayList<android.util.Pair<Integer, String>> pairs,
                                           boolean list) {
        if (pairs == null || pairs.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (android.util.Pair<Integer, String> pair : pairs) {
            if (pair == null || pair.second == null) {
                continue;
            }
            sb.append("  ").append(javaName).append(" -> ")
                    .append(list ? "lista " : "variable ")
                    .append(pair.second)
                    .append(" (").append(variableTypeName(pair.first, list)).append(")\n");
            count++;
        }
        return count;
    }

    private static String variableTypeName(Integer type, boolean list) {
        int value = type == null ? -1 : type;
        if (list) {
            switch (value) {
                case 1:
                    return "ListInt";
                case 3:
                    return "ListMap";
                default:
                    return "ListString";
            }
        }
        switch (value) {
            case 0:
                return "boolean";
            case 1:
                return "number(double)";
            case 3:
                return "map";
            default:
                return "String";
        }
    }

    private static void appendPermissions(StringBuilder sb, String scId) {
        sb.append("- Permisos en el AndroidManifest:\n");
        try {
            List<String> permissions = AgentProjectPermissions.list(scId);
            if (permissions.isEmpty()) {
                sb.append("  (sin permisos personalizados)\n");
                return;
            }
            for (String permission : permissions) {
                sb.append("  - ").append(permission).append('\n');
            }
        } catch (Throwable throwable) {
            sb.append("  (no se pudieron leer)\n");
        }
    }

    private static String typeName(int type) {
        switch (type) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "LinearLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_RELATIVE:
                return "RelativeLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW:
                return "HScrollView";
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
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "VScrollView";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "Switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "SeekBar";
            case ViewBean.VIEW_TYPE_WIDGET_CALENDARVIEW:
                return "CalendarView";
            case ViewBean.VIEW_TYPE_WIDGET_FAB:
                return "Fab";
            case ViewBean.VIEW_TYPE_WIDGET_ADVIEW:
                return "AdView";
            case ViewBean.VIEW_TYPE_WIDGET_MAPVIEW:
                return "MapView";
            default:
                return "type" + type;
        }
    }
}
