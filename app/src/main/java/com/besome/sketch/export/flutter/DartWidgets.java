package com.besome.sketch.export.flutter;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce un layout de Sketchware (lista de {@link ViewBean} + su raiz) a widgets Flutter.
 *
 * <p>El arbol se reconstruye con el campo {@code parent} de cada {@link ViewBean} ({@code "root"}
 * o vacio para los hijos directos de la raiz). Los {@code id} se conservan como
 * {@code ValueKey} del widget y como clave del runtime ({@code Sk.setText('id', ...)}).</p>
 *
 * <p>Los eventos ({@code onClick}, {@code onTextChanged}...) se enganchan al widget equivalente
 * ({@code onPressed}, {@code onChanged}...).</p>
 */
public class DartWidgets {

    private static final String TODO_PREFIX = "// TODO: ";

    private final Map<String, List<ViewBean>> childrenByParent = new LinkedHashMap<>();
    private final Map<String, ViewBean> viewsById = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> events;
    private final Set<String> dynamicTextIds;
    private final ArrayList<String> todos = new ArrayList<>();
    /**
     * Comentario TODO que debe emitirse inmediatamente antes del widget que se esta generando
     * (nunca dentro de una expresion: rompe la sintaxis Dart).
     */
    private String pendingComment = "";

    /**
     * @param views          todas las vistas del layout (como las devuelve {@code eC.d(xmlName)}).
     * @param events         {@code id de vista -> (nombre de evento -> cuerpo Dart)}.
     * @param dynamicTextIds ids cuyo texto se modifica con bloques {@code setText}; se muestran con
     *                       {@code Sk.bindText} para que los cambios se reflejen.
     */
    public DartWidgets(ArrayList<ViewBean> views, Map<String, Map<String, String>> events, Set<String> dynamicTextIds) {
        this.events = events;
        this.dynamicTextIds = dynamicTextIds;
        if (views != null) {
            for (ViewBean view : views) {
                viewsById.put(view.id, view);
                childrenByParent.computeIfAbsent(view.parent == null ? "root" : view.parent,
                        key -> new ArrayList<>()).add(view);
            }
        }
    }

    /**
     * @return el cuerpo del layout traducido (sin {@code Scaffold}).
     */
    public String build(String rootClassName, Map<String, String> rootAttributes) {
        List<ViewBean> rootChildren = childrenByParent.getOrDefault("root", new ArrayList<>());
        String body = buildRootWidget(rootClassName, rootAttributes, rootChildren);
        return "SafeArea(\n  child: " + indent(body, 1) + ",\n)";
    }

    /**
     * @return lineas {@code // TODO: ...} generadas (para el README).
     */
    public ArrayList<String> getTodos() {
        return todos;
    }

    // ---------------------------------------------------------------- raiz

    private String buildRootWidget(String rootClassName, Map<String, String> rootAttributes, List<ViewBean> children) {
        int orientation = orientationFromAttributes(rootAttributes, defaultOrientationFor(rootClassName));
        String body;
        String simpleName = simpleName(rootClassName);
        if (isVerticalContainer(simpleName, orientation) || isHorizontalContainer(simpleName, orientation)) {
            body = containerWidget(simpleName, orientation, children);
        } else if (isScrollView(simpleName)) {
            body = scrollWidget(simpleName, children);
        } else {
            body = stackWidget(children);
        }

        String inner = applyBox(rootAttributes, body);
        return inner;
    }

    private String applyBox(Map<String, String> attributes, String child) {
        if (attributes == null || attributes.isEmpty()) {
            return child;
        }
        Integer padding = parseDimension(attributes.get("android:padding"));
        if (padding == null) {
            return child;
        }
        return "Padding(\n  padding: EdgeInsets.all(" + padding + ".0),\n  child: " + indent(child, 1) + ",\n)";
    }

    // ---------------------------------------------------------------- contenedores

