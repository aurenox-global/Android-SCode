package io.ascode.android;

import io.ascode.android.rag.LocalAiSemanticContext;

public class LocalAiPromptFactory {
    private static final int MAX_CONTEXT_CHARS = 12000;

    public enum Action {
        EXPLAIN_CODE,
        FIX_CODE,
        GENERATE_FROM_COMMENT
    }

    public enum Role {
        AGENT_MAESTRO("Agente Maestro Android SCode",
            "Experto total en el ecosistema Android SCode: estructura de proyectos, pantallas y actividades; bloques, eventos, listas, mapas, SharedPreferences, intents, timers, Firebase; diseno UI Material; Java/Kotlin del generador; compilacion y librerias locales. Resuelve cualquier peticion devolviendo las acciones JSON del agente (add_view, add_event, inject_code); si la peticion es solo informativa, responde con reply breve en espanol."),
        ASCODE_ARCHITECT("Arquitecto Android SCode",
            "Domina la estructura de proyectos Android SCode: pantallas (xml), actividades (java), eventos, variables, recursos, permisos y flujo de compilacion. Para crear o modificar pantallas devuelve acciones JSON (add_view + inject_code); usa screen=main y parent=root salvo que el usuario pida otra pantalla."),
        BLOCK_LOGIC_ENGINEER("Ingeniero de bloques y logica",
                "Traduce requisitos a logica ejecutable: eventos (initializeLogic, onClick), variables, listas, mapas, SharedPreferences, intents, timers y Firebase. Devuelve inject_code con codigo Java minimo y valido para el evento indicado (initializeLogic o el id de la vista)."),
        UI_UX_DESIGNER("Disenador UI Android/Material",
                "Disena pantallas con jerarquia clara y componentes Material (linear, button, text, input, card, image, scroll). Usa ids secuenciales por tipo (button1, text1, input1...), parent=root por defecto y dimensiones match_parent/wrap_content; nunca inventes ids duplicados."),
        JAVA_KOTLIN_ENGINEER("Programador Java/Kotlin Android",
            "Escribe codigo Android valido para el generador de Android SCode: sentencias sueltas dentro de initializeLogic o de un onClick (sin declarar metodos ahi), findViewById(R.id.button1) o binding.button1, lifecycle, adapters, WebView, intents y APIs nativas. Codigo corto y listo para inyectar."),
        CROSS_PLATFORM_ENGINEER("Arquitecto Flutter/React Native",
            "Disena apps multiplataforma Flutter/React Native con arquitectura por pantallas, estado, navegacion y capa de datos. Si el usuario pide una app visual editable en Android SCode, responde con acciones JSON de vistas en lugar de codigo multiplataforma."),
        BUILD_DEBUGGER("Debugger de compilacion Android SCode",
                "Diagnostica errores de Java/Kotlin/XML/manifest/Gradle/D8/R8, recursos duplicados, imports ambiguos, dependencias y empaquetado. Da parches concretos: archivo y evento donde inyectar codigo o el codigo corregido completo."),
        LOCAL_LIB_NATIVE_ENGINEER("Experto en librerias locales y nativas",
                "Integra JAR/AAR/DEX/res/assets/jniLibs, ProGuard, JNI y .so por ABI dentro de Android SCode. Explica pasos exactos y, cuando sea posible, devuelve el codigo de integracion listo para pegar.");

        private final String title;
        private final String skills;

        Role(String title, String skills) {
            this.title = title;
            this.skills = skills;
        }

        public String getTitle() {
            return title;
        }

        public String getSkills() {
            return skills;
        }

        public String getPromptPrefix() {
            return "Rol: " + title + "\nSkills Android SCode: " + skills + "\n"
                    + "Responde directo, en espanol, con pasos aplicables dentro de Android SCode. "
                    + "Cuando propongas codigo, indica donde pegarlo o que evento/bloque lo activa.\n\n";
        }
    }

    public static String build(Action action, String filename, String language, String content) {
        Role role = switch (action) {
            case EXPLAIN_CODE -> Role.ASCODE_ARCHITECT;
            case FIX_CODE -> Role.BUILD_DEBUGGER;
            case GENERATE_FROM_COMMENT -> Role.JAVA_KOTLIN_ENGINEER;
        };
        return build(action, filename, language, content, role, LocalAiSemanticContext.EMPTY);
    }

    public static String build(Action action, String filename, String language, String content, Role role) {
        return build(action, filename, language, content, role, LocalAiSemanticContext.EMPTY);
    }

