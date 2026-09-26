package com.besome.sketch.export.flutter;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce bloques ({@link BlockBean}) a Dart.
 *
 * <p>Espejo en Dart de {@code a.a.a.Fx} (el generador Java de bloques de Sketchware Pro): mismo
 * recorrido (lista de bloques + {@code nextBlock} + {@code subStack1}/{@code subStack2}), mismos
 * parametros ({@code @id} para bloques anidados, tipos {@code %b}/{@code %d}/{@code %s}/{@code %m})
 * pero emitiendo codigo Dart que se apoya en {@code lib/runtime/sk.dart}.</p>
 *
 * <p>Fase 1: variables, control de flujo, operadores/comparaciones, texto/matematicas, Toast,
 * Intent/startActivity, listeners de boton, setText/getText y finish(). Todo lo demas se emite como
 * {@code // TODO: <opcode> <parametros>} para no perder informacion.</p>
 */
public class DartBlocks {

    /**
     * Prefijo de los TODO para poder resumirlos despues.
     */
    public static final String TODO_PREFIX = "// TODO: ";

    private static final Pattern PARAM_PATTERN = Pattern.compile("%m(?!\\.[\\w]+)");
    private static final Pattern PARAM_TYPE_PATTERN = Pattern.compile("%\\w+(?:\\.\\w+)?|%\\w");

    private final ArrayList<BlockBean> eventBlocks;
    /**
     * Variables de Intent -> nombre de la Activity a la que apuntan (bloques {@code intentSetScreen}).
     */
    private final Map<String, String> intentScreens;
    /**
     * Registro de componentes (Fase 3): los opcodes de componentes Android marcan que plugin de
     * pub.dev hay que anadir al {@code pubspec.yaml} generado.
     */
    private final DartComponents components;
    /**
     * Cadenas del proyecto (Fase 3): los bloques {@code getResStr}/{@code getResString} registran
     * aqui las claves que acaban en {@code lib/strings.dart}.
     */
    private final FlutterStrings strings;
    private final ArrayList<String> todoLines = new ArrayList<>();

    private Map<String, BlockBean> blockMap = new HashMap<>();
    private String moreBlock = "";
    private String parentOperator = "";

    public DartBlocks(ArrayList<BlockBean> eventBlocks, Map<String, String> intentScreens) {
        this(eventBlocks, intentScreens, null, null);
    }

    public DartBlocks(ArrayList<BlockBean> eventBlocks, Map<String, String> intentScreens,
                      DartComponents components, FlutterStrings strings) {
        this.eventBlocks = eventBlocks;
        this.intentScreens = intentScreens;
        this.components = components;
        this.strings = strings;
    }

    /**
     * @param androidCodeCode {@code true} si se quiere el codigo Dart Dart de todos los bloques.
     * @return codigo Dart del primer bloque + su cadena ({@code nextBlock}).
     */
    public String generate() {
        blockMap = new HashMap<>();
        if (eventBlocks == null || eventBlocks.isEmpty()) {
            return "";
        }
        for (BlockBean bean : eventBlocks) {
            blockMap.put(bean.id, bean);
        }
        return generateBlock(eventBlocks.get(0), "");
    }

    /**
     * @return lineas {@code // TODO: ...} que se han emitido (para el README).
     */
    public ArrayList<String> getTodoLines() {
        return todoLines;
    }

    // ---------------------------------------------------------------- recorrido

    public final String generateBlock(BlockBean bean, String parentOp) {
        ArrayList<String> params = getBlockParams(bean);
        String code = getBlockCode(bean, params);
        if (isOperator(bean.opCode) && isOperator(parentOp)) {
            code = "(" + code + ")";
        }
        if (bean.nextBlock >= 0) {
            String next = generateNext(String.valueOf(bean.nextBlock), moreBlock);
            if (!next.isEmpty()) {
                code += (code.isEmpty() ? "" : "\n") + next;
            }
        }
        return code;
    }

    private String generateNext(String blockId, String parentOp) {
        BlockBean bean = blockMap.get(blockId);
        if (bean == null) {
            return "";
        }
        String previous = parentOperator;
        parentOperator = parentOp;
        String code = generateBlock(bean, parentOp);
        parentOperator = previous;
        return code;
    }

    private String generateSubStack(int blockId) {
        if (blockId < 0) {
            return "";
        }
        return generateNext(String.valueOf(blockId), "");
    }

    private boolean isOperator(String opCode) {
        return Arrays.asList("repeat", "+", "-", "*", "/", "%", ">", "=", "<", "&&", "||", "not")
                .contains(opCode);
    }

    // ---------------------------------------------------------------- parametros

    public ArrayList<String> getBlockParams(BlockBean bean) {
        ArrayList<String> params = new ArrayList<>();
        ArrayList<String> paramsTypes = extractParamsTypes(bean.spec);
        for (int i = 0; i < bean.parameters.size(); i++) {
            String paramType = i < paramsTypes.size() ? paramsTypes.get(i) : "";
            String param = getParamValue(bean.parameters.get(i), paramType);
            params.add(formatParam(param, getBlockType(bean, i)));
        }
        return params;
    }

    /**
     * Resuelve un parametro a una expresion Dart (un literal, una referencia a variable o el
     * codigo de un bloque anidado).
     */
    private String formatParam(String param, int type) {
        if (param == null) {
            param = "";
        }
        if (!param.isEmpty() && param.charAt(0) == '@') {
            String nested = generateNext(param.substring(1), "");
            if (type == 2 && nested.isEmpty()) {
                return "\"\"";
            }
            return nested;
        }
        switch (type) {
            case 2:
                return "\"" + escapeString(param) + "\"";
            case 1:
                if (param.isEmpty()) {
                    return "0";
                }
                return param;
            case 0:
                if (param.isEmpty()) {
                    return "true";
                }
                if (!param.equals("true") && !param.equals("false")) {
                    return "true";
                }
                return param;
            default:
                return param;
        }
    }

    private String getParamValue(String param, String paramType) {
        if (param == null) {
            return "";
        }
        // En Fase 1 no hay ViewBinding: los parametros de vista se usan como ids ("button1").
        // Los colores de recursos (@color/... -> R.color.x) no tienen equivalente: se dejan
        // tal cual y el emisor del bloque decide (normalmente TODO).
        return param;
    }

    private ArrayList<String> extractParamsTypes(String spec) {
        ArrayList<String> matches = new ArrayList<>();
        Matcher matcher = PARAM_TYPE_PATTERN.matcher(spec);
        while (matcher.find()) {
            matches.add(matcher.group().toLowerCase());
        }
        return matches;
    }

    private int getBlockType(BlockBean bean, int parameterIndex) {
        List<?> paramClassInfo = bean.getParamClassInfo();
        if (paramClassInfo == null || parameterIndex >= paramClassInfo.size()) {
            return 3;
        }
        Object info = paramClassInfo.get(parameterIndex);
        if (!(info instanceof a.a.a.Gx gx)) {
            return 3;
        }
        if (gx.b("boolean")) {
            return 0;
        }
        if (gx.b("double")) {
            return 1;
        }
        if (gx.b("String")) {
            return 2;
        }
        return 3;
    }

    /**
     * @return el parametro {@code index} ya formateado como literal Dart, o {@code ""}.
     */
    private String param(ArrayList<String> params, int index) {
        return index < params.size() ? params.get(index) : "";
    }

    /**
     * @return un id de vista/componente listo para pasarse al runtime Dart ({@code Sk.xxx('id')}).
     * Si el parametro venia ya formateado como literal de texto ({@code "id"}), se le quitan las
     * comillas para no duplicarlas.
     */
    private String widget(String id) {
        String value = id == null ? "" : id;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return "\"" + escapeString(value) + "\"";
    }

    private boolean hasEmptySelectorParam(ArrayList<String> params, String spec) {
        Matcher matcher = PARAM_PATTERN.matcher(spec);
        if (matcher.find()) {
            return false;
        }
        Matcher paramMatcher = Pattern.compile("%[bdsm]").matcher(spec);
        int count = 0;
        ArrayList<Integer> selectorParamPositions = new ArrayList<>();
        while (paramMatcher.find()) {
            if ("%m".equals(paramMatcher.group())) {
                selectorParamPositions.add(count);
            }
            count++;
        }
        for (int position : selectorParamPositions) {
            if (position >= params.size()) {
                continue;
            }
            String param = params.get(position);
            if (param == null || param.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String escapeString(String input) {
        StringBuilder escaped = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '$' -> escaped.append("\\$");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    /**
     * Registra un TODO (una sola vez por linea) para el resumen del README y devuelve codigo Dart
     * seguro: el comentario {@code // TODO: <opcode> <parametros>} seguido de una llamada a
     * {@code Sk.todo(...)}. Asi el bloque no soportado funciona tanto en posicion de sentencia
     * como dentro de una expresion (un comentario solo romperia la expresion).
     */
    private String todo(BlockBean bean, ArrayList<String> params) {
        StringBuilder comment = new StringBuilder(TODO_PREFIX).append(bean.opCode);
        StringBuilder args = new StringBuilder();
        for (String param : params) {
            comment.append(' ').append(param);
            if (args.length() > 0) {
                args.append(", ");
            }
            args.append(param);
        }
        String line = comment.toString();
        if (!todoLines.contains(line)) {
            todoLines.add(line);
        }
        return line + "\nSk.todo(\"" + escapeString(bean.opCode) + "\", [" + args + "])";
    }

    // ---------------------------------------------------------------- emisores

    private String getBlockCode(BlockBean bean, ArrayList<String> params) {
        // Fase 3: cualquier opcode de componente marca su plugin para el pubspec generado.
        if (components != null) {
            components.useForOpcode(bean.opCode);
        }
        StringBuilder code = new StringBuilder();
        switch (bean.opCode) {
            // --- variables ---
            case "getVar": {
                String variableName = bean.spec;
                if (variableName == null || variableName.isEmpty()
                        || variableName.indexOf('%') >= 0 || variableName.indexOf(' ') >= 0) {
                    /* Algunos bloques guardan el nombre de la variable en el primer parametro. */
                    if (!bean.parameters.isEmpty() && bean.parameters.get(0) != null
                            && !bean.parameters.get(0).isEmpty()) {
                        variableName = bean.parameters.get(0);
                    }
                }
                code.append("Sk.getVar(").append(widget(variableName)).append(")");
                break;
            }
            case "setVarBoolean":
            case "setVarInt":
            case "setVarString":
                code.append("Sk.setVar(").append(widget(param(params, 0)))
                        .append(", ").append(param(params, 1)).append(");");
                break;
            case "increaseInt":
                code.append("Sk.setVar(").append(widget(param(params, 0)))
                        .append(", Sk.add(Sk.getVar(").append(widget(param(params, 0)))
                        .append("), 1));");
                break;
            case "decreaseInt":
                code.append("Sk.setVar(").append(widget(param(params, 0)))
                        .append(", Sk.sub(Sk.getVar(").append(widget(param(params, 0)))
                        .append("), 1));");
                break;

            // --- control de flujo ---
            case "if":
                code.append("if (Sk.toBool(").append(param(params, 0)).append(")) {\n")
                        .append(indent(generateSubStack(bean.subStack1), 1)).append("\n}");
                break;
            case "ifElse":
                code.append("if (Sk.toBool(").append(param(params, 0)).append(")) {\n")
                        .append(indent(generateSubStack(bean.subStack1), 1)).append("\n} else {\n")
                        .append(indent(generateSubStack(bean.subStack2), 1)).append("\n}");
                break;
            case "repeat": {
                String loopVar = "_repeat" + sanitizeIdentifier(bean.id);
                code.append("for (int ").append(loopVar).append(" = 0; ")
                        .append(loopVar).append(" < Sk.toNumber(").append(param(params, 0))
                        .append(").toInt(); ").append(loopVar).append("++) {\n")
                        .append(indent(generateSubStack(bean.subStack1), 1)).append("\n}");
                break;
            }
            case "forever":
                code.append("while (true) {\n").append(indent(generateSubStack(bean.subStack1), 1))
                        .append("\n}");
                break;
            case "break":
                code.append("break;");
                break;

            // --- booleanos y operadores ---
            case "true", "false":
                code.append(bean.opCode);
                break;
            case "not":
                code.append("!Sk.toBool(").append(param(params, 0)).append(")");
                break;
            case "+":
                code.append("Sk.add(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "-":
                code.append("Sk.sub(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "*":
                code.append("Sk.mul(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "/":
                code.append("Sk.div(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "%":
                code.append("Sk.mod(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case ">":
                code.append("Sk.gt(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "<":
                code.append("Sk.lt(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "=":
                code.append("Sk.eq(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "&&":
                code.append("Sk.and(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "||":
                code.append("Sk.or(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;

            // --- cadenas ---
            case "stringLength":
                code.append("Sk.toText(").append(param(params, 0)).append(").length");
                break;
            case "stringJoin":
                code.append("Sk.add(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "stringIndex":
                code.append("Sk.toText(").append(param(params, 1)).append(").indexOf(Sk.toText(")
                        .append(param(params, 0)).append("))");
                break;
            case "stringLastIndex":
                code.append("Sk.toText(").append(param(params, 1)).append(").lastIndexOf(Sk.toText(")
                        .append(param(params, 0)).append("))");
                break;
            case "stringSub":
                code.append("Sk.toText(").append(param(params, 0)).append(").substring(")
                        .append("Sk.toNumber(").append(param(params, 1)).append(").toInt(), ")
                        .append("Sk.toNumber(").append(param(params, 2)).append(").toInt())");
                break;
            case "stringEquals":
                code.append("Sk.eq(").append(param(params, 0)).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "stringContains":
                code.append("Sk.toText(").append(param(params, 0)).append(").contains(Sk.toText(")
                        .append(param(params, 1)).append("))");
                break;
            case "stringReplace":
                code.append("Sk.toText(").append(param(params, 0)).append(").replaceAll(Sk.toText(")
                        .append(param(params, 1)).append("), Sk.toText(").append(param(params, 2))
                        .append("))");
                break;
            case "stringReplaceFirst":
                code.append("Sk.toText(").append(param(params, 0))
                        .append(").replaceFirst(Sk.toText(").append(param(params, 1))
                        .append("), Sk.toText(").append(param(params, 2)).append("))");
                break;
            case "stringReplaceAll":
                code.append("Sk.toText(").append(param(params, 0)).append(").replaceAll(Sk.toText(")
                        .append(param(params, 1)).append("), Sk.toText(").append(param(params, 2))
                        .append("))");
                break;
            case "trim":
                code.append("Sk.toText(").append(param(params, 0)).append(").trim()");
                break;
            case "toUpperCase":
                code.append("Sk.toText(").append(param(params, 0)).append(").toUpperCase()");
                break;
            case "toLowerCase":
                code.append("Sk.toText(").append(param(params, 0)).append(").toLowerCase()");
                break;
            case "toNumber":
                code.append("Sk.toNumber(").append(param(params, 0)).append(")");
                break;
            case "toString":
                code.append("Sk.toText(Sk.toNumber(").append(param(params, 0)).append(").toInt())");
                break;
            case "toStringWithDecimal":
                code.append("Sk.toText(Sk.toNumber(").append(param(params, 0)).append("))");
                break;

            // --- matematicas ---
            case "random":
                code.append("Sk.random(Sk.toNumber(").append(param(params, 0)).append("), Sk.toNumber(")
                        .append(param(params, 1)).append("))");
                break;
            case "mathGetDip":
                code.append("Sk.getDip(context, Sk.toNumber(").append(param(params, 0)).append("))");
                break;
            case "mathGetDisplayWidth":
                code.append("Sk.getDisplayWidth(context)");
                break;
            case "mathGetDisplayHeight":
                code.append("Sk.getDisplayHeight(context)");
                break;
            case "mathPi":
                code.append("Sk.pi");
                break;
            case "mathE":
                code.append("Sk.e");
                break;
            case "mathPow":
                code.append(mathCall("pow", params, 2));
                break;
            case "mathMin":
                code.append(mathCall("min", params, 2));
                break;
            case "mathMax":
                code.append(mathCall("max", params, 2));
                break;
            case "mathSqrt":
                code.append(mathCall("sqrt", params, 1));
                break;
            case "mathAbs":
                code.append(mathCall("abs", params, 1));
                break;
            case "mathRound":
                code.append(mathCall("round", params, 1));
                break;
            case "mathCeil":
                code.append(mathCall("ceil", params, 1));
                break;
            case "mathFloor":
                code.append(mathCall("floor", params, 1));
                break;
            case "mathSin":
                code.append(mathCall("sin", params, 1));
                break;
            case "mathCos":
                code.append(mathCall("cos", params, 1));
                break;
            case "mathTan":
                code.append(mathCall("tan", params, 1));
                break;
            case "mathAsin":
                code.append(mathCall("asin", params, 1));
                break;
            case "mathAcos":
                code.append(mathCall("acos", params, 1));
                break;
            case "mathAtan":
                code.append(mathCall("atan", params, 1));
                break;
            case "mathExp":
                code.append(mathCall("exp", params, 1));
                break;
            case "mathLog":
                code.append(mathCall("log", params, 1));
                break;
            case "mathLog10":
                code.append(mathCall("log10", params, 1));
                break;
            case "mathToRadian":
                code.append(mathCall("toRadian", params, 1));
                break;
            case "mathToDegree":
                code.append(mathCall("toDegree", params, 1));
                break;

            // --- vistas: texto ---
            case "setText":
                code.append("Sk.setText(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "getText":
                code.append("Sk.getText(").append(widget(param(params, 0))).append(")");
                break;
            case "viewOnClick":
                // Dentro de un evento `onClick` el listener ya lo genera la pantalla
                // (`onPressed` del boton); aqui solo se emite el cuerpo del listener.
                code.append("{\n").append(indent(generateSubStack(bean.subStack1), 1)).append("\n}");
                break;

            // --- Toast / navegacion ---
            case "doToast":
                code.append("Sk.toast(context, ").append(param(params, 0)).append(");");
                break;
            case "finishActivity":
                code.append("Sk.finish(context);");
                break;
            case "intentSetScreen":
                // Se registra en `intentScreens` (pre-escaneo) para poder emitir el
                // Navigator.push cuando llegue el `startActivity`.
                code.append("// intent de ").append(param(params, 0)).append(" -> ")
                        .append(param(params, 1));
                break;
            case "startActivity": {
                String intentVariable = param(params, 0);
                String screen = resolveScreen(intentVariable);
                if (screen != null) {
                    code.append("Sk.go(context, const ").append(screen).append("());");
                } else {
                    code.append(todo(bean, params));
                }
                break;
            }

            // --- listas (Fase 2) ---
            case "addListInt":
                code.append("Sk.list(").append(widget(param(params, 1))).append(").add(Sk.toNumber(")
                        .append(param(params, 0)).append("));");
                break;
            case "insertListInt":
                code.append("Sk.list(").append(widget(param(params, 2))).append(").insert(Sk.toNumber(")
                        .append(param(params, 1)).append(").toInt(), Sk.toNumber(")
                        .append(param(params, 0)).append("));");
                break;
            case "getAtListInt":
                code.append("Sk.toNumber(Sk.list(").append(widget(param(params, 1))).append(")[Sk.toNumber(")
                        .append(param(params, 0)).append(").toInt()])");
                break;
            case "indexListInt":
                code.append("Sk.list(").append(widget(param(params, 1))).append(").indexOf(Sk.toNumber(")
                        .append(param(params, 0)).append("))");
                break;
            case "containListInt":
                code.append("Sk.list(").append(widget(param(params, 0))).append(").contains(Sk.toNumber(")
                        .append(param(params, 1)).append("))");
                break;
            case "addListStr":
            case "addMapToList":
                code.append("Sk.list(").append(widget(param(params, 1))).append(").add(")
                        .append(param(params, 0)).append(");");
                break;
            case "insertListStr":
                code.append("Sk.list(").append(widget(param(params, 2))).append(").insert(Sk.toNumber(")
                        .append(param(params, 1)).append(").toInt(), ")
                        .append(param(params, 0)).append(");");
                break;
            case "getAtListStr":
                code.append("Sk.list(").append(widget(param(params, 1))).append(")[Sk.toNumber(")
                        .append(param(params, 0)).append(").toInt()]");
                break;
            case "indexListStr":
                code.append("Sk.list(").append(widget(param(params, 1))).append(").indexOf(")
                        .append(param(params, 0)).append(")");
                break;
            case "containListStr":
                code.append("Sk.list(").append(widget(param(params, 0))).append(").contains(")
                        .append(param(params, 1)).append(")");
                break;
            case "deleteList":
                code.append("Sk.list(").append(widget(param(params, 1))).append(").removeAt(Sk.toNumber(")
                        .append(param(params, 0)).append(").toInt());");
                break;
            case "lengthList":
                code.append("Sk.list(").append(widget(param(params, 0))).append(").length");
                break;
            case "clearList":
                code.append("Sk.list(").append(widget(param(params, 0))).append(").clear();");
                break;
            case "addListMap":
                code.append("{\n")
                        .append("  final Map<String, dynamic> _item = <String, dynamic>{};\n")
                        .append("  _item[Sk.toText(").append(param(params, 0)).append(")] = ")
                        .append(param(params, 1)).append(";\n")
                        .append("  Sk.list(").append(widget(param(params, 2))).append(").add(_item);\n")
                        .append("}");
                break;
            case "insertListMap":
                code.append("{\n")
                        .append("  final Map<String, dynamic> _item = <String, dynamic>{};\n")
                        .append("  _item[Sk.toText(").append(param(params, 0)).append(")] = ")
                        .append(param(params, 1)).append(";\n")
                        .append("  Sk.list(").append(widget(param(params, 3))).append(").insert(Sk.toNumber(")
                        .append(param(params, 2)).append(").toInt(), _item);\n")
                        .append("}");
                break;
            case "getAtListMap":
                code.append("Sk.toText((Sk.list(").append(widget(param(params, 2)))
                        .append(")[Sk.toNumber(").append(param(params, 0))
                        .append(").toInt()] as Map)[Sk.toText(").append(param(params, 1))
                        .append(")])");
                break;
            case "setListMap":
                code.append("(Sk.list(").append(widget(param(params, 3)))
                        .append(")[Sk.toNumber(").append(param(params, 2))
                        .append(").toInt()] as Map)[Sk.toText(").append(param(params, 0))
                        .append(")] = ").append(param(params, 1)).append(";");
                break;
            case "containListMap":
                code.append("(Sk.list(").append(widget(param(params, 0)))
                        .append(")[Sk.toNumber(").append(param(params, 1))
                        .append(").toInt()] as Map).containsKey(Sk.toText(")
                        .append(param(params, 2)).append("))");
                break;
            case "insertMapToList":
                code.append("Sk.list(").append(widget(param(params, 2))).append(").insert(Sk.toNumber(")
                        .append(param(params, 1)).append(").toInt(), ")
                        .append(param(params, 0)).append(");");
                break;
            case "getMapInList":
                code.append("Sk.setVar(").append(widget(param(params, 2))).append(", Sk.list(")
                        .append(widget(param(params, 1))).append(")[Sk.toNumber(")
                        .append(param(params, 0)).append(").toInt()]);");
                break;

            // --- mapas (Fase 2) ---
            case "mapCreateNew":
                code.append("Sk.setVar(").append(widget(param(params, 0)))
                        .append(", <String, dynamic>{});");
                break;
            case "mapPut":
                code.append("Sk.map(").append(widget(param(params, 0))).append(")[Sk.toText(")
                        .append(param(params, 1)).append(")] = ").append(param(params, 2)).append(";");
                break;
            case "mapGet":
                code.append("Sk.toText(Sk.map(").append(widget(param(params, 0)))
                        .append(")[Sk.toText(").append(param(params, 1)).append(")])");
                break;
            case "mapContainKey":
                code.append("Sk.map(").append(widget(param(params, 0)))
                        .append(").containsKey(Sk.toText(").append(param(params, 1)).append("))");
                break;
            case "mapRemoveKey":
                code.append("Sk.map(").append(widget(param(params, 0))).append(").remove(Sk.toText(")
                        .append(param(params, 1)).append("));");
                break;
            case "mapSize":
                code.append("Sk.map(").append(widget(param(params, 0))).append(").length");
                break;
            case "mapIsEmpty":
                code.append("Sk.map(").append(widget(param(params, 0))).append(").isEmpty");
                break;
            case "mapClear":
                code.append("Sk.map(").append(widget(param(params, 0))).append(").clear();");
                break;
            case "mapGetAllKeys":
                code.append("Sk.setKeys(").append(widget(param(params, 1))).append(", Sk.map(")
                        .append(widget(param(params, 0))).append("));");
                break;

            // --- JSON <-> mapas/listas (Fase 2) ---
            case "strToMap":
                code.append("Sk.setVar(").append(widget(param(params, 1))).append(", Sk.jsonToMap(")
                        .append(param(params, 0)).append("));");
                break;
            case "strToListMap":
                code.append("Sk.setVar(").append(widget(param(params, 1))).append(", Sk.jsonToListMap(")
                        .append(param(params, 0)).append("));");
                break;
            case "mapToStr":
            case "listMapToStr":
                code.append("Sk.jsonEncode(").append(param(params, 0)).append(")");
                break;

            // --- persistencia: SharedPreferences (Fase 2) ---
            case "fileSetFileName":
                code.append("Sk.setPrefFileName(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "fileGetData":
                code.append("Sk.getPrefString(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(")");
                break;
            case "fileSetData":
                code.append("Sk.setPrefString(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(", ").append(param(params, 2)).append(");");
                break;
            case "fileRemoveData":
                code.append("Sk.removePref(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;

            // --- timers (Fase 2) ---
            case "timerAfter":
                code.append("Sk.timerAfter(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(", () {\n")
                        .append(indent(generateSubStack(bean.subStack1), 1)).append("\n});");
                break;
            case "timerEvery":
                code.append("Sk.timerEvery(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(", ").append(param(params, 2))
                        .append(", () {\n")
                        .append(indent(generateSubStack(bean.subStack1), 1)).append("\n});");
                break;
            case "timerCancel":
                code.append("Sk.timerCancel(").append(widget(param(params, 0))).append(");");
                break;

            // --- dialogos (Fase 2) ---
            case "dialogSetTitle":
                code.append("Sk.dialogSetTitle(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "dialogSetMessage":
                code.append("Sk.dialogSetMessage(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "dialogShow":
                code.append("Sk.dialogShow(context, ").append(widget(param(params, 0))).append(");");
                break;
            case "dialogDismiss":
                code.append("Sk.dialogDismiss(context);");
                break;
            case "dialogOkButton":
                code.append(dialogButton(param(params, 0), "okText", "onOk", param(params, 1),
                        generateSubStack(bean.subStack1)));
                break;
            case "dialogCancelButton":
                code.append(dialogButton(param(params, 0), "cancelText", "onCancel", param(params, 1),
                        generateSubStack(bean.subStack1)));
                break;
            case "dialogNeutralButton":
                code.append(dialogButton(param(params, 0), "neutralText", "onNeutral", param(params, 1),
                        generateSubStack(bean.subStack1)));
                break;

            // --- cadenas/fecha extra (Fase 2) ---
            case "toStringFormat":
                code.append("Sk.decimalFormat(").append(param(params, 1)).append(", ")
                        .append(param(params, 0)).append(")");
                break;
            case "currentTime":
                code.append("DateTime.now().millisecondsSinceEpoch");
                break;

            // --- ListView/Spinner con datos (Fase 2) ---
            case "listSetData":
                code.append("Sk.setListData(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "listRefresh":
                code.append("Sk.refreshList(").append(widget(param(params, 0))).append(");");
                break;
            case "listSetItemChecked":
                code.append("Sk.setListItemChecked(").append(widget(param(params, 0)))
                        .append(", Sk.toNumber(").append(param(params, 1))
                        .append(").toInt(), Sk.toBool(").append(param(params, 2)).append("));");
                break;
            case "listGetCheckedPosition":
                code.append("Sk.getCheckedPosition(").append(widget(param(params, 0))).append(")");
                break;
            case "listGetCheckedPositions":
                code.append("Sk.setVar(").append(widget(param(params, 1)))
                        .append(", Sk.getCheckedPositions(").append(widget(param(params, 0)))
                        .append("));");
                break;
            case "listGetCheckedCount":
                code.append("Sk.getCheckedCount(").append(widget(param(params, 0))).append(")");
                break;
            case "spnSetData":
                code.append("Sk.setSpinnerData(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "spnRefresh":
                code.append("Sk.refreshSpinner(").append(widget(param(params, 0))).append(");");
                break;
            case "spnSetSelection":
                code.append("Sk.setSpinnerIndex(").append(widget(param(params, 0)))
                        .append(", Sk.toNumber(").append(param(params, 1)).append(").toInt());");
                break;
            case "spnGetSelection":
                code.append("Sk.getSpinnerIndex(").append(widget(param(params, 0))).append(")");
                break;

            // ================================================================
            // Fase 3: componentes Android -> plugins Flutter (open source)
            // ================================================================

            // --- WebView -> webview_flutter ---
            case "webViewLoadUrl":
                code.append("SkCWebView.loadUrl(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "webViewGoBack":
                code.append("SkCWebView.goBack(").append(widget(param(params, 0))).append(");");
                break;
            case "webViewGoForward":
                code.append("SkCWebView.goForward(").append(widget(param(params, 0))).append(");");
                break;
            case "webViewClearCache":
                code.append("SkCWebView.clearCache(").append(widget(param(params, 0))).append(");");
                break;
            case "webViewGetUrl":
            case "webViewCanGoBack":
            case "webViewCanGoForward":
            case "webViewSetCacheMode":
            case "webViewClearHistory":
            case "webViewStopLoading":
            case "webViewZoomIn":
            case "webViewZoomOut":
                // En Flutter estas operaciones son asincronas o no existen: TODO compilable.
                code.append(todo(bean, params));
                break;

            // --- Camara -> camera ---
            case "camerastarttakepicture":
                code.append("SkCCamera.takePicture(").append(widget(param(params, 0)))
                        .append(", '');");
                break;

            // --- Galeria / selector de ficheros -> image_picker ---
            case "filepickerstartpickfiles":
                code.append("SkCPicker.pickFiles(").append(widget(param(params, 0)))
                        .append(", '');");
                break;

            // --- Audio -> audioplayers ---
            case "mediaplayerCreate":
                code.append("SkCAudio.create(").append(widget(param(params, 0))).append(", ")
                        .append(widget(param(params, 1))).append(");");
                break;
            case "mediaplayerStart":
                code.append("SkCAudio.start(").append(widget(param(params, 0))).append(");");
                break;
            case "mediaplayerPause":
                code.append("SkCAudio.pause(").append(widget(param(params, 0))).append(");");
                break;
            case "mediaplayerSeek":
                code.append("SkCAudio.seek(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "mediaplayerReset":
                code.append("SkCAudio.reset(").append(widget(param(params, 0))).append(");");
                break;
            case "mediaplayerRelease":
                code.append("SkCAudio.release(").append(widget(param(params, 0))).append(");");
                break;
            case "mediaplayerSetLooping":
                code.append("SkCAudio.setLooping(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "mediaplayerGetCurrent":
            case "mediaplayerGetDuration":
            case "mediaplayerIsPlaying":
            case "mediaplayerIsLooping":
            case "soundpoolCreate":
            case "soundpoolLoad":
            case "soundpoolStreamPlay":
            case "soundpoolStreamStop":
                // Los getters son asincronos en audioplayers y SoundPool no tiene equivalente
                // directo: TODO compilable (la dependencia ya queda en el pubspec).
                code.append(todo(bean, params));
                break;

            // --- Video -> video_player ---
            case "videoviewSetVideoUri":
                code.append("SkCVideo.setUri(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "videoviewStart":
                code.append("SkCVideo.start(").append(widget(param(params, 0))).append(");");
                break;
            case "videoviewPause":
                code.append("SkCVideo.pause(").append(widget(param(params, 0))).append(");");
                break;
            case "videoviewStop":
                code.append("SkCVideo.stop(").append(widget(param(params, 0))).append(");");
                break;
            case "videoviewGetCurrentPosition":
            case "videoviewGetDuration":
            case "videoviewIsPlaying":
            case "videoviewCanPause":
            case "videoviewCanSeekBackward":
            case "videoviewCanSeekForward":
                code.append(todo(bean, params));
                break;

            // --- GPS / ubicacion -> geolocator ---
            case "locationManagerRequestLocationUpdates":
                code.append("SkCLocation.startUpdates(").append(widget(param(params, 0)))
                        .append(", ").append(widget(param(params, 1))).append(", ")
                        .append(param(params, 2)).append(", ").append(param(params, 3))
                        .append(");");
                break;
            case "locationManagerRemoveUpdates":
                code.append("SkCLocation.stopUpdates(").append(widget(param(params, 0)))
                        .append(");");
                break;

            // --- Sensores -> sensors_plus ---
            case "gyroscopeStartListen":
                code.append("SkCSensors.startGyroscope(").append(widget(param(params, 0)))
                        .append(");");
                break;
            case "gyroscopeStopListen":
                code.append("SkCSensors.stopGyroscope(").append(widget(param(params, 0)))
                        .append(");");
                break;

            // --- Bluetooth -> flutter_blue_plus ---
            case "bluetoothConnectStartConnection":
            case "bluetoothConnectStartConnectionToUuid":
                code.append("SkCBluetooth.startConnection(").append(widget(param(params, 0)))
                        .append(", ").append(param(params, 1)).append(", ")
                        .append(param(params, 2)).append(");");
                break;
            case "bluetoothConnectReadyConnection":
            case "bluetoothConnectReadyConnectionToUuid":
                code.append("SkCBluetooth.startConnection(").append(widget(param(params, 0)))
                        .append(", ").append(param(params, 1)).append(", ")
                        .append(param(params, 2)).append(");");
                break;
            case "bluetoothConnectStopConnection":
                code.append("SkCBluetooth.stopConnection(").append(widget(param(params, 0)))
                        .append(", ").append(param(params, 1)).append(");");
                break;
            case "bluetoothConnectSendData":
                code.append("SkCBluetooth.sendData(").append(widget(param(params, 0)))
                        .append(", ").append(param(params, 1)).append(", ")
                        .append(param(params, 2)).append(");");
                break;
            case "bluetoothConnectActivateBluetooth":
                code.append("SkCBluetooth.activateBluetooth();");
                break;
            case "bluetoothConnectGetPairedDevices":
                code.append("SkCBluetooth.getPairedDevices(").append(widget(param(params, 0)))
                        .append(", ").append(widget(param(params, 1))).append(");");
                break;
            case "bluetoothConnectIsBluetoothEnabled":
            case "bluetoothConnectIsBluetoothActivated":
            case "bluetoothConnectGetRandomUuid":
                // Devuelven un valor asincrono en Flutter: TODO compilable.
                code.append(todo(bean, params));
                break;

            // --- MapView -> flutter_map (OpenStreetMap, libre) ---
            case "mapViewMoveCamera":
                code.append("SkCMap.state(").append(widget(param(params, 0))).append(").move(")
                        .append(param(params, 1)).append(", ").append(param(params, 2))
                        .append(", ").append(param(params, 3)).append(");");
                break;
            case "mapViewZoomTo":
                code.append("SkCMap.state(").append(widget(param(params, 0))).append(").zoomTo(")
                        .append(param(params, 1)).append(");");
                break;
            case "mapViewAddMarker":
                code.append("SkCMap.state(").append(widget(param(params, 0))).append(").addMarker(")
                        .append(param(params, 1)).append(", ").append(param(params, 2))
                        .append(", ").append(param(params, 3)).append(");");
                break;
            case "mapViewZoomIn":
            case "mapViewZoomOut":
            case "mapViewSetMapType":
            case "mapViewSetMarkerInfo":
            case "mapViewSetMarkerPosition":
            case "mapViewSetMarkerColor":
            case "mapViewSetMarkerIcon":
            case "mapViewSetMarkerVisible":
                // flutter_map no expone estas operaciones igual que Google Maps: TODO compilable.
                code.append(todo(bean, params));
                break;

            // --- Anuncios: SDK de pago/servicio -> TODO explicito ---
            case "adViewLoadAd":
            case "interstitialadCreate":
            case "interstitialadLoadAd":
            case "interstitialadShow":
            case "rewardedVideoAdLoad":
            case "rewardedVideoAdShow":
                code.append("// TODO: anuncios en Flutter requieren un SDK de anuncios (p.ej. google_mobile_ads).\n");
                code.append(todo(bean, params));
                break;

            // ================================================================
            // Fase 3: patrones de UI
            // ================================================================
            case "isDrawerOpen":
                code.append("Sk.isDrawerOpen(context)");
                break;
            case "openDrawer":
                code.append("Sk.openDrawer(context);");
                break;
            case "closeDrawer":
                code.append("Sk.closeDrawer(context);");
                break;
            case "fabIcon":
                code.append("Sk.setFabIcon('_fab', ").append(widget(param(params, 0))).append(");");
                break;
            case "fabVisibility":
                code.append("Sk.setFabVisible('_fab', Sk.toText(").append(widget(param(params, 0)))
                        .append(") == 'visible');");
                break;
            case "fabSize":
                code.append(todo(bean, params));
                break;

            // --- Menu de opciones -> PopupMenuButton en el AppBar ---
            case "menuAddItem":
            case "menuAddMenuItem":
            case "menuAddSubmenu":
            case "submenuAddItem":
                code.append("Sk.addMenuItem(").append(lastTextParam(params)).append(");");
                break;
            case "menuInflater":
                code.append("// TODO: menuInflater (menu XML) -> define las acciones con PopupMenuItem.\n");
                code.append(todo(bean, params));
                break;

            // --- TabLayout -> TabBar ---
            case "addTab":
                code.append("Sk.addTab(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "setSelectedTabIndicatorColor":
                code.append("Sk.setTabIndicatorColor(").append(widget(param(params, 0)))
                        .append(", ").append(dartColor(param(params, 1))).append(");");
                break;
            case "setTabTextColors":
                code.append("Sk.setTabTextColors(").append(widget(param(params, 0)))
                        .append(", ").append(dartColor(param(params, 1))).append(", ")
                        .append(dartColor(param(params, 2))).append(");");
                break;
            case "setupWithViewPager":
                code.append("// TODO: TabLayout+ViewPager sincronizados (usa un TabController).\n");
                code.append(todo(bean, params));
                break;

            // --- BottomNavigationView -> BottomNavigationBar ---
            case "bottomMenuAddItem":
                code.append("Sk.addBottomItem(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 2)).append(", ").append(widget(param(params, 3)))
                        .append(");");
                break;

            // --- ViewPager -> PageView ---
            case "pagerSetCurrentItem":
                code.append("Sk.setPage(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 1)).append(");");
                break;
            case "pagerGetCurrentItem":
                code.append("Sk.currentPage(").append(widget(param(params, 0))).append(")");
                break;
            case "pagerSetFragmentAdapter":
                code.append("Sk.setPageCount(").append(widget(param(params, 0))).append(", ")
                        .append(param(params, 2))
                        .append("); // TODO: contenido de cada fragmento");
                break;
            case "pagerSetOffscreenPageLimit":
            case "pagerGetOffscreenPageLimit":
                code.append(todo(bean, params));
                break;

            // --- Adapters personalizados -> ListView.builder esbozado + TODO ---
            case "listSetCustomViewData":
            case "recyclerSetCustomViewData":
            case "gridSetCustomViewData":
            case "spnSetCustomViewData":
            case "pagerSetCustomViewData": {
                String target = widget(param(params, 0));
                String data = param(params, 1);
                code.append("// TODO: adapter personalizado -> el builder del widget esta esbozado;")
                        .append(" enlaza aqui los campos del layout del item.\n");
                code.append("Sk.setListData(").append(target).append(", ").append(data)
                        .append(");");
                break;
            }

            // --- Cadenas de recursos -> lib/strings.dart ---
            case "getResStr": {
                String key = bean.spec == null ? "" : bean.spec;
                code.append(strings == null
                        ? "Sk.resStr('" + escapeString(key) + "')"
                        : strings.dart(key));
                break;
            }
            case "getResString": {
                String key = unquote(param(params, 0));
                code.append(strings == null
                        ? "Sk.resStr('" + escapeString(key) + "')"
                        : strings.dart(key));
                break;
            }

            // --- variables declaradas / bloques personalizados de otros addons ---
            case "addCustomVariable":
                code.append("// variable global: ").append(param(params, 0));
                break;

            case "addSourceDirectly":
                // `addSourceDirectly` inserta codigo del usuario tal cual: en Java es valido,
                // en Dart hay que revisarlo.
                code.append("// addSourceDirectly (revisar; es codigo Android): ")
                        .append(param(params, 0));
                code.append("\n").append(todo(bean, params));
                break;

            default:
                code.append(todo(bean, params));
        }

        if (hasEmptySelectorParam(params, bean.spec)) {
            code.setLength(0);
        }
        return code.toString();
    }

    /**
     * @return el parametro {@code index} como id de componente/vista sin comillas ({@code 'id'}).
     */
    private static String unquote(String value) {
        String text = value == null ? "" : value;
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }

    /**
     * @return el ultimo parametro que parece un literal de texto Dart ({@code "titulo"}), o el
     * ultimo parametro si ninguno lo parece. Los bloques de menu ponen el titulo al final y el
     * numero de parametros varia entre versiones de Sketchware.
     */
    private static String lastTextParam(ArrayList<String> params) {
        for (int i = params.size() - 1; i >= 0; i--) {
            String value = params.get(i);
            if (value != null && value.startsWith("\"")) {
                return value;
            }
        }
        return params.isEmpty() ? "\"\"" : params.get(params.size() - 1);
    }

    /**
     * @return el color de un bloque ({@code #RRGGBB}, {@code 0xAARRGGBB}, numero ARGB o nombre de
     * color de Sketchware) como {@code Color} de Dart.
     */
    private static String dartColor(String value) {
        String text = unquote(value).trim();
        if (text.startsWith("#")) {
            String hex = text.substring(1).toUpperCase();
            if (hex.length() == 6) {
                return "Color(0xFF" + hex + ")";
            }
            if (hex.length() == 8) {
                return "Color(0x" + hex + ")";
            }
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return "Color(" + text + ")";
        }
        if (text.matches("-?\\d+")) {
            return "Color(0xFF000000 | (" + text + " & 0xFFFFFF))";
        }
        switch (text.toLowerCase()) {
            case "black":
                return "Colors.black";
            case "white":
                return "Colors.white";
            case "red":
                return "Colors.red";
            case "green":
                return "Colors.green";
            case "blue":
                return "Colors.blue";
            case "yellow":
                return "Colors.yellow";
            case "gray", "grey":
                return "Colors.grey";
            case "orange":
                return "Colors.orange";
            case "purple":
                return "Colors.purple";
            default:
                return "Colors.blueGrey";
        }
    }

    /**
     * @return codigo del boton de un dialogo de Sketchware ({@code dialogOkButton} y similares):
     * asigna el texto del boton y engancha el cuerpo del evento como {@code void Function()}.
     */
    private String dialogButton(String dialogVariable, String textProperty, String callbackProperty,
                                String text, String body) {
        String dialog = "Sk.dialog(" + widget(dialogVariable) + ")";
        return dialog + "." + textProperty + " = " + text + ";\n"
                + dialog + "." + callbackProperty + " = () {\n"
                + indent(body, 1) + "\n};";
    }

    private String mathCall(String function, ArrayList<String> params, int count) {
        StringBuilder sb = new StringBuilder("Sk.").append(function).append('(');
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("Sk.toNumber(").append(param(params, i)).append(")");
        }
        return sb.append(')').toString();
    }

    /**
     * @return el nombre de la pantalla Dart a la que apunta una variable de Intent, o {@code null}.
     */
    /**
     * Indenta cada linea de {@code code} con {@code levels} niveles de 2 espacios.
     */
    static String indent(String code, int levels) {
        return DartWidgets.indent(code, levels);
    }

    private String resolveScreen(String intentVariable) {
        String activityName = intentScreens.get(intentVariable);
        if (activityName == null) {
            return null;
        }
        return FlutterProjectExporter.screenClassNameForActivity(activityName);
    }

    private String sanitizeIdentifier(String value) {
        if (value == null) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        if (sb.length() == 0) {
            sb.append('0');
        }
        return sb.toString();
    }
}