    private String containerWidget(String simpleName, int orientation, List<ViewBean> children) {
        boolean horizontal = orientation == LayoutBean.ORIENTATION_HORIZONTAL;
        String name = horizontal ? "Row" : "Column";
        StringBuilder sb = new StringBuilder(name).append("(\n");
        String childrenCode = childrenCode(children, horizontal);
        if (!childrenCode.isEmpty()) {
            sb.append("  mainAxisSize: MainAxisSize.max,\n");
            sb.append("  crossAxisAlignment: CrossAxisAlignment.start,\n");
            sb.append("  children: [\n").append(indent(childrenCode, 2)).append("\n  ],\n");
        } else {
            sb.append("  children: const [],\n");
        }
        sb.append(")");
        return sb.toString();
    }

    private String scrollWidget(String simpleName, List<ViewBean> children) {
        boolean horizontal = simpleName.toLowerCase().contains("horizontal");
        String inner = containerWidget("LinearLayout",
                horizontal ? LayoutBean.ORIENTATION_HORIZONTAL : LayoutBean.ORIENTATION_VERTICAL, children);
        return "SingleChildScrollView(\n"
                + (horizontal ? "  scrollDirection: Axis.horizontal,\n" : "")
                + "  child: " + indent(inner, 1) + ",\n)";
    }

    private String stackWidget(List<ViewBean> children) {
        String childrenCode = childrenCode(children, false);
        if (childrenCode.isEmpty()) {
            return "Stack(\n  children: [],\n)";
        }
        return "Stack(\n  children: [\n" + indent(childrenCode, 2) + "\n  ],\n)";
    }