    public static String build(Action action,
                               String filename,
                               String language,
                               String content,
                               Role role,
                               LocalAiSemanticContext semanticContext) {
        return build(action, filename, language, content, role, semanticContext, MAX_CONTEXT_CHARS);
    }

    public static String build(Action action,
                               String filename,
                               String language,
                               String content,
                               Role role,
                               LocalAiSemanticContext semanticContext,
                               int maxContextChars) {
        String safeContent = trimContext(content == null ? "" : content, maxContextChars);
        String fileLabel = filename == null || filename.isEmpty() ? "current file" : filename;
        String languageLabel = language == null || language.isEmpty() ? "code" : language;
        Role safeRole = role == null ? Role.ASCODE_ARCHITECT : role;
        LocalAiSemanticContext safeContext = semanticContext == null ? LocalAiSemanticContext.EMPTY : semanticContext;
        String ragSection = safeContext.toPromptSection();
        String ragInstructions = ragSection.isEmpty()
                ? ""
                : "Use the retrieved context when relevant, but prioritize correctness of the current file.\n\n"
                        + ragSection
                        + "\n";

        return switch (action) {
            case EXPLAIN_CODE -> safeRole.getPromptPrefix()
                    + "Explain this " + languageLabel + " file clearly in Spanish. Focus on what it does, risky parts, and practical improvements.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
            case FIX_CODE -> safeRole.getPromptPrefix()
                    + "Find likely bugs in this " + languageLabel + " code and return a corrected version when possible. "
                    + "Keep the same behavior and style. If you cannot safely rewrite the whole file, return focused patches and explain why in Spanish.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
            case GENERATE_FROM_COMMENT -> safeRole.getPromptPrefix()
                    + "Read this " + languageLabel + " file and generate the missing code implied by the latest TODO/comment or incomplete area. "
                    + "Return only useful code first, then a short Spanish note if needed.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
        };
    }

    public static int maxContextCharsFor(int contextSize, int maxTokens) {
        int reservedForOutput = Math.max(maxTokens + 96, 512);
        int tokensForContent = Math.max(256, contextSize - reservedForOutput);
        return tokensForContent * 3;
    }

    public static final String NO_REASONING_INSTRUCTION =
            "IMPORTANTE: NO razones ni muestres pensamiento paso a paso. No incluyas bloques <think> ni explicaciones de tu proceso. Responde directo y conciso.";