    private String childrenCode(List<ViewBean> children, boolean horizontalParent) {
        StringBuilder sb = new StringBuilder();
        for (ViewBean child : children) {
            String code = buildView(child);
            if (code.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",\n");
            }
            sb.append(code);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- vistas

    private String buildView(ViewBean bean) {
        pendingComment = "";
        String simpleName = simpleName(bean.convert.isEmpty()
                ? bean.getClassInfo().getClassName() : bean.convert);
        String inner;
        switch (simpleName) {
            case "LinearLayout" -> inner = containerWidget(simpleName, bean.layout.orientation,
                    childrenByParent.getOrDefault(bean.id, new ArrayList<>()));
            case "RelativeLayout", "FrameLayout", "ConstraintLayout", "CoordinatorLayout" ->
                    inner = stackWidget(childrenByParent.getOrDefault(bean.id, new ArrayList<>()));
            case "ScrollView", "HorizontalScrollView" ->
                    inner = scrollWidget(simpleName, childrenByParent.getOrDefault(bean.id, new ArrayList<>()));
            case "TextView" -> inner = textWidget(bean);
            case "Button", "MaterialButton" -> inner = buttonWidget(bean);
            case "EditText" -> inner = editTextWidget(bean);
            case "ImageView", "CircleImageView" -> inner = imageWidget(bean, simpleName);
            case "CheckBox" -> inner = checkboxWidget(bean);
            case "Switch", "SwitchCompat" -> inner = switchWidget(bean);
            case "Spinner" -> inner = spinnerWidget(bean);
            case "ListView", "RecyclerView", "GridView" -> inner = listWidget(bean, simpleName);
            case "ProgressBar" -> inner = progressWidget(bean);
            case "SeekBar" -> inner = seekBarWidget(bean);
            case "include" -> inner = todo(bean, "include de layout");
            default -> inner = todo(bean, simpleName);
        }
        String result = wrapBox(bean, inner);
        if (!pendingComment.isEmpty()) {
            result = pendingComment + "\n" + result;
        }
        return result;
    }

    private String textWidget(ViewBean bean) {
        String style = textStyle(bean.text);
        String key = "key: const ValueKey('" + bean.id + "')";
        if (dynamicTextIds.contains(bean.id)) {
            return "Sk.bindText('" + bean.id + "', (context, value) => Text(\n"
                    + "  value,\n"
                    + "  " + key + ",\n"
                    + (style.isEmpty() ? "" : "  style: " + style + ",\n")
                    + "))";
        }
        String eventCode = tapEvent(bean);
        if (!eventCode.isEmpty()) {
            return "GestureDetector(\n" + indent(eventCode, 1) + "\n  child: Text(\n"
                    + "    " + literal(bean.text.text) + ",\n"
                    + "    " + key + ",\n"
                    + (style.isEmpty() ? "" : "    style: " + style + ",\n")
                    + "  ),\n)";
        }
        return "Text(\n  " + literal(bean.text.text) + ",\n  " + key + ",\n"
                + (style.isEmpty() ? "" : "  style: " + style + ",\n") + ")";
    }

    private String buttonWidget(ViewBean bean) {
        String onClick = body(bean, "onClick");
        String onLong = body(bean, "onLongClick");
        String key = "key: const ValueKey('" + bean.id + "')";
        StringBuilder sb = new StringBuilder();
        if (!onLong.isEmpty()) {
            sb.append("GestureDetector(\n  onLongPress: () {\n").append(indent(onLong, 2))
                    .append("\n  },\n  child: ");
        }
        if (dynamicTextIds.contains(bean.id)) {
            sb.append("Sk.bindText('").append(bean.id).append("', (context, value) => ElevatedButton(\n");
            sb.append("  onPressed: () {\n").append(indent(onClick, 2)).append("\n  },\n");
            sb.append("  ").append(key).append(",\n");
            sb.append("  child: Text(value),\n)).");
        } else {
            sb.append("ElevatedButton(\n");
            sb.append("  onPressed: () {\n").append(indent(onClick, 2)).append("\n  },\n");
            sb.append("  ").append(key).append(",\n");
            sb.append("  child: Text(").append(literal(bean.text.text)).append("),\n)");
        }
        if (!onLong.isEmpty()) {
            sb.append(",\n)");
        }
        return sb.toString();
    }

    private String editTextWidget(ViewBean bean) {
        StringBuilder sb = new StringBuilder("TextField(\n");
        sb.append("  controller: Sk.controller('").append(bean.id).append("'),\n");
        sb.append("  key: const ValueKey('").append(bean.id).append("'),\n");
        if (bean.text.hint != null && !bean.text.hint.isEmpty()) {
            sb.append("  decoration: InputDecoration(hintText: ").append(literal(bean.text.hint)).append("),\n");
        }
        String onChanged = body(bean, "onTextChanged");
        if (!onChanged.isEmpty()) {
            sb.append("  onChanged: (value) {\n").append(indent(onChanged, 2)).append("\n  },\n");
        }
        sb.append(")");
        return sb.toString();
    }

    private String imageWidget(ViewBean bean, String simpleName) {
        String src = bean.image == null ? "" : bean.image.resName;
        if (src == null) {
            src = "";
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return "Image.network(\n  '" + src + "',\n  key: const ValueKey('" + bean.id + "'),\n)";
        }
        pendingComment = comment(bean.id + " ImageView (" + simpleName
                + "): recurso '" + src + "' no copiado a assets");
        return "Container(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  child: const Icon(Icons.image, size: 48),\n"
                + ")";
    }

    private String checkboxWidget(ViewBean bean) {
        String onChanged = body(bean, "onCheckedChanged");
        return "Checkbox(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  value: " + (bean.checked != 0) + ",\n"
                + "  onChanged: (value) {\n" + indent(onChanged, 2) + "\n  },\n)";
    }

    private String switchWidget(ViewBean bean) {
        String onChanged = body(bean, "onCheckedChanged");
        return "Switch(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  value: " + (bean.checked != 0) + ",\n"
                + "  onChanged: (value) {\n" + indent(onChanged, 2) + "\n  },\n)";
    }

    private String spinnerWidget(ViewBean bean) {
        String event = body(bean, "onItemSelected");
        String onChanged = event.isEmpty() ? "{}" : "() {\n" + indent(event, 1) + "\n}";
        pendingComment = comment(bean.id + " Spinner: falta `spnSetData`");
        return "DropdownButton<String>(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  items: const [],\n"
                + "  onChanged: (value) " + onChanged + ",\n"
                + ")";
    }

    private String listWidget(ViewBean bean, String simpleName) {
        String event = body(bean, "onItemClick");
        if (event.isEmpty()) {
            event = body(bean, "onItemSelected");
        }
        String onTap = event.isEmpty() ? "" : "  onTap: () {\n" + indent(event, 2) + "\n  },\n";
        pendingComment = comment(bean.id + " " + simpleName + ": falta `listSetData` (adapter)");
        return "ListView(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  children: const [],\n"
                + (onTap.isEmpty() ? "" : indent(onTap, 1))
                + ")";
    }

    private String progressWidget(ViewBean bean) {
        if (bean.progressStyle != null && bean.progressStyle.contains("Horizontal")) {
            return "LinearProgressIndicator(key: const ValueKey('" + bean.id + "'), value: 0)";
        }
        return "CircularProgressIndicator(key: const ValueKey('" + bean.id + "'), value: 0)";
    }

    private String seekBarWidget(ViewBean bean) {
        String onChanged = body(bean, "onProgressChanged");
        return "Slider(\n"
                + "  key: const ValueKey('" + bean.id + "'),\n"
                + "  value: 0,\n"
                + "  min: 0,\n"
                + "  max: " + Math.max(bean.max, 1) + ".0,\n"
                + "  onChanged: (value) {\n" + indent(onChanged, 2) + "\n  },\n)";
    }

    private String todo(ViewBean bean, String detail) {
        pendingComment = comment(bean.id + " " + detail + " (widget sin equivalente Flutter)");
        return "Container(\n  key: const ValueKey('" + bean.id + "'),\n  child: const SizedBox.shrink(),\n)";
    }

    /**
     * @return linea {@code // TODO: ...} registrada para el README de la exportacion.
     */
    private String comment(String detail) {
        String line = TODO_PREFIX + detail;
        if (!todos.contains(line)) {
            todos.add(line);
        }
        return line;
    }

    // ---------------------------------------------------------------- eventos

    private String body(ViewBean bean, String eventName) {
        Map<String, String> viewEvents = events.get(bean.id);
        if (viewEvents == null) {
            return "";
        }
        String code = viewEvents.get(eventName);
        return code == null ? "" : code;
    }

    private String tapEvent(ViewBean bean) {
        String onClick = body(bean, "onClick");
        if (onClick.isEmpty()) {
            return "";
        }
        return "onTap: () {\n" + indent(onClick, 1) + "\n},";
    }

    // ---------------------------------------------------------------- estilo

    private String textStyle(TextBean text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("TextStyle(");
        boolean first = true;
        if (text.textSize > 0) {
            sb.append("fontSize: ").append(text.textSize).append(".0");
            first = false;
        }
        if (text.textColor != 0 && text.textColor != 0xffffff) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("color: ").append(color(text.textColor));
            first = false;
        }
        if (text.textType == TextBean.TEXT_TYPE_BOLD || text.textType == TextBean.TEXT_TYPE_BOLDITALIC) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("fontWeight: FontWeight.bold");
            first = false;
        }
        if (text.textType == TextBean.TEXT_TYPE_ITALIC || text.textType == TextBean.TEXT_TYPE_BOLDITALIC) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("fontStyle: FontStyle.italic");
            first = false;
        }
        sb.append(")");
        if (first) {
            return "";
        }
        return sb.toString();
    }

    private String color(int argb) {
        if ((argb >> 24 & 0xff) == 0) {
            // Sketchware guarda 0xRRGGBB: se interpreta como opaco.
            argb = 0xff000000 | (argb & 0xffffff);
        }
        return String.format("Color(0x%08X)", argb);
    }

    // ---------------------------------------------------------------- cajas

    private String wrapBox(ViewBean bean, String inner) {
        LayoutBean layout = bean.layout;
        String result = inner;
        if (layout == null) {
            return result;
        }
        if (hasPadding(layout)) {
            result = "Padding(\n  padding: EdgeInsets.only("
                    + "left: " + layout.paddingLeft + ".0, "
                    + "top: " + layout.paddingTop + ".0, "
                    + "right: " + layout.paddingRight + ".0, "
                    + "bottom: " + layout.paddingBottom + ".0),\n  child: " + indent(result, 1) + ",\n)";
        }
        if (layout.backgroundColor != 0 && layout.backgroundColor != 0xffffff) {
            result = "Container(\n  color: " + color(layout.backgroundColor) + ",\n  child: " + indent(result, 1) + ",\n)";
        }
        if (layout.width != LayoutBean.LAYOUT_WRAP_CONTENT || layout.height != LayoutBean.LAYOUT_WRAP_CONTENT) {
            result = "SizedBox(\n"
                    + (layout.width != LayoutBean.LAYOUT_WRAP_CONTENT ? "  width: " + dimension(layout.width) + ",\n" : "")
                    + (layout.height != LayoutBean.LAYOUT_WRAP_CONTENT ? "  height: " + dimension(layout.height) + ",\n" : "")
                    + "  child: " + indent(result, 1) + ",\n)";
        }
        if (hasMargin(layout)) {
            result = "Padding(\n  padding: EdgeInsets.only("
                    + "left: " + layout.marginLeft + ".0, "
                    + "top: " + layout.marginTop + ".0, "
                    + "right: " + layout.marginRight + ".0, "
                    + "bottom: " + layout.marginBottom + ".0),\n  child: " + indent(result, 1) + ",\n)";
        }
        String alignment = alignment(layout.layoutGravity != 0 ? layout.layoutGravity : layout.gravity);
        if (alignment != null) {
            result = "Align(\n  alignment: " + alignment + ",\n  child: " + indent(result, 1) + ",\n)";
        }
        if (layout.weight > 0) {
            result = "Expanded(\n  flex: " + layout.weight + ",\n  child: " + indent(result, 1) + ",\n)";
        }
        return result;
    }

    private boolean hasPadding(LayoutBean layout) {
        return layout.paddingLeft != 0 || layout.paddingTop != 0
                || layout.paddingRight != 0 || layout.paddingBottom != 0;
    }

    private boolean hasMargin(LayoutBean layout) {
        return layout.marginLeft != 0 || layout.marginTop != 0
                || layout.marginRight != 0 || layout.marginBottom != 0;
    }

    /**
     * @return la gravedad de Android traducida a un {@code Alignment} de Flutter.
     */
    private String alignment(int gravity) {
        int horizontal = gravity & 0x07;
        int vertical = gravity & 0x70;
        Double x = null;
        Double y = null;
        if (horizontal == 3) {
            x = -1.0;
        } else if (horizontal == 5) {
            x = 1.0;
        } else if (horizontal == 1) {
            x = 0.0;
        }
        if (vertical == 48) {
            y = -1.0;
        } else if (vertical == 80) {
            y = 1.0;
        } else if (vertical == 16) {
            y = 0.0;
        }
        if (x == null && y == null) {
            return null;
        }
        return "Alignment(" + (x == null ? 0 : x) + ", " + (y == null ? 0 : y) + ")";
    }

    // ---------------------------------------------------------------- utilidades

    private static String simpleName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        String trimmed = className.replace(' ', '$');
        int dot = trimmed.lastIndexOf('.');
        return dot < 0 ? trimmed : trimmed.substring(dot + 1);
    }

    private static boolean isVerticalContainer(String simpleName, int orientation) {
        return "LinearLayout".equals(simpleName) && orientation != LayoutBean.ORIENTATION_HORIZONTAL;
    }

    private static boolean isHorizontalContainer(String simpleName, int orientation) {
        return "LinearLayout".equals(simpleName) && orientation == LayoutBean.ORIENTATION_HORIZONTAL;
    }

    private static boolean isScrollView(String simpleName) {
        return "ScrollView".equals(simpleName) || "HorizontalScrollView".equals(simpleName);
    }

    private static int defaultOrientationFor(String rootClassName) {
        return LayoutBean.ORIENTATION_VERTICAL;
    }

    private static int orientationFromAttributes(Map<String, String> attributes, int fallback) {
        if (attributes == null) {
            return fallback;
        }
        String value = attributes.get("android:orientation");
        if ("horizontal".equals(value)) {
            return LayoutBean.ORIENTATION_HORIZONTAL;
        }
        if ("vertical".equals(value)) {
            return LayoutBean.ORIENTATION_VERTICAL;
        }
        return fallback;
    }

    private static Integer parseDimension(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.endsWith("dp")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        try {
            return (int) Float.parseFloat(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String dimension(int value) {
        if (value == LayoutBean.LAYOUT_MATCH_PARENT) {
            return "double.infinity";
        }
        return value + ".0";
    }

    private static String literal(String value) {
        if (value == null) {
            value = "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '$' -> sb.append("\\$");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    /**
     * Indenta cada linea de {@code code} con {@code levels} niveles de 2 espacios.
     */
    static String indent(String code, int levels) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String prefix = "  ".repeat(levels);
        StringBuilder sb = new StringBuilder();
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (!lines[i].isEmpty()) {
                sb.append(prefix).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