    private static final String AGENT_JSON_SCHEMA =
            "IMPORTANTE: \"type\" de cada action es SOLO add_view, update_view, delete_view, move_view, add_event, inject_code, add_variable, update_variable, delete_variable, add_permission, remove_permission o add_screen; el tipo de vista va en \"view_type\" y el de la variable en \"var_type\".\n"
                    + "Acciones:\n"
                    + "  add_view: crear vista. Campos: screen,parent,view_type,id,text,text_size,hint,width,height,orientation,margenes,background_color. view_type: linear|vertical|horizontal|scroll|card|button|text|input|image|webview|progress|list|spinner|checkbox|switch|seekbar\n"
                    + "  update_view: modificar una vista EXISTENTE por su id. Campos: screen,id, y solo los que cambian: text,text_size,hint,width,height,orientation,background_color,text_color,margin,margin_left,margin_top,margin_right,margin_bottom,padding,padding_left,padding_top,padding_right,padding_bottom,gravity,weight. Lo que no envies no se toca.\n"
                    + "  delete_view: BORRAR una vista EXISTENTE y sus hijas/eventos. Campos: screen,id\n"
                    + "  move_view: cambiar de padre o de orden una vista EXISTENTE. Campos: screen,id,parent,index (index=0 es el primero)\n"
                    + "  inject_code: inyectar codigo Java. Campos: target,event,code. event=\"initializeLogic\" o el id de la vista para su onClick\n"
                    + "  add_event: crear el onClick de una vista. Campos: target,view_id,event,code\n"
                    + "  add_variable: crear una variable o una lista. Campos: screen,name,var_type,value(opcional). var_type: boolean|number|string|map (variable) o list_string|list_number|list_map (lista). No repitas una variable que ya exista.\n"
                    + "  update_variable: renombrar o cambiar el tipo de una variable EXISTENTE. Campos: screen,name,new_name(opcional),var_type(opcional),value(opcional)\n"
                    + "  delete_variable: BORRAR una variable EXISTENTE. Campos: screen,name\n"
                    + "  add_permission: agregar un permiso al AndroidManifest. Campos: permission (p.ej. CAMERA o android.permission.CAMERA)\n"
                    + "  remove_permission: quitar un permiso del AndroidManifest. Campos: permission\n"
                    + "  add_screen: crear una pantalla/actividad nueva (xml+java vacios). Campos: name (minusculas, p.ej. login, screen2), orientation(opcional: portrait|landscape|both). Luego puedes usarla en screen/target de otras acciones.\n"
                    + "Reglas:\n"
                    + "  - Crea primero las pantallas (add_screen) del mismo JSON antes de crear vistas dentro de ellas.\n"
                    + "  - Crea primero las vistas (add_view) y despues sus eventos (inject_code/add_event), SIEMPRE juntos en el mismo JSON.\n"
                    + "  - update_view/delete_view/move_view/update_variable/delete_variable/remove_permission solo funcionan con cosas que YA existan; si no existe, se omite sin romper nada.\n"
                    + "  - ids unicos: button1, text1, input1...\n"
                    + "  - screen=main es la pantalla principal. parent=root por defecto (nivel superior).\n"
                    + "  - PARENT (anidar): si el usuario indica DONDE insertar la vista (frases como 'dentro de linear1', 'en el linearX', 'metido en X', 'dentro del layout X', 'dentro del contenedor X'), usa parent=<id EXACTO de esa vista existente> en vez de root. Si esa vista NO existe, creala tambien y usa su id. Nunca inventes un parent inexistente.\n"
                    + "  - ORDEN: si creas un contenedor y algo dentro de el en la misma peticion, el add_view del contenedor va PRIMERO y el add_view del hijo despues, con parent=<id del contenedor>.\n"
                    + "  - codigo Java dentro del evento indicado, sin declarar metodos.\n"
                    + "  - Para preguntas o consejos usa solo \"reply\" con \"actions\": [].\n"
                    + "  - Devuelve SOLO tu propio objeto JSON: empieza por { y termina en }, sin texto antes ni despues, sin bloques de codigo, sin explicaciones y sin repetir estas instrucciones ni los ejemplos.\n"
                    + "Ejemplo 1 (crear):\n"
                    + "{\"reply\":\"Listo, boton creado con su evento.\",\"actions\":["
                    + "{\"type\":\"add_view\",\"screen\":\"main\",\"parent\":\"root\",\"view_type\":\"button\",\"id\":\"button1\",\"text\":\"Presioname\",\"width\":\"match_parent\",\"height\":\"wrap_content\",\"margin_top\":8},"
                    + "{\"type\":\"inject_code\",\"target\":\"main\",\"event\":\"button1\",\"code\":\"Toast.makeText(getApplicationContext(), \\\"Hola\\\", Toast.LENGTH_SHORT).show();\"}]}\n"
                    + "Ejemplo 2 (modificar y borrar):\n"
                    + "{\"reply\":\"Texto y color actualizados; boton borrado.\",\"actions\":["
                    + "{\"type\":\"update_view\",\"screen\":\"main\",\"id\":\"button1\",\"text\":\"Hola\",\"background_color\":\"#00AA00\"},"
                    + "{\"type\":\"delete_view\",\"screen\":\"main\",\"id\":\"button2\"}]}\n"
                    + "Ejemplo 3 (crear DENTRO de un contenedor existente linear1):\n"
                    + "{\"reply\":\"Boton agregado dentro de linear1.\",\"actions\":["
                    + "{\"type\":\"add_view\",\"screen\":\"main\",\"parent\":\"linear1\",\"view_type\":\"button\",\"id\":\"button1\",\"text\":\"Boton\",\"width\":\"match_parent\",\"height\":\"wrap_content\"}]}\n"
                    + "Ejemplo 4 (variable nueva con valor inicial):\n"
                    + "{\"reply\":\"Variable creada en main.\",\"actions\":["
                    + "{\"type\":\"add_variable\",\"screen\":\"main\",\"name\":\"contador\",\"var_type\":\"number\",\"value\":\"0\"}]}\n"
                    + "Ejemplo 5 (permiso) :\n"
                    + "{\"reply\":\"Permiso de camara agregado al manifest.\",\"actions\":["
                    + "{\"type\":\"add_permission\",\"permission\":\"CAMERA\"}]}\n"
                    + "Ejemplo 6 (pantalla nueva y vista dentro de ella):\n"
                    + "{\"reply\":\"Pantalla login creada con un texto.\",\"actions\":["
                    + "{\"type\":\"add_screen\",\"name\":\"login\"},"
                    + "{\"type\":\"add_view\",\"screen\":\"login\",\"parent\":\"root\",\"view_type\":\"text\",\"id\":\"text1\",\"text\":\"Iniciar sesion\"}]}\n";

    public static String buildAgentPrompt(String projectScope,
                                          String userPrompt,
                                          String projectContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres un Agente de Android SCode. Responde UNICAMENTE con un objeto JSON valido ")
                .append("que contenga \"reply\" (1-2 frases en espanol) y \"actions\" (array; vacio si no hay cambios). ")
                .append("No escribas nada antes ni despues del JSON y no repitas estas instrucciones.\n\n")
                .append(AGENT_JSON_SCHEMA)
                .append("\nContexto del proyecto:\n")
                .append(projectContext == null || projectContext.trim().isEmpty()
                        ? "- Scope: " + (projectScope == null ? "" : projectScope) + "\n"
                        : projectContext)
                .append('\n')
                .append(buildParentHint(userPrompt, projectContext))
                .append("\nSolicitud del usuario:\n")
                .append(userPrompt);
        return prompt.toString();
    }

    /**
     * Deterministic prompt hint: if the user message names an existing container view
     * (LinearLayout/RelativeLayout/ScrollView/CardView) that is listed in the project
     * context, tell the model to use that exact id as {@code parent}. Small models often
     * default to {@code parent=root} even when a container is explicitly requested.
     * Kept conservative: it only emits the hint line; the client-side safety net in
     * {@code AgentActionExecutor} still enforces nesting even if the model ignores it.
     */
    public static String buildParentHint(String userPrompt, String projectContext) {
        if (userPrompt == null || userPrompt.trim().isEmpty()
                || projectContext == null || projectContext.trim().isEmpty()) {
            return "";
        }
        java.util.LinkedHashSet<String> mentioned = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = PROJECT_VIEW_PATTERN.matcher(projectContext);
        while (matcher.find()) {
            String id = matcher.group(1);
            String type = matcher.group(2);
            if (!isContainerTypeName(type)) {
                continue;
            }
            if (isMentioned(userPrompt, id)) {
                mentioned.add(id);
            }
        }
        if (mentioned.isEmpty()) {
            return "";
        }
        StringBuilder hint = new StringBuilder("PISTA DE ANIDADO (obligatoria): ");
        if (mentioned.size() == 1) {
            hint.append("el usuario menciona el contenedor existente '")
                    .append(mentioned.iterator().next())
                    .append("'. Cualquier vista que crees 'dentro de' el debe llevar parent=\"")
                    .append(mentioned.iterator().next())
                    .append("\" (NO parent=root).");
        } else {
            hint.append("el usuario menciona los contenedores ")
                    .append(String.join(", ", mentioned))
                    .append("; usa como parent el id exacto del contenedor que corresponda (NO parent=root).");
        }
        return hint.append('\n').toString();
    }

    private static final java.util.regex.Pattern PROJECT_VIEW_PATTERN =
            java.util.regex.Pattern.compile("- id=([A-Za-z0-9_]+) tipo=([A-Za-z0-9_]+)");

    private static boolean isContainerTypeName(String type) {
        if (type == null) {
            return false;
        }
        String lower = type.toLowerCase(java.util.Locale.US);
        return lower.contains("layout") || lower.contains("scroll") || lower.contains("card")
                || lower.contains("container") || lower.contains("root");
    }

    private static boolean isMentioned(String text, String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(id)
                + "(?![A-Za-z0-9_])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    public static String buildAssistantPrompt(String userPrompt, boolean reasoningEnabled) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Actua como asistente breve y preciso. ")
                .append("Responde solo lo que se te pregunta, sin relleno ni explicaciones largas. ")
                .append("No repitas frases ni ideas. ")
                .append("Si no piden detalle, responde en un parrafo corto o lista corta.");
        if (!reasoningEnabled) {
            prompt.append('\n').append(NO_REASONING_INSTRUCTION);
        }
        prompt.append("\n\n").append(userPrompt);
        return prompt.toString();
    }

    private static String trimContext(String content, int maxContextChars) {
        int safeMax = Math.max(2000, maxContextChars);
        if (content.length() <= safeMax) {
            return content;
        }
        int half = safeMax / 2;
        return content.substring(0, half)
                + "\n\n/* ...content trimmed for local model context... */\n\n"
                + content.substring(content.length() - half);
    }
}