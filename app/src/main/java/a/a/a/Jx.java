package a.a.a;

import static dev.aldi.sayuti.block.ExtraBlockFile.getExtraBlockData;

import android.text.TextUtils;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.agus.jcoderz.beans.ViewBeans;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.agus.jcoderz.handle.component.ConstVarComponent;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import mod.hilal.saif.blocks.CommandBlock;
import mod.hilal.saif.events.LogicHandler;
import mod.pranav.viewbinding.ViewBindingBuilder;
import com.ascode.android.control.logic.PermissionManager;
import com.ascode.android.utility.DesignShapeAttrs;
import com.ascode.android.webview.ProjectWebViewSettings;

public class Jx {

    public static final String EOL = "\r\n";
    public static final Pattern WIDGET_NAME_PATTERN = Pattern.compile("\\w*\\..*\\.");

    /**
     * Version of the WebView Text-to-Speech helper block emitted by
     * {@link #appendWebViewTtsHelpers(StringBuilder, String)}. Bump this whenever the helper
     * changes so {@code yq} can detect projects that still carry an older helper and upgrade it
     * in place on the next build (see {@link #hasCurrentWebViewTtsHelper(String)}).
     * <p>Version 4 adds the optional per-event diagnostic Toast (see
     * {@link #WEBVIEW_TTS_DIAG_MARKER_PREFIX}).</p>
     * <p>Version 5 adds the on-screen diagnostics overlay injected by the IDE when the project
     * enabled the diagnostics switch: bridge/shim state, {@code speak()}/{@code stop()} call
     * counters exposed through {@code getDiagnostics()}, captured page JavaScript errors and a
     * &quot;Probar voz&quot; button that drives the engine directly (see
     * {@link #appendWebViewTtsDiagnosticsOverlay(StringBuilder, String)}). With the switch off the
     * emitted helper keeps the exact previous behaviour.</p>
     * <p>Version 6 restores the <em>system</em> engine path to the v1.0.40 behaviour, the one
     * confirmed to sound on real devices: {@code speak(text, QUEUE_FLUSH, null, id)} with no
     * {@code Bundle}/{@code KEY_PARAM_STREAM}/{@code KEY_PARAM_VOLUME}, no
     * {@code setAudioAttributes} and no {@code requestAudioFocus}, and the
     * {@code window.speechSynthesis} shim guarded only by
     * {@code typeof window.speechSynthesis === 'undefined'}.</p>
     */
    public static final int WEBVIEW_TTS_HELPER_VERSION = 6;

    /** Opening marker comment wrapping the versioned WebView TTS helper block. */
    public static final String WEBVIEW_TTS_HELPER_BEGIN_MARKER =
            "// <ascode-tts v" + WEBVIEW_TTS_HELPER_VERSION + ">";

    /** Closing marker comment wrapping the versioned WebView TTS helper block. */
    public static final String WEBVIEW_TTS_HELPER_END_MARKER = "// </ascode-tts>";

    /**
     * Comment, appended to the generated diagnostics flag, that records whether the helper was
     * built with the diagnostic Toast enabled ({@code // ascode-tts-diag=true} / {@code false}).
     * {@code yq} compares it against the project setting so toggling the switch re-emits the
     * helper on the next build, even when the helper version itself did not change.
     */
    public static final String WEBVIEW_TTS_DIAG_MARKER_PREFIX = "// ascode-tts-diag=";

    /**
     * Comment appended to the generated {@code _ttsUsePiper} field recording that the helper was
     * built to prefer the bundled offline Piper voice ({@code // ascode-tts-piper=true}). Absent
     * means the system engine. {@code yq} compares it against the project setting so toggling the
     * switch re-emits the helper on the next build, even when the helper version is unchanged.
     */
    public static final String WEBVIEW_TTS_PIPER_MARKER_PREFIX = "// ascode-tts-piper=";
        private static final Pattern WEBVIEW_DOWNLOAD_LISTENER_PATTERN = Pattern.compile(
            "((?:[A-Za-z_$][A-Za-z0-9_$]*\\s*\\.\\s*)*[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*setDownloadListener\\s*\\(\\s*new\\s+DownloadListener\\s*\\(\\s*\\)\\s*\\{");
        private static final Pattern WEBVIEW_CHROME_CLIENT_PATTERN = Pattern.compile(
            "((?:[A-Za-z_$][A-Za-z0-9_$]*\\s*\\.\\s*)*[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*setWebChromeClient\\s*\\(\\s*new\\s+WebChromeClient\\s*\\(\\s*\\)\\s*\\{");
    private final ProjectSettings settings;
    private final PermissionManager permissionManager;
    private final String packageName;
    private final ProjectFileBean projectFileBean;
    private final eC projectDataManager;
    private final jq buildConfig;
    private final Ox ox;
    private final Boolean isViewBindingEnabled;
    private final ProjectWebViewSettings webViewSettings;
    /**
     * Fields with static initializer that added Components need,
     * e.g. {"private Timer _timer = new Timer();"}
     */
    private final ArrayList<String> fieldsWithStaticInitializers = new ArrayList<>();
    /**
     * Fields of the currently generating class,
     * e.g. {"private FloatingActionBar _fab;"}
     */
    private final ArrayList<String> fields = new ArrayList<>();
    private final ArrayList<String> lists = new ArrayList<>();
    private final ArrayList<String> views = new ArrayList<>();
    /**
     * Field declarations from components. Can include static initializer, but doesn't have to.
     */
    private final ArrayList<String> components = new ArrayList<>();
    /**
     * Statements to be added to initialize(Bundle),
     * e.g. {"_drawer.addDrawerListener(_toggle);"}
     */
    private final ArrayList<String> initializeMethodCode = new ArrayList<>();
    private final ManageLocalLibrary mll;
    /**
     * Component initializer lines which get added to <code>_initialize(Bundle)</code>
     */
    private final ArrayList<String> componentInitializers = new ArrayList<>();
    /**
     * Code of More Blocks that have been created
     */
    private final ArrayList<String> moreBlocks = new ArrayList<>();
    /**
     * Code of inner Adapter classes, used for Widgets like ListView or RecyclerView
     */
    private final ArrayList<String> adapterClasses = new ArrayList<>();
    /**
     * Filled with request code constants for FilePicker components
     */
    private final ArrayList<String> filePickerRequestCodes = new ArrayList<>();

    private final ArrayList<HashMap<String, Object>> extraBlocks;
    private Hx eventManager;
    private ArrayList<String> imports = new ArrayList<>();
    private String onCreateEventCode = "";
    private Material3LibraryManager materialLibraryManager;

    public Jx(jq jqVar, ProjectFileBean projectFileBean, eC eCVar) {
        packageName = jqVar.packageName;
        this.projectFileBean = projectFileBean;
        projectDataManager = eCVar;
        buildConfig = jqVar;
        mll = new ManageLocalLibrary(eCVar.a);
        settings = new ProjectSettings(eCVar.a);
        permissionManager = new PermissionManager(eCVar.a, projectFileBean.getJavaName());
        ox = new Ox(buildConfig, projectFileBean);
        extraBlocks = getExtraBlockData();
        isViewBindingEnabled = settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, BuildSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE);
        webViewSettings = new ProjectWebViewSettings(settings);
        materialLibraryManager = new Material3LibraryManager(projectDataManager.a);
    }

    public String activityResult() {
        ArrayList<BlockBean> blocks = jC.a(projectDataManager.a).a(projectFileBean.getJavaName(), "onActivityResult_onActivityResult");
        return Lx.j(new Fx(projectFileBean.getActivityName(), buildConfig, blocks, isViewBindingEnabled).a(), false);
    }

    public String initializeLogic() {
        ArrayList<BlockBean> blocks = jC.a(projectDataManager.a).a(projectFileBean.getJavaName(), "initializeLogic_initializeLogic");
        return Lx.j(new Fx(projectFileBean.getActivityName(), buildConfig, blocks, isViewBindingEnabled).a(), false);
    }

    private void extraVariables() {
        for (Map.Entry<String, ArrayList<BlockBean>> blocks : jC.a(projectDataManager.a).b(projectFileBean.getJavaName()).entrySet()) {
            for (BlockBean block : blocks.getValue()) {
                switch (block.opCode) {
                    case "addCustomVariable":
                        if (!block.parameters.get(0).trim().isEmpty()) {
                            fields.add(block.parameters.get(0));
                        }
                        break;

                    case "addInitializer":
                        if (!block.parameters.get(0).trim().isEmpty()) {
                            initializeMethodCode.add(block.parameters.get(0));
                        }
                        break;
                }
            }
        }
    }

    private void removeExtraImports() {
        ArrayList<String> newImports = new ArrayList<>();
        for (String value : imports) {
            if (!newImports.contains(value) && !value.trim().isEmpty()) {
                newImports.add(value);
            }
        }
        imports = newImports;
    }

    /**
     * @return Import to be added to the currently generating class
     * (includes import of default launcher activity)
     */
    private String getLauncherActivity(String packageName) {
        String theImport = "";

        String activityName = ProjectFileBean.getActivityName(AndroidManifestInjector.getLauncherActivity(projectDataManager.a));
        if (!activityName.equals("MainActivity")) {
            theImport = "import " + packageName + "." + activityName + ";" + EOL;
        }

        return theImport;
    }

    private String getBillingResponseCode(ConstVarComponent component) {
        HashMap<String, ArrayList<String>> param = component.param;
        if (param == null || !param.containsKey("OnResultBillingResponse")) {
            return "";
        }

        ArrayList<String> arrayList = param.get("OnResultBillingResponse");
        return "if (!" + arrayList.get(0) + ".handleActivityResult(_requestCode, _resultCode, _data))";
    }

    /**
     * @return Generated Java code of the current View (not Widget)
     */
    public String generateCode(boolean isAndroidStudioExport, String sc_id) {
        boolean isDialogFragment = projectFileBean.fileName.contains("_dialog_fragment");
        boolean isBottomDialogFragment = projectFileBean.fileName.contains("_bottomdialog_fragment");
        boolean isFragment = projectFileBean.fileName.contains("_fragment");
        boolean hasGeneratedWebView = hasWebViewInCurrentFile();

        extraVariables();
        handleAppCompat();
        addFieldsDeclaration();
        addDrawerComponentInitializer();
        addDesignGlassInitializers();
        if (hasGeneratedWebView) {
            addWebViewSupportInitializers();
            fields.add("private ValueCallback<Uri[]> _filePathCallback;");
            fields.add("private static final int _WEBVIEW_FILE_CHOOSER_REQUEST_CODE = 2001;");
        }
        initializeEventsCodeGenerator();
        addMoreBlockCodes();
        addAdapterCode();
        addRequestCodeConstants();
        addImportsForBlocks();
        addLocalLibraryImports();

        StringBuilder sb = new StringBuilder(8192);
        sb.append("package ").append(packageName).append(";").append(EOL)
                .append(EOL);
        if (projectFileBean.getActivityName().equals("MainActivity")) {
            sb.append(getLauncherActivity(packageName));
        }

        if (buildConfig.isFirebaseEnabled) addImport("com.google.firebase.FirebaseApp");

        if (buildConfig.isAdMobEnabled) {
            addImport("com.google.android.gms.ads.MobileAds");

            if (buildConfig.isDebugBuild)
                addImport("com.google.android.gms.ads.RequestConfiguration");
        }

        if (buildConfig.g) {
            addImport("androidx.fragment.app.Fragment");
            addImport("androidx.fragment.app.FragmentManager");
            addImport("androidx.fragment.app.DialogFragment");
            if (isBottomDialogFragment) {
                addImport("com.google.android.material.bottomsheet.BottomSheetDialogFragment");
            }
        } else {
            addImport("android.app.Fragment");
            addImport("android.app.FragmentManager");
            addImport("android.app.DialogFragment");
        }
        if (permissionManager.hasNewPermission() || buildConfig.a(projectFileBean.getActivityName()).a()) {
            if (buildConfig.g) {
                addImport("androidx.core.content.ContextCompat");
                addImport("androidx.core.app.ActivityCompat");
            }
            addImport("android.Manifest");
            addImport("android.content.pm.PackageManager");
        }
        if (isAndroidStudioExport && isViewBindingEnabled) {
            addImport(packageName + ".databinding.*");
        }

        removeExtraImports();
        Collections.sort(imports);
        for (String anImport : imports) {
            sb.append("import ").append(anImport).append(";").append(EOL);
        }

        String importsAddedByImportBlocks = LogicHandler.imports(eventManager.generateActivityLifecycleEventCode());
        if (!importsAddedByImportBlocks.isEmpty()) {
            sb.append(importsAddedByImportBlocks).append(EOL);
        }
        sb.append(EOL);

        sb.append("public class ").append(projectFileBean.getActivityName()).append(" extends ");
        if (buildConfig.g) {
            if (isBottomDialogFragment) {
                sb.append("BottomSheetDialogFragment");
            } else if (isDialogFragment) {
                sb.append("DialogFragment");
            } else if (isFragment) {
                sb.append("Fragment");
            } else {
                sb.append("AppCompatActivity");
            }
        } else {
            if (isBottomDialogFragment) {
                sb.append("/* Enable AppCompat to use it */");
            } else if (isDialogFragment) {
                sb.append("DialogFragment");
            } else if (isFragment) {
                sb.append("Fragment");
            } else {
                sb.append("Activity");
            }
        }
        sb.append(" {").append(EOL);

        boolean activityHasFields = false;

        for (String constant : filePickerRequestCodes) {
            if (!constant.isEmpty()) {
                activityHasFields = true;
                sb.append(EOL);
                sb.append(constant);
            }
        }

        if (!fieldsWithStaticInitializers.isEmpty()) {
            if (activityHasFields) sb.append(EOL);
            activityHasFields = true;

            for (String componentFieldDeclaration : fieldsWithStaticInitializers) {
                if (!componentFieldDeclaration.isEmpty()) {
                    sb.append(EOL);
                    sb.append(componentFieldDeclaration);
                }
            }
        }

        if (!fields.isEmpty()) {
            if (activityHasFields) sb.append(EOL);
            activityHasFields = true;

            for (String field : fields) {
                if (!field.isEmpty()) {
                    sb.append(EOL);
                    sb.append(field);
                }
            }
        }

        if (!lists.isEmpty()) {
            if (activityHasFields) sb.append(EOL);
            activityHasFields = true;

            for (String value : lists) {
                if (!value.isEmpty()) {
                    sb.append(EOL);
                    sb.append(value);
                }
            }
        }

        if (!views.isEmpty()) {
            if (activityHasFields) sb.append(EOL);
            activityHasFields = true;

            for (String viewDeclaration : views) {
                if (!viewDeclaration.isEmpty()) {
                    sb.append(EOL);
                    sb.append(viewDeclaration);
                }
            }
        }

        if (!components.isEmpty()) {
            if (activityHasFields) sb.append(EOL);
            activityHasFields = true;

            for (String componentFieldDeclaration : components) {
                if (!componentFieldDeclaration.isEmpty()) {
                    sb.append(EOL);
                    sb.append(componentFieldDeclaration);
                }
            }
        }

        if (activityHasFields) sb.append(EOL);

        sb.append(EOL);
        String bindingName = ViewBindingBuilder.generateFileNameForLayout(projectFileBean.fileName);
        if (isFragment) {
            if (buildConfig.g) {
                sb.append("@NonNull").append(EOL);
                sb.append("@Override").append(EOL);
                sb.append("public View onCreateView(@NonNull LayoutInflater _inflater, " +
                        "@Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {").append(EOL);
            } else {
                sb.append("@Override").append(EOL);
                sb.append("public View onCreateView(LayoutInflater _inflater, ViewGroup _container, " +
                        "Bundle _savedInstanceState) {").append(EOL);
            }
            if (isViewBindingEnabled) {
                sb.append("binding = ").append(bindingName).append(".inflate(_inflater, _container, false);").append(EOL);
                sb.append("initialize(_savedInstanceState, binding.getRoot());");
            } else {
                sb.append("View _view = _inflater.inflate(R.layout.").append(projectFileBean.fileName).append(", _container, false);").append(EOL);
                sb.append("initialize(_savedInstanceState, _view);");
            }
        } else {
            sb.append("@Override").append(EOL);
            sb.append("protected void onCreate(Bundle _savedInstanceState) {").append(EOL);
            sb.append("super.onCreate(_savedInstanceState);").append(EOL);

            if (isViewBindingEnabled) {
                sb.append("binding = ").append(bindingName).append(".inflate(getLayoutInflater());").append(EOL);
                sb.append("setContentView(binding.getRoot());").append(EOL);
            } else {
                sb.append("setContentView(R.layout.").append(projectFileBean.fileName).append(");").append(EOL);
            }
            sb.append("initialize(_savedInstanceState);");
        }
        sb.append(EOL);
        if (buildConfig.isFirebaseEnabled) {
            if (isFragment) {
                sb.append("FirebaseApp.initializeApp(getContext());");
            } else {
                sb.append("FirebaseApp.initializeApp(this);");
            }
            sb.append(EOL);
        }

        if (buildConfig.isAdMobEnabled && !isFragment) {
            if (!buildConfig.isFirebaseEnabled) {
                sb.append(EOL);
            }
            sb.append("MobileAds.initialize(this);");
            sb.append(EOL);
            if (fieldsWithStaticInitializers.contains(Lx.getComponentFieldCode("InterstitialAd"))) {
                sb.append("_ad_unit_id = \"").append(buildConfig.isDebugBuild ? "ca-app-pub-3940256099942544/1033173712" : buildConfig.interstitialAdUnitId).append("\";");
            }
            if (fieldsWithStaticInitializers.contains(Lx.getComponentFieldCode("RewardedVideoAd"))) {
                sb.append("_reward_ad_unit_id = \"").append(buildConfig.isDebugBuild ? "ca-app-pub-3940256099942544/5224354917" : buildConfig.rewardAdUnitId).append("\";");
            }

            if (buildConfig.isDebugBuild) {
                StringBuilder testDevicesListCode = new StringBuilder("List<String> testDeviceIds = Arrays.asList(");
                ArrayList<String> testDevices = buildConfig.t;
                for (int j = 0, testDevicesSize = testDevices.size(); j < testDevicesSize; j++) {
                    String testDeviceId = testDevices.get(j);

                    testDevicesListCode.append("\"").append(testDeviceId).append("\"");
                    if (j != testDevicesSize - 1) {
                        testDevicesListCode.append(", ");
                    }
                }
                testDevicesListCode.append(");").append(EOL);

                sb.append(EOL);
                sb.append(testDevicesListCode);
                sb.append("MobileAds.setRequestConfiguration(new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build());");
            }

            sb.append(EOL);
        }

        if (!isFragment) {
            // Adds initializeLogic() call too, don't worry
            sb.append(permissionManager.writePermission(buildConfig.g, buildConfig.a(projectFileBean.getActivityName()).c));
        } else {
            sb.append("initializeLogic();").append(EOL);
            if (isViewBindingEnabled) {
                sb.append("return binding.getRoot();").append(EOL);
            } else {
                sb.append("return _view;").append(EOL);
            }
        }
        sb.append("}").append(EOL);

        if (permissionManager.hasPermission && !isFragment) {
            sb.append(EOL);
            sb.append("@Override").append(EOL);
            sb.append("public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {").append(EOL);
            sb.append("super.onRequestPermissionsResult(requestCode, permissions, grantResults);").append(EOL);
            sb.append("if (requestCode == 1000) {").append(EOL);
            sb.append("initializeLogic();").append(EOL);
            sb.append("}").append(EOL);
            sb.append("}").append(EOL);
        }
        sb.append(EOL);

        if (isFragment) {
            sb.append("private void initialize(Bundle _savedInstanceState, View _view) {");
        } else {
            sb.append("private void initialize(Bundle _savedInstanceState) {");
        }
        if (!TextUtils.isEmpty(initializeLogic())) {
            sb.append(EOL);
            sb.append(initializeLogic());
        }

        for (String value : initializeMethodCode) {
            if (!value.isEmpty()) {
                sb.append(EOL);
                sb.append(value);
            }
        }

        for (String componentInitializer : componentInitializers) {
            if (!componentInitializer.isEmpty()) {
                sb.append(EOL);
                sb.append(componentInitializer);
            }
        }

        String hxG = eventManager.generateViewEvents();
        if (!hxG.isEmpty()) {
            sb.append(EOL);
            sb.append(EOL);
            sb.append(hxG);
        }

        String hxC = eventManager.generateComponentEvents();
        if (!hxC.isEmpty()) {
            sb.append(EOL);
            sb.append(EOL);
            sb.append(hxC);
        }

        String hxD = eventManager.generateDrawerEvents();
        if (!hxD.isEmpty()) {
            sb.append(EOL);
            sb.append(EOL);
            sb.append(hxD);
        }

        String hxF = eventManager.generateAuthEvents();
        if (!hxF.isEmpty()) {
            sb.append(EOL);
            sb.append(EOL);
            sb.append(hxF);
        }

        sb.append(EOL);
        sb.append("}").append(EOL);
        sb.append(EOL);
        sb.append("private void initializeLogic() {").append(EOL);
        if (!onCreateEventCode.isEmpty()) {
            sb.append(onCreateEventCode).append(EOL);
        }
        sb.append("}").append(EOL);

        if (hasGeneratedWebView) {
            sb.append(EOL);
            sb.append("private boolean _openWebFileChooser(ValueCallback<Uri[]> _callback, WebChromeClient.FileChooserParams _params) {").append(EOL);
            sb.append("if (_filePathCallback != null) {").append(EOL);
            sb.append("_filePathCallback.onReceiveValue(null);").append(EOL);
            sb.append("}").append(EOL);
            sb.append("_filePathCallback = _callback;").append(EOL);
            sb.append("Intent _intent = null;").append(EOL);
            sb.append("if (_params != null) {").append(EOL);
            sb.append("try {").append(EOL);
            sb.append("_intent = _params.createIntent();").append(EOL);
            sb.append("} catch (Exception _e) {").append(EOL);
            sb.append("_intent = null;").append(EOL);
            sb.append("}").append(EOL);
            sb.append("}").append(EOL);
            sb.append("if (_intent == null) {").append(EOL);
            sb.append("_intent = new Intent(Intent.ACTION_GET_CONTENT);").append(EOL);
            sb.append("_intent.addCategory(Intent.CATEGORY_OPENABLE);").append(EOL);
            sb.append("_intent.setType(\"*/*\");").append(EOL);
            sb.append("}").append(EOL);
            sb.append("if (_intent.getType() == null || _intent.getType().isEmpty()) {").append(EOL);
            sb.append("_intent.setType(\"*/*\");").append(EOL);
            sb.append("}").append(EOL);
            sb.append("_intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);").append(EOL);
            sb.append("try {").append(EOL);
            sb.append("startActivityForResult(Intent.createChooser(_intent, \"Select file\"), _WEBVIEW_FILE_CHOOSER_REQUEST_CODE);").append(EOL);
            sb.append("return true;").append(EOL);
            sb.append("} catch (ActivityNotFoundException _e) {").append(EOL);
            sb.append("if (_filePathCallback != null) {").append(EOL);
            sb.append("_filePathCallback.onReceiveValue(null);").append(EOL);
            sb.append("_filePathCallback = null;").append(EOL);
            sb.append("}").append(EOL);
            sb.append("AscodeUtil.showMessage(getApplicationContext(), \"No file picker found\");").append(EOL);
            sb.append("}").append(EOL);
            sb.append("return false;").append(EOL);
            sb.append("}").append(EOL);

            appendWebViewDownloadHelpers(sb, EOL);
            if (webViewSettings.isTtsBridgeEnabled()) {
                appendWebViewTtsHelpers(sb, EOL, webViewSettings.getTtsRate(), webViewSettings.getTtsLang(),
                        webViewSettings.isTtsDiagnosticsEnabled(), webViewSettings.isTtsPiperEnabled());
            }
        }

        String agusComponentsOnActivityResultCode = getBillingResponseCode(buildConfig.x);
        String onActivityResultLogic = activityResult();
        String onActivityResultSwitchLogic = eventManager.getOnActivityResultSwitchCases();
        if (hasGeneratedWebView || !agusComponentsOnActivityResultCode.isEmpty() || !onActivityResultLogic.isEmpty() || !onActivityResultSwitchLogic.isEmpty()) {
            sb.append(EOL);
            sb.append("@Override").append(EOL);
            if (isFragment) {
                sb.append("public");
            } else {
                sb.append("protected");
            }
            sb.append(" void onActivityResult(int _requestCode, int _resultCode, Intent _data) {").append(EOL);
            sb.append(agusComponentsOnActivityResultCode);
            sb.append("super.onActivityResult(_requestCode, _resultCode, _data);").append(EOL);
            if (hasGeneratedWebView) {
                sb.append("if (_requestCode == _WEBVIEW_FILE_CHOOSER_REQUEST_CODE) {").append(EOL);
                sb.append("if (_filePathCallback == null) {").append(EOL);
                sb.append("return;").append(EOL);
                sb.append("}").append(EOL);
                sb.append("Uri[] _results = null;").append(EOL);
                sb.append("if (_resultCode == Activity.RESULT_OK) {").append(EOL);
                sb.append("_results = WebChromeClient.FileChooserParams.parseResult(_resultCode, _data);").append(EOL);
                sb.append("if (_results == null && _data != null) {").append(EOL);
                sb.append("Uri _singleUri = _data.getData();").append(EOL);
                sb.append("if (_singleUri != null) {").append(EOL);
                sb.append("_results = new Uri[] {_singleUri};").append(EOL);
                sb.append("} else if (_data.getClipData() != null && _data.getClipData().getItemCount() > 0) {").append(EOL);
                sb.append("ClipData _clipData = _data.getClipData();").append(EOL);
                sb.append("_results = new Uri[_clipData.getItemCount()];").append(EOL);
                sb.append("for (int _i = 0; _i < _clipData.getItemCount(); _i++) {").append(EOL);
                sb.append("_results[_i] = _clipData.getItemAt(_i).getUri();").append(EOL);
                sb.append("}").append(EOL);
                sb.append("}").append(EOL);
                sb.append("}").append(EOL);
                sb.append("}").append(EOL);
                sb.append("_filePathCallback.onReceiveValue(_results);").append(EOL);
                sb.append("_filePathCallback = null;").append(EOL);
                sb.append("return;").append(EOL);
                sb.append("}").append(EOL);
            }
            sb.append(onActivityResultLogic).append(EOL);
            sb.append("switch (_requestCode) {").append(EOL);
            sb.append(onActivityResultSwitchLogic).append(EOL);
            sb.append("default:").append(EOL);
            sb.append("break;").append(EOL);
            sb.append("}").append(EOL);
            sb.append("}").append(EOL);
        }

        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER) && !isViewBindingEnabled) {
            eventManager.addLifecycleEvent("onBackPressed", "DrawerLayout", "_drawer");
        }

        ArrayList<ViewBean> beans = projectDataManager.d(projectFileBean.getXmlName());
        for (ViewBean next : beans) {
            if (next.type == ViewBean.VIEW_TYPE_WIDGET_MAPVIEW) {
                eventManager.addLifecycleEvent("onStart", "MapView", next.id);
                eventManager.addLifecycleEvent("onResume", "MapView", next.id);
                eventManager.addLifecycleEvent("onPause", "MapView", next.id);
                eventManager.addLifecycleEvent("onStop", "MapView", next.id);
                eventManager.addLifecycleEvent("onDestroy", "MapView", next.id);
            }
            if (next.type == ViewBean.VIEW_TYPE_WIDGET_ADVIEW) {
                eventManager.addLifecycleEvent("onResume", "AdView", next.id);
                eventManager.addLifecycleEvent("onPause", "AdView", next.id);
                eventManager.addLifecycleEvent("onDestroy", "AdView", next.id);
            }
        }
        if (!eventManager.k.isEmpty()) {
            sb.append(EOL);
            sb.append(eventManager.k).append(EOL);
        }
        if (!eventManager.l.isEmpty()) {
            sb.append(EOL);
            sb.append(eventManager.l);
            sb.append(EOL);
        }

        String base = LogicHandler.base(eventManager.generateActivityLifecycleEventCode());
        if (!base.isEmpty()) {
            sb.append(EOL);
            sb.append(base);
        }

        for (String moreBlocksCode : moreBlocks) {
            sb.append(EOL);
            sb.append(moreBlocksCode).append(EOL);
        }

        sb.append(EOL);
        for (int j = 0, qSize = adapterClasses.size(); j < qSize; j++) {
            String adapterCode = adapterClasses.get(j);

            if (base.contains("public CharSequence onTabLayoutNewTabAdded(int _position) {")
                    || !adapterCode.contains("return onTabLayoutNewTabAdded(pos);")) {
                sb.append(adapterCode);
            } else {
                sb.append(adapterCode.replace("return onTabLayoutNewTabAdded(pos);",
                        "// Use the Activity Event (onTabLayoutNewTabAdded) in order to use this method" + EOL +
                                "return \"page \" + String.valueOf(pos);"));
            }
            if (j != qSize - 1) {
                sb.append(EOL);
            }
        }
        if (!isFragment && !settings.getValue(ProjectSettings.SETTING_DISABLE_OLD_METHODS, BuildSettings.SETTING_GENERIC_VALUE_TRUE)
                .equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            sb.append(getDeprecatedMethodsCode());
        }
        sb.append("}").append(EOL);
        String code = sb.toString();

        if (isFragment) {
            code = code.replaceAll("getApplicationContext\\(\\)", "getContext().getApplicationContext()")
                    .replaceAll("getBaseContext\\(\\)", "getActivity().getBaseContext()")
                    .replaceAll("\\(ClipboardManager\\) getSystemService", "(ClipboardManager) getContext().getSystemService")
                    .replaceAll("\\(Vibrator\\) getSystemService", "(Vibrator) getContext().getSystemService")
                    .replaceAll("\\(SensorManager\\) getSystemService", "(SensorManager) getContext().getSystemService")
                    .replaceAll("Typeface.createFromAsset\\(getAssets\\(\\)", "Typeface.createFromAsset(getContext().getAssets()")
                    .replaceAll("= getAssets\\(\\).open", "= getContext().getAssets().open")
                    .replaceAll("getSharedPreferences", "getContext().getSharedPreferences")
                    .replaceAll("AlertDialog.Builder\\(this\\);", "AlertDialog.Builder(getActivity());")
                    .replaceAll("SpeechRecognizer.createSpeechRecognizer\\(this\\);", "SpeechRecognizer.createSpeechRecognizer(getContext());")
                    .replaceAll("new RequestNetwork\\(this\\);", "new RequestNetwork((Activity) getContext());")
                    .replaceAll("new BluetoothConnect\\(this\\);", "new BluetoothConnect((Activity) getContext());")
                    .replaceAll("MobileAds.getRewardedVideoAdInstance\\(this\\);", "MobileAds.getRewardedVideoAdInstance(getContext());")
                    .replaceAll("runOnUiThread\\(new", "getActivity().runOnUiThread(new")
                    .replaceAll(".setLayoutManager\\(new LinearLayoutManager\\(this", ".setLayoutManager(new LinearLayoutManager(getContext()")
                    .replaceAll("getLayoutInflater\\(\\)", "getActivity().getLayoutInflater()")
                    .replaceAll("getSupportFragmentManager\\(\\)", "getActivity().getSupportFragmentManager()");
        } else if (buildConfig.g) {
            code = code.replaceAll("getFragmentManager", "getSupportFragmentManager");
        }

        String generatedCode = CommandBlock.CB(Lx.j(code, false));
        if (hasGeneratedWebView) {
            generatedCode = ensureGeneratedWebViewImportExportSupport(generatedCode, EOL);
        }
        return generatedCode;
    }

    private String getListDeclarationAndAddImports(int listType, String listName) {
        String typeName = mq.b(listType);
        addImports(mq.getImportsByTypeName(projectDataManager.a, typeName, null));
        return Lx.a(typeName, listName, Lx.AccessModifier.PRIVATE);
    }

    private String getComponentDeclarationAndAddImports(ComponentBean componentBean) {
        String typeName = mq.a(componentBean.type);
        addImports(mq.getImportsByTypeName(projectDataManager.a, typeName, null));
        return Lx.a(typeName, componentBean.componentId, Lx.AccessModifier.PRIVATE, componentBean.param1, componentBean.param2, componentBean.param3);
    }

    private String getDrawerViewDeclarationAndAddImports(ViewBean viewBean) {
        String viewType = WIDGET_NAME_PATTERN.matcher(viewBean.convert).replaceAll("");
        if (viewType.isEmpty()) {
            viewType = viewBean.getClassInfo().getClassName();
        }
        addImports(mq.getImportsByTypeName(projectDataManager.a, viewType, null));
        return Lx.a(viewType, "_drawer_" + viewBean.id, Lx.AccessModifier.PRIVATE, isViewBindingEnabled);
    }

    /**
     * @return Definition line for a Variable
     */
    private String getVariableDeclarationAndAddImports(int variableType, String name) {
        String variableTypeName = mq.c(variableType);
        addImports(mq.getImportsByTypeName(projectDataManager.a, variableTypeName, null));
        return Lx.a(variableTypeName, name, Lx.AccessModifier.PRIVATE);
    }

    private String getViewDeclarationAndAddImports(ViewBean viewBean) {
        String viewType = WIDGET_NAME_PATTERN.matcher(viewBean.convert).replaceAll("");
        if (viewType.isEmpty()) {
            viewType = viewBean.getClassInfo().getClassName();
        }
        if (requireImports(viewBean)) {
            addImports(mq.getImportsByTypeName(projectDataManager.a, viewType, viewBean.convert));
        }
        return Lx.a(viewType, viewBean.id, Lx.AccessModifier.PRIVATE, isViewBindingEnabled);
    }

    private String getDeprecatedMethodsCode() {
        return EOL +
                "@Deprecated" + EOL +
                "public void showMessage(String _s) {" + EOL +
                "Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public int getLocationX(View _v) {" + EOL +
                "int _location[] = new int[2];" + EOL +
                "_v.getLocationInWindow(_location);" + EOL +
                "return _location[0];" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public int getLocationY(View _v) {" + EOL +
                "int _location[] = new int[2];" + EOL +
                "_v.getLocationInWindow(_location);" + EOL +
                "return _location[1];" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public int getRandom(int _min, int _max) {" + EOL +
                "Random random = new Random();" + EOL +
                "return random.nextInt(_max - _min + 1) + _min;" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public ArrayList<Double> getCheckedItemPositionsToArray(ListView _list) {" + EOL +
                "ArrayList<Double> _result = new ArrayList<Double>();" + EOL +
                "SparseBooleanArray _arr = _list.getCheckedItemPositions();" + EOL +
                "for (int _iIdx = 0; _iIdx < _arr.size(); _iIdx++) {" + EOL +
                "if (_arr.valueAt(_iIdx))" + EOL +
                "_result.add((double)_arr.keyAt(_iIdx));" + EOL +
                "}" + EOL +
                "return _result;" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public float getDip(int _input) {" + EOL +
                "return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _input, getResources().getDisplayMetrics());" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public int getDisplayWidthPixels() {" + EOL +
                "return getResources().getDisplayMetrics().widthPixels;" + EOL +
                "}" + EOL +
                EOL +
                "@Deprecated" + EOL +
                "public int getDisplayHeightPixels() {" + EOL +
                "return getResources().getDisplayMetrics().heightPixels;" + EOL +
                "}" + EOL;
    }

    private void addImport(String classToImport) {
        if (!imports.contains(classToImport)) {
            imports.add(classToImport);
        }
    }

    private void addImports(ArrayList<String> imports) {
        if (imports != null) {
            for (String value : imports) {
                addImport(value);
            }
        }
    }

    /**
     * @see Lx#getComponentInitializerCode(String, String, String...)
     */
    private String getComponentBeanInitializer(ComponentBean componentBean) {
        return Lx.getComponentInitializerCode(mq.a(componentBean.type), componentBean.componentId, componentBean.param1, componentBean.param2, componentBean.param3);
    }

    private void handleAppCompat() {
        if (buildConfig.g) {
            addImport("androidx.appcompat.app.AppCompatActivity");
            addImport("androidx.annotation.*");
        } else {
            addImport("android.app.Activity");
        }
        if (isViewBindingEnabled) {
            fields.add("private " + ViewBindingBuilder.generateFileNameForLayout(projectFileBean.fileName) + " binding;");
        }

        if (buildConfig.g) {
            if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR) && !projectFileBean.fileName.contains("_fragment")) {
                addImport(
                        (materialLibraryManager.isMaterial3Enabled()) ? "com.google.android.material.appbar.MaterialToolbar" : "androidx.appcompat.widget.Toolbar"
                );
                addImport("androidx.coordinatorlayout.widget.CoordinatorLayout");
                addImport("com.google.android.material.appbar.AppBarLayout");

                if (isViewBindingEnabled) {
                    initializeMethodCode.add(
                            "setSupportActionBar(binding.Toolbar);" + EOL +
                                    "getSupportActionBar().setDisplayHomeAsUpEnabled(true);" + EOL +
                                    "getSupportActionBar().setHomeButtonEnabled(true);" + EOL +
                                    "binding.Toolbar.setNavigationOnClickListener(new View.OnClickListener() {" + EOL +
                                    "@Override" + EOL +
                                    "public void onClick(View _v) {" + EOL +
                                    "onBackPressed();" + EOL +
                                    "}" + EOL +
                                    "});"
                    );
                } else {
                    fields.add("private " +
                            (materialLibraryManager.isMaterial3Enabled() ? "MaterialToolbar" : "Toolbar") +
                            " _toolbar;");
                    fields.add("private AppBarLayout _app_bar;");
                    fields.add("private CoordinatorLayout _coordinator;");

                    initializeMethodCode.add(
                            "_app_bar = findViewById(R.id._app_bar);" + EOL +
                                    "_coordinator = findViewById(R.id._coordinator);" + EOL +
                                    "_toolbar = findViewById(R.id._toolbar);" + EOL +
                                    "setSupportActionBar(_toolbar);" + EOL +
                                    "getSupportActionBar().setDisplayHomeAsUpEnabled(true);" + EOL +
                                    "getSupportActionBar().setHomeButtonEnabled(true);" + EOL +
                                    "_toolbar.setNavigationOnClickListener(new View.OnClickListener() {" + EOL +
                                    "@Override" + EOL +
                                    "public void onClick(View _v) {" + EOL +
                                    "onBackPressed();" + EOL +
                                    "}" + EOL +
                                    "});"
                    );
                }
            }
            if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) {
                addImport("com.google.android.material.floatingactionbutton.FloatingActionButton");

                if (!isViewBindingEnabled) {
                    fields.add("private FloatingActionButton _fab;");
                    initializeMethodCode.add("_fab = " +
                            (projectFileBean.fileName.contains("_fragment") ? "_view." : "") +
                            "findViewById(R.id._fab);");
                }
            }
            if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER) && !projectFileBean.fileName.contains("_fragment")) {
                addImport("androidx.core.view.GravityCompat");
                addImport("androidx.drawerlayout.widget.DrawerLayout");
                addImport("androidx.appcompat.app.ActionBarDrawerToggle");


                if (isViewBindingEnabled) {
                    initializeMethodCode.add(
                            "ActionBarDrawerToggle _toggle = new ActionBarDrawerToggle(" +
                                    projectFileBean.getActivityName() + ".this, binding.Drawer, " +

                                    (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR) ?
                                            "binding.Toolbar, " : "") +

                                    "R.string.app_name, R.string.app_name);" + EOL +
                                    "binding.Drawer.addDrawerListener(_toggle);" + EOL +
                                    "_toggle.syncState();" + EOL
                    );
                } else {
                    fields.add("private DrawerLayout _drawer;");
                    initializeMethodCode.add("_drawer = findViewById(R.id._drawer);" + EOL +
                            "ActionBarDrawerToggle _toggle = new ActionBarDrawerToggle(" +
                            projectFileBean.getActivityName() + ".this, _drawer, " +
                            (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR) ?
                                    "_toolbar, " : "") +
                            "R.string.app_name, R.string.app_name);" + EOL +
                            "_drawer.addDrawerListener(_toggle);" + EOL +
                            "_toggle.syncState();" + EOL + EOL +
                            "LinearLayout _nav_view = findViewById(R.id._nav_view);" + EOL
                    );
                }
                addImports(mq.getImportsByTypeName(projectDataManager.a, "LinearLayout", null));
            }
        }
        addImport("android.app.*");
        addImport("android.os.*");
        addImport("android.view.*");
        addImport("android.view.View.*");
        addImport("android.widget.*");
        addImport("android.content.*");
        addImport("android.content.res.*");
        addImport("android.graphics.*");
        addImport("android.graphics.drawable.*");
        addImport("android.media.*");
        addImport("android.net.*");
        addImport("android.text.*");
        addImport("android.text.style.*");
        addImport("android.util.*");
        addImport("android.webkit.*");
        addImport("android.animation.*");
        addImport("android.view.animation.*");
        addImport("java.io.*");
        addImport("java.util.*");
        addImport("java.util.regex.*");
        addImport("java.text.*");
        addImport("org.json.*");
        onCreateEventCode = new Fx(projectFileBean.getActivityName(), buildConfig, projectDataManager.a(projectFileBean.getJavaName(), "onCreate_initializeLogic"), isViewBindingEnabled).a();
    }

    private String getDrawerViewInitializer(ViewBean viewBean) {
        String replaceAll = WIDGET_NAME_PATTERN.matcher(viewBean.convert).replaceAll("");
        if (replaceAll.isEmpty()) {
            replaceAll = viewBean.getClassInfo().getClassName();
        }
        return Lx.getDrawerViewInitializer(replaceAll, viewBean.id, "_nav_view");
    }

    private void addAdapterCode() {
        for (ViewBean viewBean : projectDataManager.f(projectFileBean.getXmlName())) {
            String xmlName = ProjectFileBean.getXmlName(viewBean.customView);
            projectFileBean.getJavaName();
            String eventName = viewBean.id + "_onBindCustomView";
            String adapterLogic = new Fx(projectFileBean.getActivityName(), buildConfig, projectDataManager.a(projectFileBean.getJavaName(), eventName), isViewBindingEnabled).a();
            String adapterCode;
            if (viewBean.type == ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER) {
                adapterCode = Lx.pagerAdapter(ox, viewBean.id, viewBean.customView, projectDataManager.d(xmlName), adapterLogic, isViewBindingEnabled);
            } else if (viewBean.type == ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW) {
                adapterCode = Lx.recyclerViewAdapter(ox, viewBean.id, viewBean.customView, projectDataManager.d(xmlName), adapterLogic, isViewBindingEnabled);
                addImport("androidx.recyclerview.widget.LinearLayoutManager");
                addImport("androidx.recyclerview.widget.RecyclerView");
            } else {
                adapterCode = Lx.getListAdapterCode(ox, viewBean.id, viewBean.customView, projectDataManager.d(xmlName), adapterLogic, isViewBindingEnabled);
            }
            adapterClasses.add(adapterCode);
        }
    }

    private String getViewInitializer(ViewBean viewBean) {
        String replaceAll = WIDGET_NAME_PATTERN.matcher(viewBean.convert).replaceAll("");
        if (replaceAll.isEmpty()) {
            replaceAll = viewBean.getClassInfo().getClassName();
        }
        if (projectFileBean.fileName.contains("_fragment")) {
            return Lx.getViewInitializer(replaceAll, viewBean.id, true, isViewBindingEnabled, webViewSettings);
        }
        return Lx.getViewInitializer(replaceAll, viewBean.id, false, isViewBindingEnabled, webViewSettings);
    }

    private void addMoreBlockCodes() {
        String javaName = projectFileBean.getJavaName();
        ArrayList<Pair<String, String>> pairs = projectDataManager.i(javaName);
        for (int index = 0, pairsSize = pairs.size(); index < pairsSize; index++) {
            Pair<String, String> next = pairs.get(index);
            String name = next.first + "_moreBlock";
            String code = Lx.getMoreBlockCode(next.first, next.second, new Fx(projectFileBean.getActivityName(), buildConfig, projectDataManager.a(javaName, name), isViewBindingEnabled).a());
            if (index < (pairsSize - 1)) {
                moreBlocks.add(code);
            } else {
                // Removes unnecessary newline at end of More Block code
                moreBlocks.add(code.substring(0, code.length() - 2));
            }
        }
    }

    private void initializeEventsCodeGenerator() {
        eventManager = new Hx(buildConfig, projectFileBean, projectDataManager);
        addImports(eventManager.getImports());
    }

    /**
     * Adds imports for blocks used in the currently generated Activity.
     */
    private void addImportsForBlocks() {
        for (Map.Entry<String, ArrayList<BlockBean>> entry : projectDataManager.b(projectFileBean.getJavaName()).entrySet()) {
            for (BlockBean blockBean : entry.getValue()) {
                switch (blockBean.opCode) {
                    case "toStringWithDecimal":
                    case "toStringFormat":
                        addImport("java.text.DecimalFormat");
                        break;

                    case "strToListMap":
                    case "strToListStr":
                    case "strToMap":
                    case "GsonStringToListString":
                    case "GsonStringToListNumber":
                        addImport("com.google.gson.Gson");
                        addImport("com.google.gson.reflect.TypeToken");
                        break;

                    case "mapToStr":
                    case "listMapToStr":
                    case "GsonListTojsonString":
                        addImport("com.google.gson.Gson");
                        break;

                    case "setTypeface":
                        addImport("android.graphics.Typeface");
                        break;

                    case "copyToClipboard":
                        addImport("android.content.ClipData");
                        addImport("android.content.ClipboardManager");
                        break;

                    case "fileutilGetLastSegmentPath":
                        addImport("android.net.Uri");
                        break;

                    case "setImageUrl":
                        addImport("com.bumptech.glide.Glide");
                        break;

                    case "interstitialAdLoad":
                    case "rewardedVideoAdLoad":
                        addImport("com.google.android.gms.ads.AdRequest");
                        addImport("com.google.android.gms.ads.LoadAdError");
                        break;
                    default:
                        var block = getExtraBlockByName(blockBean.opCode);
                        if (block != null && block.containsKey("imports")) {
                            var imports = block.get("imports").toString().split("\n");
                            for (String importCode : imports) {
                                addImport(importCode);
                            }
                        }
                        break;
                }
            }
        }
    }

    private Map<String, Object> getExtraBlockByName(String name) {
        for (Map<String, Object> block : extraBlocks) {
            if (block.containsKey("name") && block.get("name").toString().equals(name)) {
                return block;
            }
        }
        return null;
    }

    /**
     * Handles the Activity's Drawer Views and Components
     */
    private void addDrawerComponentInitializer() {
        ArrayList<ViewBean> viewBeans = projectDataManager.d(projectFileBean.getXmlName());
        for (ViewBean viewBean : viewBeans) {
            if (!viewBean.convert.equals("include")) {
                Set<String> toNotAdd = ox.readAttributesToReplace(viewBean);
                if (!toNotAdd.contains("android:id")) {
                    initializeMethodCode.add(getViewInitializer(viewBean));
                }
            }
        }
        if (!isViewBindingEnabled) {
            if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                ArrayList<ViewBean> drawerBeans = projectDataManager.d(projectFileBean.getDrawerXmlName());
                for (ViewBean viewBean : drawerBeans) {
                    if (!viewBean.convert.equals("include")) {
                        Set<String> toNotAdd = ox.readAttributesToReplace(viewBean);
                        if (!toNotAdd.contains("android:id")) {
                            initializeMethodCode.add(getDrawerViewInitializer(viewBean));
                        }
                    }
                }
            }
        }
        ArrayList<ComponentBean> componentBeans = projectDataManager.e(projectFileBean.getJavaName());
        for (ComponentBean componentBean : componentBeans) {
            componentInitializers.add(getComponentBeanInitializer(componentBean));
        }
    }

    /**
     * Handles the file's request code constants.
     */
    private void addRequestCodeConstants() {
        int startValue = 100;
        for (ComponentBean next : projectDataManager.e(projectFileBean.getJavaName())) {
            switch (next.type) {
                case ComponentBean.COMPONENT_TYPE_CAMERA:
                case ComponentBean.COMPONENT_TYPE_FILE_PICKER:
                case 31:
                    int incrementedValue = startValue + 1;
                    filePickerRequestCodes.add(Lx.getRequestCodeConstant(next.componentId, incrementedValue));
                    startValue = incrementedValue;
                    break;
            }
        }
    }

    private void addFieldsDeclaration() {
        String javaName = projectFileBean.getJavaName();
        for (Pair<Integer, String> next : new ArrayList<>(projectDataManager.k(javaName))) {
            int variableId = next.first;
            String variableValue = next.second;
            if (variableId == 9) {
                addImport(variableValue);
            } else if (variableId == 6) {
                fields.add(variableValue + ";");
            } else {
                fields.add(getVariableDeclarationAndAddImports(variableId, variableValue));
            }
        }
        for (Pair<Integer, String> next2 : projectDataManager.j(javaName)) {
            lists.add(getListDeclarationAndAddImports(next2.first, next2.second));
        }
        for (ViewBean viewBean : projectDataManager.d(projectFileBean.getXmlName())) {
            if (!viewBean.convert.equals("include")) {
                Set<String> toNotAdd = ox.readAttributesToReplace(viewBean);
                if (!toNotAdd.contains("android:id")) {
                    String viewDeclarations = getViewDeclarationAndAddImports(viewBean);
                    if (!viewDeclarations.isEmpty()) {
                        views.add(viewDeclarations);
                    }
                }
            }
        }

        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            for (ViewBean viewBean : projectDataManager.d(projectFileBean.getDrawerXmlName())) {
                if (!viewBean.convert.equals("include")) {
                    Set<String> toNotAdd = ox.readAttributesToReplace(viewBean);
                    if (!toNotAdd.contains("android:id")) {
                        String drawerViewDeclarations = getDrawerViewDeclarationAndAddImports(viewBean);
                        if (!drawerViewDeclarations.isEmpty()) {
                            views.add(drawerViewDeclarations);
                        }
                    }
                }
            }
        }
        ArrayList<ComponentBean> componentBeans = projectDataManager.e(javaName);
        for (ComponentBean bean : componentBeans) {
            components.add(getComponentDeclarationAndAddImports(bean));
        }

        boolean hasTimer = false;
        boolean hasFirebaseDB = false;
        boolean hasFirebaseStorage = false;
        boolean hasInterstitialAd = false;
        boolean hasRewardedVideoAd = false;
        for (ComponentBean bean : componentBeans) {
            switch (bean.type) {
                case ComponentBean.COMPONENT_TYPE_TIMERTASK:
                    hasTimer = true;
                    break;

                case ComponentBean.COMPONENT_TYPE_FIREBASE:
                    hasFirebaseDB = true;
                    break;

                case ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE:
                    hasFirebaseStorage = true;
                    break;

                case ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD:
                    hasInterstitialAd = true;
                    break;

                case ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD:
                    hasRewardedVideoAd = true;
                    break;
            }
        }
        if (hasTimer) {
            fieldsWithStaticInitializers.add(Lx.getComponentFieldCode("Timer"));
        }
        if (hasFirebaseDB) {
            fieldsWithStaticInitializers.add(Lx.getComponentFieldCode("FirebaseDB"));
        }
        if (hasFirebaseStorage) {
            fieldsWithStaticInitializers.add(Lx.getComponentFieldCode("FirebaseStorage"));
        }
        if (hasInterstitialAd) {
            fieldsWithStaticInitializers.add(Lx.getComponentFieldCode("InterstitialAd"));
        }
        if (hasRewardedVideoAd) {
            fieldsWithStaticInitializers.add(Lx.getComponentFieldCode("RewardedVideoAd"));
        }
    }

    private boolean hasWebViewInCurrentFile() {
        for (ViewBean viewBean : projectDataManager.d(projectFileBean.getXmlName())) {
            if (viewBean.type == ViewBean.VIEW_TYPE_WIDGET_WEBVIEW) {
                return true;
            }
        }
        return false;
    }

    /**
     * Aplica el blur del modo glass en la APP COMPILADA, a traves de los inicializadores del
     * Activity/Fragment (se ejecutan tras crear las vistas). El desenfoque de lo que hay DETRAS de
     * la vista no es posible de forma nativa: aqui se difumina el PROPIO widget. En API < 31 no se
     * emite blur (el glass queda como translucido + borde + esquinas, sin excepciones).
     */
    private void addDesignGlassInitializers() {
        for (ViewBean viewBean : projectDataManager.d(projectFileBean.getXmlName())) {
            if (viewBean == null || viewBean.id == null || viewBean.id.startsWith("_") || viewBean.inject == null) {
                continue;
            }
            if ("include".equals(viewBean.convert)) {
                continue;
            }
            if (!DesignShapeAttrs.isGlass(viewBean.inject)) {
                continue;
            }
            int blurDp = DesignShapeAttrs.glassBlurDp(viewBean.inject);
            if (blurDp <= 0) {
                continue;
            }
            String viewRef = Lx.getBindingOrViewName(viewBean.id, isViewBindingEnabled);
            initializeMethodCode.add(
                    "if (android.os.Build.VERSION.SDK_INT >= 31) {" + EOL +
                            "try {" + EOL +
                            viewRef + ".setRenderEffect(android.graphics.RenderEffect.createBlurEffect(" + blurDp
                            + "f * getResources().getDisplayMetrics().density, " + blurDp
                            + "f * getResources().getDisplayMetrics().density, android.graphics.Shader.TileMode.CLAMP));" + EOL +
                            "} catch (Throwable _glassError) {}" + EOL +
                            "}"
            );
        }
    }

    private void addWebViewSupportInitializers() {
        boolean ttsBridgeEnabled = webViewSettings.isTtsBridgeEnabled();
        if (ttsBridgeEnabled) {
            addImport("android.speech.tts.TextToSpeech");
        }
        for (ViewBean viewBean : projectDataManager.d(projectFileBean.getXmlName())) {
            if (viewBean.type != ViewBean.VIEW_TYPE_WIDGET_WEBVIEW) {
                continue;
            }

            String webViewName = isViewBindingEnabled
                    ? Lx.getBindingOrViewName(viewBean.id, true)
                    : viewBean.id;

            StringBuilder chromeClient = new StringBuilder(512);
            chromeClient.append(webViewName).append(".setWebChromeClient(new WebChromeClient() {").append(EOL);
            chromeClient.append("@Override").append(EOL);
            chromeClient.append("public boolean onShowFileChooser(WebView _webView, ValueCallback<Uri[]> _filePathCallback, FileChooserParams _fileChooserParams) {").append(EOL);
            chromeClient.append("return _openWebFileChooser(_filePathCallback, _fileChooserParams);").append(EOL);
            chromeClient.append("}").append(EOL);
            if (ttsBridgeEnabled) {
                chromeClient.append("@Override").append(EOL);
                chromeClient.append("public void onProgressChanged(WebView _webView, int _newProgress) {").append(EOL);
                chromeClient.append("if (_newProgress >= 100) {").append(EOL);
                chromeClient.append("_injectTtsShim(_webView);").append(EOL);
                if (webViewSettings.isTtsDiagnosticsEnabled()) {
                    chromeClient.append("_injectTtsDiagnosticsOverlay(_webView);").append(EOL);
                }
                chromeClient.append("}").append(EOL);
                chromeClient.append("}").append(EOL);
            }
            chromeClient.append("});");
            initializeMethodCode.add(chromeClient.toString());

            initializeMethodCode.add(
                    webViewName + ".addJavascriptInterface(new _BlobDownloadBridge(), \"_BlobDownloader\");"
            );

            if (ttsBridgeEnabled) {
                initializeMethodCode.add(
                        webViewName + ".addJavascriptInterface(new _TtsBridge(), \"AndroidBridge\");"
                );
            }

            initializeMethodCode.add(buildSafeWebViewDownloadListener(webViewName, EOL));
        }
    }

    private boolean requireImports(ViewBean viewBean) {
        if (!isViewBindingEnabled) {
            return true;
        }
        return switch (viewBean.type) {
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW,
                 ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW,
                 ViewBeans.VIEW_TYPE_LAYOUT_BOTTOMNAVIGATIONVIEW,
                 ViewBean.VIEW_TYPE_WIDGET_SPINNER,
                 ViewBean.VIEW_TYPE_WIDGET_WEBVIEW,
                 ViewBean.VIEW_TYPE_WIDGET_ADVIEW,
                 ViewBean.VIEW_TYPE_WIDGET_MAPVIEW,
                 ViewBeans.VIEW_TYPE_LAYOUT_SWIPEREFRESHLAYOUT,
                 ViewBeans.VIEW_TYPE_WIDGET_PATTERNLOCKVIEW,
                 ViewBeans.VIEW_TYPE_WIDGET_CODEVIEW,
                 ViewBeans.VIEW_TYPE_WIDGET_LOTTIEANIMATIONVIEW,
                 ViewBeans.VIEW_TYPE_WIDGET_YOUTUBEPLAYERVIEW,
                 ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT,
                 ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER ->
                    true; // it's necessary for the adapters, listeners...
            default -> false;
        };
    }

    /**
     * Adds Local libraries' imports
     */
    private void addLocalLibraryImports() {
        for (String value : mll.getImportLocalLibrary()) {
            addImport(value);
        }
    }

    /**
     * Imports required by {@link #appendWebViewDownloadHelpers(StringBuilder, String)} and
     * {@link #buildSafeWebViewDownloadListener(String, String)}. Used by the in-place migration
     * patcher in {@code yq#inPlacePatchLegacyWebViewDownload(...)} to ensure legacy generated
     * MainActivity files compile after being patched.
     */
    public static final String[] WEBVIEW_DOWNLOAD_HELPER_REQUIRED_IMPORTS = new String[] {
            "android.app.DownloadManager",
            "android.content.ContentValues",
            "android.content.Context",
            "android.content.Intent",
            "android.net.Uri",
            "android.os.Build",
            "android.os.Environment",
            "android.os.Handler",
            "android.os.Looper",
            "android.webkit.DownloadListener",
            "android.webkit.JavascriptInterface",
            "android.webkit.URLUtil",
            "android.webkit.WebView",
            "java.io.File",
            "java.io.FileOutputStream",
            "java.io.OutputStream",
            "java.util.Locale",
                "java.util.UUID",
            "org.json.JSONObject"
    };

    /**
     * Builds a safe {@code setDownloadListener(...)} invocation plus the required
     * {@code addJavascriptInterface(...)} call for the given WebView variable name.
     * Used both by the generator and by {@code yq}'s in-place migration patcher.
     */
    public static String buildSafeWebViewDownloadListener(String webViewName, String EOL) {
        return webViewName + ".setDownloadListener(new DownloadListener() {" + EOL
                + "@Override" + EOL
                + "public void onDownloadStart(String _url, String _userAgent, String _contentDisposition, String _mimeType, long _contentLength) {" + EOL
                + "try {" + EOL
                + "_downloadWebFile(" + webViewName + ", _url, _userAgent, _contentDisposition, _mimeType);" + EOL
                + "} catch (Throwable _e) {" + EOL
                + "AscodeUtil.showMessage(getApplicationContext(), \"Download failed\");" + EOL
                + "}" + EOL
                + "}" + EOL
                + "});" + EOL
                + webViewName + ".addJavascriptInterface(new _BlobDownloadBridge(), \"_BlobDownloader\");";
    }

    public static String ensureGeneratedWebViewImportExportSupport(String javaCode, String EOL) {
        String updatedCode = replaceUnsafeWebViewDownloadListeners(javaCode, EOL);
        return ensureWebChromeClientsOpenFileChooser(updatedCode, EOL);
    }

    public static boolean hasUnsafeWebViewDownloadListener(String javaCode) {
        if (javaCode == null || javaCode.isEmpty()) {
            return false;
        }

        Matcher listenerMatcher = WEBVIEW_DOWNLOAD_LISTENER_PATTERN.matcher(javaCode);
        int searchFrom = 0;
        while (listenerMatcher.find(searchFrom)) {
            int braceOpenIndex = findAnonymousClassBraceOpen(javaCode, listenerMatcher.end());
            int afterBody = findMatchingBraceEnd(javaCode, braceOpenIndex);
            if (afterBody < 0) {
                searchFrom = listenerMatcher.end();
                continue;
            }

            int invocationEnd = findAnonymousListenerInvocationEnd(javaCode, afterBody);
            if (invocationEnd < 0) {
                searchFrom = listenerMatcher.end();
                continue;
            }

            String body = javaCode.substring(braceOpenIndex, afterBody);
            if (body.contains("DownloadManager.Request") && body.contains("Uri.parse(")) {
                return true;
            }
            searchFrom = invocationEnd;
        }
        return false;
    }

    public static boolean hasWebChromeClientWithoutFileChooser(String javaCode) {
        if (javaCode == null || javaCode.isEmpty()) {
            return false;
        }

        Matcher clientMatcher = WEBVIEW_CHROME_CLIENT_PATTERN.matcher(javaCode);
        int searchFrom = 0;
        while (clientMatcher.find(searchFrom)) {
            int braceOpenIndex = findAnonymousClassBraceOpen(javaCode, clientMatcher.end());
            int afterBody = findMatchingBraceEnd(javaCode, braceOpenIndex);
            if (afterBody < 0) {
                searchFrom = clientMatcher.end();
                continue;
            }

            int invocationEnd = findAnonymousListenerInvocationEnd(javaCode, afterBody);
            if (invocationEnd < 0) {
                searchFrom = clientMatcher.end();
                continue;
            }

            String body = javaCode.substring(braceOpenIndex, afterBody);
            if (!body.contains("onShowFileChooser(")) {
                return true;
            }
            searchFrom = invocationEnd;
        }
        return false;
    }

    public static String replaceUnsafeWebViewDownloadListeners(String javaCode, String EOL) {
        if (javaCode == null || javaCode.isEmpty()) {
            return javaCode;
        }

        String currentCode = javaCode;
        int searchFrom = 0;
        while (true) {
            Matcher listenerMatcher = WEBVIEW_DOWNLOAD_LISTENER_PATTERN.matcher(currentCode);
            if (!listenerMatcher.find(searchFrom)) {
                break;
            }

            String webViewName = normalizeWebViewExpression(listenerMatcher.group(1));
            int invocationStart = listenerMatcher.start();
            int braceOpenIndex = findAnonymousClassBraceOpen(currentCode, listenerMatcher.end());
            int afterBody = findMatchingBraceEnd(currentCode, braceOpenIndex);
            if (afterBody < 0) {
                searchFrom = listenerMatcher.end();
                continue;
            }

            int invocationEnd = findAnonymousListenerInvocationEnd(currentCode, afterBody);
            if (invocationEnd < 0) {
                searchFrom = listenerMatcher.end();
                continue;
            }

            String body = currentCode.substring(braceOpenIndex, afterBody);
            if (body.contains("_downloadWebFile(") && !body.contains("DownloadManager.Request")) {
                searchFrom = invocationEnd;
                continue;
            }
            if (!body.contains("DownloadManager.Request") || !body.contains("Uri.parse(")) {
                searchFrom = invocationEnd;
                continue;
            }

            String safeListener = buildSafeWebViewDownloadListener(webViewName, EOL);
            currentCode = currentCode.substring(0, invocationStart)
                    + safeListener
                    + currentCode.substring(invocationEnd);
            searchFrom = invocationStart + safeListener.length();
        }
        return currentCode;
    }

    public static String ensureWebChromeClientsOpenFileChooser(String javaCode, String EOL) {
        if (javaCode == null || javaCode.isEmpty()) {
            return javaCode;
        }

        String currentCode = javaCode;
        int searchFrom = 0;
        while (true) {
            Matcher clientMatcher = WEBVIEW_CHROME_CLIENT_PATTERN.matcher(currentCode);
            if (!clientMatcher.find(searchFrom)) {
                break;
            }

            int braceOpenIndex = findAnonymousClassBraceOpen(currentCode, clientMatcher.end());
            int afterBody = findMatchingBraceEnd(currentCode, braceOpenIndex);
            if (afterBody < 0) {
                searchFrom = clientMatcher.end();
                continue;
            }

            int invocationEnd = findAnonymousListenerInvocationEnd(currentCode, afterBody);
            if (invocationEnd < 0) {
                searchFrom = clientMatcher.end();
                continue;
            }

            String body = currentCode.substring(braceOpenIndex, afterBody);
            if (body.contains("onShowFileChooser(")) {
                searchFrom = invocationEnd;
                continue;
            }

            String fileChooserOverride = buildWebFileChooserOverride(EOL);
            currentCode = currentCode.substring(0, braceOpenIndex + 1)
                    + fileChooserOverride
                    + currentCode.substring(braceOpenIndex + 1);
            searchFrom = braceOpenIndex + fileChooserOverride.length() + 1;
        }
        return currentCode;
    }

    private static String buildWebFileChooserOverride(String EOL) {
        return EOL
                + "@Override" + EOL
                + "public boolean onShowFileChooser(WebView _webView, ValueCallback<Uri[]> _filePathCallback, FileChooserParams _fileChooserParams) {" + EOL
                + "return _openWebFileChooser(_filePathCallback, _fileChooserParams);" + EOL
                + "}" + EOL;
    }

    private static String normalizeWebViewExpression(String expression) {
        return expression == null ? "" : expression.replaceAll("\\s+", "");
    }

    private static int findAnonymousClassBraceOpen(String source, int matcherEnd) {
        if (source == null || matcherEnd <= 0) {
            return -1;
        }
        for (int index = matcherEnd - 1; index >= 0; index--) {
            if (source.charAt(index) == '{') {
                return index;
            }
        }
        return -1;
    }

    private static int findAnonymousListenerInvocationEnd(String source, int afterBody) {
        if (source == null || afterBody < 0) {
            return -1;
        }

        int cursor = afterBody;
        int length = source.length();
        while (cursor < length && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= length || source.charAt(cursor) != ')') {
            return -1;
        }
        cursor++;
        while (cursor < length && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= length || source.charAt(cursor) != ';') {
            return -1;
        }
        return cursor + 1;
    }

    private static int findMatchingBraceEnd(String source, int braceOpenIndex) {
        if (source == null || braceOpenIndex < 0 || braceOpenIndex >= source.length()) {
            return -1;
        }

        int depth = 1;
        int cursor = braceOpenIndex + 1;
        int length = source.length();
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        while (cursor < length && depth > 0) {
            char current = source.charAt(cursor);
            char next = cursor + 1 < length ? source.charAt(cursor + 1) : '\0';
            if (inLineComment) {
                if (current == '\n') inLineComment = false;
            } else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    cursor++;
                }
            } else if (inString) {
                if (current == '\\' && next != '\0') {
                    cursor++;
                } else if (current == '"') {
                    inString = false;
                }
            } else if (inChar) {
                if (current == '\\' && next != '\0') {
                    cursor++;
                } else if (current == '\'') {
                    inChar = false;
                }
            } else {
                if (current == '/' && next == '/') {
                    inLineComment = true;
                    cursor++;
                } else if (current == '/' && next == '*') {
                    inBlockComment = true;
                    cursor++;
                } else if (current == '"') {
                    inString = true;
                } else if (current == '\'') {
                    inChar = true;
                } else if (current == '{') {
                    depth++;
                } else if (current == '}') {
                    depth--;
                }
            }
            cursor++;
        }
        return depth == 0 ? cursor : -1;
    }

    /**
     * Appends the WebView download helper methods (and the {@code _BlobDownloadBridge} inner
     * class) to {@code sb}. These helpers safely handle {@code blob:}, {@code data:} and
     * {@code http(s):} download URLs, avoiding the {@link IllegalArgumentException} thrown by
     * {@code DownloadManager.Request} when the URI scheme is not {@code http(s)}.
     */
    public static void appendWebViewDownloadHelpers(StringBuilder sb, String EOL) {
        sb.append(EOL);
        sb.append("private String _blobDownloadNonce = \"\";").append(EOL);
        sb.append("private String _blobDownloadOrigin = \"\";").append(EOL);

        sb.append(EOL);
        sb.append("private String _safeWebOrigin(String _url) {").append(EOL);
        sb.append("if (_url == null || _url.isEmpty()) {").append(EOL);
        sb.append("return \"\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("Uri _uri = Uri.parse(_url);").append(EOL);
        sb.append("String _scheme = _uri.getScheme() == null ? \"\" : _uri.getScheme().toLowerCase(Locale.US);").append(EOL);
        sb.append("if (_scheme.equals(\"http\") || _scheme.equals(\"https\")) {").append(EOL);
        sb.append("String _host = _uri.getHost();").append(EOL);
        sb.append("if (_host == null || _host.isEmpty()) {").append(EOL);
        sb.append("return \"\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("int _port = _uri.getPort();").append(EOL);
        sb.append("return _port > 0 ? (_scheme + \"://\" + _host + \":\" + _port) : (_scheme + \"://\" + _host);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_scheme.equals(\"file\")) {").append(EOL);
        sb.append("return \"file://\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_scheme.equals(\"content\")) {").append(EOL);
        sb.append("return \"content://\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _ignored) {").append(EOL);
        sb.append("}").append(EOL);
        sb.append("return \"\";").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private boolean _isTrustedBlobBridgeCall(String _origin, String _nonce) {").append(EOL);
        sb.append("if (_nonce == null || _nonce.isEmpty()) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (!_nonce.equals(_blobDownloadNonce)) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _expectedOrigin = _blobDownloadOrigin == null ? \"\" : _blobDownloadOrigin;").append(EOL);
        sb.append("String _resolvedOrigin = _safeWebOrigin(_origin);").append(EOL);
        sb.append("if (_expectedOrigin.isEmpty() || _resolvedOrigin.isEmpty()) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("boolean _trusted = _expectedOrigin.equals(_resolvedOrigin);").append(EOL);
        sb.append("_blobDownloadNonce = \"\";").append(EOL);
        sb.append("_blobDownloadOrigin = \"\";").append(EOL);
        sb.append("return _trusted;").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _downloadWebFile(WebView _sourceWebView, String _url, String _userAgent, String _contentDisposition, String _mimeType) {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_url = _url == null ? \"\" : _url.trim();").append(EOL);
        sb.append("_url = Uri.decode(_url);").append(EOL);
        sb.append("if (_url.isEmpty()) {").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"No file to export\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("Uri _parsedUri = Uri.parse(_url);").append(EOL);
        sb.append("String _scheme = _parsedUri.getScheme() == null ? \"\" : _parsedUri.getScheme().toLowerCase(Locale.US);").append(EOL);
        sb.append("if (_scheme.equals(\"blob\")) {").append(EOL);
        sb.append("if (_downloadBlobFromWebView(_sourceWebView, _url, _mimeType, _contentDisposition)) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Export failed\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_scheme.equals(\"data\")) {").append(EOL);
        sb.append("if (_saveDataUrlToDownloads(_url, _mimeType, _contentDisposition)) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Export failed\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (!_scheme.equals(\"http\") && !_scheme.equals(\"https\")) {").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Unsupported export URL\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("DownloadManager.Request _request = new DownloadManager.Request(_parsedUri);").append(EOL);
        sb.append("_request.setMimeType(_mimeType);").append(EOL);
        sb.append("_request.addRequestHeader(\"User-Agent\", _userAgent);").append(EOL);
        sb.append("String _fileName = URLUtil.guessFileName(_url, _contentDisposition, _mimeType);").append(EOL);
        sb.append("_request.setTitle(_fileName);").append(EOL);
        sb.append("_request.setDescription(_url);").append(EOL);
        sb.append("_request.allowScanningByMediaScanner();").append(EOL);
        sb.append("_request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);").append(EOL);
        sb.append("_request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, _fileName);").append(EOL);
        sb.append("Context _context = getApplicationContext();").append(EOL);
        sb.append("DownloadManager _downloadManager = (DownloadManager) _context.getSystemService(Context.DOWNLOAD_SERVICE);").append(EOL);
        sb.append("if (_downloadManager != null) {").append(EOL);
        sb.append("_downloadManager.enqueue(_request);").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Downloading: \" + _fileName);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _e) {").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Download failed\");").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private String _escapeForJavascript(String _value) {").append(EOL);
        sb.append("if (_value == null) {").append(EOL);
        sb.append("return \"\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _quoted = JSONObject.quote(_value);").append(EOL);
        sb.append("return _quoted.substring(1, _quoted.length() - 1);").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private boolean _downloadBlobFromWebView(WebView _sourceWebView, String _url, String _mimeType, String _contentDisposition) {").append(EOL);
        sb.append("if (_sourceWebView == null || _url == null || _url.isEmpty()) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _resolvedOrigin = _safeWebOrigin(_sourceWebView.getUrl());").append(EOL);
        sb.append("if (_resolvedOrigin.isEmpty()) {").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Untrusted page\");").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_blobDownloadOrigin = _resolvedOrigin;").append(EOL);
        sb.append("_blobDownloadNonce = UUID.randomUUID().toString();").append(EOL);
        sb.append("String _resolvedMimeType = (_mimeType == null || _mimeType.isEmpty()) ? \"application/octet-stream\" : _mimeType;").append(EOL);
        sb.append("String _resolvedFileName = URLUtil.guessFileName(_url, _contentDisposition, _resolvedMimeType);").append(EOL);
        sb.append("if (_resolvedFileName == null || _resolvedFileName.isEmpty()) {").append(EOL);
        sb.append("_resolvedFileName = \"export_\" + System.currentTimeMillis();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _escapedUrl = _escapeForJavascript(_url);").append(EOL);
        sb.append("String _escapedMime = _escapeForJavascript(_resolvedMimeType);").append(EOL);
        sb.append("String _escapedFileName = _escapeForJavascript(_resolvedFileName);").append(EOL);
        sb.append("String _escapedOrigin = _escapeForJavascript(_resolvedOrigin);").append(EOL);
        sb.append("String _escapedNonce = _escapeForJavascript(_blobDownloadNonce);").append(EOL);
        sb.append("String _javascript = \"(function(){\"").append(EOL);
        sb.append("+ \"try{\"").append(EOL);
        sb.append("+ \"var xhr=new XMLHttpRequest();\"").append(EOL);
        sb.append("+ \"xhr.open('GET', '\" + _escapedUrl + \"', true);\"").append(EOL);
        sb.append("+ \"xhr.responseType='blob';\"").append(EOL);
        sb.append("+ \"xhr.onload=function(){\"").append(EOL);
        sb.append("+ \"if(xhr.status!==200&&xhr.status!==0){window._BlobDownloader.onBlobDownload('', '\" + _escapedMime + \"', '\" + _escapedFileName + \"', '\" + _escapedOrigin + \"', '\" + _escapedNonce + \"');return;}\"").append(EOL);
        sb.append("+ \"var reader=new FileReader();\"").append(EOL);
        sb.append("+ \"reader.onloadend=function(){\"").append(EOL);
        sb.append("+ \"var data=(reader.result||'').toString();\"").append(EOL);
        sb.append("+ \"var comma=data.indexOf(',');\"").append(EOL);
        sb.append("+ \"var base64=comma> -1 ? data.substring(comma+1) : '';\"").append(EOL);
        sb.append("+ \"window._BlobDownloader.onBlobDownload(base64, '\" + _escapedMime + \"', '\" + _escapedFileName + \"', '\" + _escapedOrigin + \"', '\" + _escapedNonce + \"');\"").append(EOL);
        sb.append("+ \"};\"").append(EOL);
        sb.append("+ \"reader.readAsDataURL(xhr.response);\"").append(EOL);
        sb.append("+ \"};\"").append(EOL);
        sb.append("+ \"xhr.onerror=function(){window._BlobDownloader.onBlobDownload('', '\" + _escapedMime + \"', '\" + _escapedFileName + \"', '\" + _escapedOrigin + \"', '\" + _escapedNonce + \"');};\"").append(EOL);
        sb.append("+ \"xhr.send();\"").append(EOL);
        sb.append("+ \"}catch(e){window._BlobDownloader.onBlobDownload('', '\" + _escapedMime + \"', '\" + _escapedFileName + \"', '\" + _escapedOrigin + \"', '\" + _escapedNonce + \"');}\"").append(EOL);
        sb.append("+ \"})();\";").append(EOL);
        sb.append("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {").append(EOL);
        sb.append("_sourceWebView.evaluateJavascript(_javascript, null);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_sourceWebView.loadUrl(\"javascript:\" + _javascript);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Preparing export...\");").append(EOL);
        sb.append("return true;").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private boolean _saveDataUrlToDownloads(String _dataUrl, String _mimeType, String _contentDisposition) {").append(EOL);
        sb.append("if (_dataUrl == null || !_dataUrl.startsWith(\"data:\")) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("int _commaIndex = _dataUrl.indexOf(',');").append(EOL);
        sb.append("if (_commaIndex <= 5) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _meta = _dataUrl.substring(5, _commaIndex);").append(EOL);
        sb.append("String _payload = _dataUrl.substring(_commaIndex + 1);").append(EOL);
        sb.append("if (_payload.isEmpty()) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _metaMimeType = _meta;").append(EOL);
        sb.append("int _separatorIndex = _meta.indexOf(';');").append(EOL);
        sb.append("if (_separatorIndex > -1) {").append(EOL);
        sb.append("_metaMimeType = _meta.substring(0, _separatorIndex);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _resolvedMimeType = (_mimeType == null || _mimeType.isEmpty()) ? _metaMimeType : _mimeType;").append(EOL);
        sb.append("if (_resolvedMimeType == null || _resolvedMimeType.isEmpty()) {").append(EOL);
        sb.append("_resolvedMimeType = \"application/octet-stream\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _resolvedFileName = URLUtil.guessFileName(\"export\", _contentDisposition, _resolvedMimeType);").append(EOL);
        sb.append("if (_resolvedFileName == null || _resolvedFileName.isEmpty()) {").append(EOL);
        sb.append("_resolvedFileName = \"export_\" + System.currentTimeMillis();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_meta.contains(\";base64\")) {").append(EOL);
        sb.append("_saveBlobBase64ToDownloads(_payload, _resolvedMimeType, _resolvedFileName);").append(EOL);
        sb.append("return true;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _textPayload = Uri.decode(_payload);").append(EOL);
        sb.append("String _base64Payload = android.util.Base64.encodeToString(_textPayload.getBytes(\"UTF-8\"), android.util.Base64.NO_WRAP);").append(EOL);
        sb.append("_saveBlobBase64ToDownloads(_base64Payload, _resolvedMimeType, _resolvedFileName);").append(EOL);
        sb.append("return true;").append(EOL);
        sb.append("} catch (Throwable _e) {").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _saveBlobBase64ToDownloads(String _base64Data, String _mimeType, String _fileName) {").append(EOL);
        sb.append("Context _context = getApplicationContext();").append(EOL);
        sb.append("if (_context == null) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_base64Data == null || _base64Data.isEmpty()) {").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Export failed\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _resolvedMimeType = (_mimeType == null || _mimeType.isEmpty()) ? \"application/octet-stream\" : _mimeType;").append(EOL);
        sb.append("String _resolvedFileName = (_fileName == null || _fileName.isEmpty()) ? (\"export_\" + System.currentTimeMillis()) : _fileName;").append(EOL);
        sb.append("byte[] _bytes = android.util.Base64.decode(_base64Data, android.util.Base64.DEFAULT);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {").append(EOL);
        sb.append("ContentValues _values = new ContentValues();").append(EOL);
        sb.append("_values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, _resolvedFileName);").append(EOL);
        sb.append("_values.put(android.provider.MediaStore.Downloads.MIME_TYPE, _resolvedMimeType);").append(EOL);
        sb.append("_values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);").append(EOL);
        sb.append("Uri _uri = _context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, _values);").append(EOL);
        sb.append("if (_uri == null) {").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Export failed\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("OutputStream _outputStream = _context.getContentResolver().openOutputStream(_uri);").append(EOL);
        sb.append("if (_outputStream == null) {").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Export failed\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_outputStream.write(_bytes);").append(EOL);
        sb.append("_outputStream.flush();").append(EOL);
        sb.append("_outputStream.close();").append(EOL);
        sb.append("_values.clear();").append(EOL);
        sb.append("_values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);").append(EOL);
        sb.append("_context.getContentResolver().update(_uri, _values, null, null);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("File _downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);").append(EOL);
        sb.append("if (_downloads != null && !_downloads.exists()) {").append(EOL);
        sb.append("_downloads.mkdirs();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("File _outputFile = new File(_downloads, _resolvedFileName);").append(EOL);
        sb.append("FileOutputStream _fileOutputStream = new FileOutputStream(_outputFile);").append(EOL);
        sb.append("_fileOutputStream.write(_bytes);").append(EOL);
        sb.append("_fileOutputStream.flush();").append(EOL);
        sb.append("_fileOutputStream.close();").append(EOL);
        sb.append("sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(_outputFile)));").append(EOL);
        sb.append("}").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Exported: \" + _resolvedFileName);").append(EOL);
        sb.append("} catch (Exception _e) {").append(EOL);
        sb.append("AscodeUtil.showMessage(_context, \"Export failed\");").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private class _BlobDownloadBridge {").append(EOL);
        sb.append("@JavascriptInterface").append(EOL);
        sb.append("public void onBlobDownload(final String _base64Data, final String _mimeType, final String _fileName, final String _origin, final String _nonce) {").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("if (!_isTrustedBlobBridgeCall(_origin, _nonce)) {").append(EOL);
        sb.append("AscodeUtil.showMessage(getApplicationContext(), \"Blocked untrusted export\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_saveBlobBase64ToDownloads(_base64Data, _mimeType, _fileName);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
    }

    /**
     * Appends the WebView Text-to-Speech helper methods (and the {@code _TtsBridge} inner class)
     * to {@code sb}. Compiled Android apps expose a JavaScript bridge named {@code AndroidBridge}
     * (methods {@code speak(String texto, String rate)} / {@code stop()}), and a minimal
     * {@code window.speechSynthesis} shim so HTML written against the Web Speech API keeps working
     * inside a WebView, where the platform does not implement {@code window.speechSynthesis}.
     */
    public static void appendWebViewTtsHelpers(StringBuilder sb, String EOL) {
        appendWebViewTtsHelpers(sb, EOL, ProjectWebViewSettings.DEFAULT_TTS_RATE, ProjectWebViewSettings.DEFAULT_TTS_LANG, false);
    }

    /**
     * Same as {@link #appendWebViewTtsHelpers(StringBuilder, String)} but using the project's
     * configured default speech rate and language. With the default values (0.95 / es-ES) the
     * generated code is identical to the pre-configuration output. Diagnostic toasts stay off.
     */
    public static void appendWebViewTtsHelpers(StringBuilder sb, String EOL, String defaultRate, String defaultLang) {
        appendWebViewTtsHelpers(sb, EOL, defaultRate, defaultLang, false);
    }

    /**
     * Same as {@link #appendWebViewTtsHelpers(StringBuilder, String, String, String)} but also
     * controlling whether the generated helper shows a short diagnostic Toast on every TTS event
     * (motor, requested language, {@code isLanguageAvailable}, {@code setLanguage} result,
     * {@code queued}, engine count and the last event). When {@code showDiagnosticsToasts} is
     * {@code false} the emitted code is identical to the previous helper version.
     */
    public static void appendWebViewTtsHelpers(StringBuilder sb, String EOL, String defaultRate, String defaultLang, boolean showDiagnosticsToasts) {
        appendWebViewTtsHelpers(sb, EOL, defaultRate, defaultLang, showDiagnosticsToasts, false);
    }

    /**
     * Same as {@link #appendWebViewTtsHelpers(StringBuilder, String, String, String, boolean)} but
     * also choosing the synthesis backend. When {@code usePiper} is {@code true} the emitted helper
     * prefers the bundled, fully-offline Piper voice through sherpa-onnx (model under
     * {@code assets/piper/}, PCM played with {@code AudioTrack}) and degrades to the system
     * {@code TextToSpeech} engine when the voice is missing or fails to load. With {@code usePiper}
     * {@code false} the emitted code is byte-for-byte identical to the system-only helper.
     */
    public static void appendWebViewTtsHelpers(StringBuilder sb, String EOL, String defaultRate, String defaultLang,
                                               boolean showDiagnosticsToasts, boolean usePiper) {
        String diagLiteral = showDiagnosticsToasts ? "true" : "false";
        String rate = (defaultRate == null || defaultRate.trim().isEmpty())
                ? ProjectWebViewSettings.DEFAULT_TTS_RATE : defaultRate.trim();
        String lang = (defaultLang == null || defaultLang.trim().isEmpty())
                ? ProjectWebViewSettings.DEFAULT_TTS_LANG : defaultLang.trim();
        float rateFloat;
        try {
            rateFloat = Float.parseFloat(rate);
        } catch (NumberFormatException e) {
            rate = ProjectWebViewSettings.DEFAULT_TTS_RATE;
            rateFloat = Float.parseFloat(rate);
        }
        String rateLiteral = rateFloat + "f";
        String[] langParts = lang.split("-", 2);
        String localeCode = "new Locale(\"" + langParts[0] + "\""
                + (langParts.length > 1 ? ", \"" + langParts[1] + "\"" : "") + ")";

        sb.append(EOL);
        sb.append(WEBVIEW_TTS_HELPER_BEGIN_MARKER).append(EOL);
        sb.append("private TextToSpeech _tts;").append(EOL);
        sb.append("private boolean _ttsReady = false;").append(EOL);
        sb.append("private String _ttsPendingText = null;").append(EOL);
        sb.append("private String _ttsPendingRate = \"").append(rate).append("\";").append(EOL);
        sb.append("private int _ttsUtteranceSeq = 0;").append(EOL);
        sb.append("private String _ttsEngine = null;").append(EOL);
        sb.append("private final java.util.ArrayList<String> _ttsTriedEngines = new java.util.ArrayList<String>();").append(EOL);
        sb.append("private boolean _ttsGivingUp = false;").append(EOL);
        sb.append("private final java.util.ArrayList<String> _ttsRejectedEngines = new java.util.ArrayList<String>();").append(EOL);
        sb.append("private boolean _ttsFinalFallbackStarted = false;").append(EOL);
        sb.append("private java.util.List<String> _ttsEnginePkgs = new java.util.ArrayList<String>();").append(EOL);
        sb.append("private String _ttsDefaultEngine = null;").append(EOL);
        sb.append("private int _ttsLangAvail = Integer.MIN_VALUE;").append(EOL);
        sb.append("private boolean _ttsLangSupported = false;").append(EOL);
        sb.append("private int _ttsSetLanguageResult = Integer.MIN_VALUE;").append(EOL);
        sb.append("private String _ttsLastEvent = \"init\";").append(EOL);
        sb.append("private int _ttsLastQueued = Integer.MIN_VALUE;").append(EOL);
        sb.append("private boolean _ttsNoVoiceWarned = false;").append(EOL);
        sb.append("private int _ttsBridgeSpeakCalls = 0;").append(EOL);
        sb.append("private int _ttsBridgeStopCalls = 0;").append(EOL);
        sb.append("private String _ttsBridgeLastText = \"\";").append(EOL);
        sb.append("private String _ttsBridgeLastRate = \"\";").append(EOL);
        sb.append("private boolean _ttsShowDiagToasts = ").append(diagLiteral).append("; ").append(WEBVIEW_TTS_DIAG_MARKER_PREFIX).append(diagLiteral).append(EOL);
        if (usePiper) {
            sb.append("private boolean _ttsUsePiper = true; ").append(WEBVIEW_TTS_PIPER_MARKER_PREFIX).append("true").append(EOL);
        }
        sb.append("private long _ttsLastDiagToastAt = 0L;").append(EOL);
        sb.append("private android.webkit.WebView _ttsWebView = null;").append(EOL);
        sb.append("private android.media.AudioManager _ttsAudioManager;").append(EOL);
        sb.append("private android.media.AudioManager.OnAudioFocusChangeListener _ttsFocusListener;").append(EOL);
        sb.append("private void _ttsRequestAudioFocus() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_ttsAudioManager == null) {").append(EOL);
        sb.append("_ttsAudioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_ttsAudioManager == null) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_ttsFocusListener == null) {").append(EOL);
        sb.append("_ttsFocusListener = new android.media.AudioManager.OnAudioFocusChangeListener() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onAudioFocusChange(int _focusChange) { }").append(EOL);
        sb.append("}; ").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsAudioManager.requestAudioFocus(_ttsFocusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);").append(EOL);
        sb.append("} catch (Throwable _ttsFocusError) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS requestAudioFocus error: \" + _ttsFocusError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("private void _ttsAbandonAudioFocus() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_ttsAudioManager != null && _ttsFocusListener != null) {").append(EOL);
        sb.append("_ttsAudioManager.abandonAudioFocus(_ttsFocusListener);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _ttsFocusReleaseError) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS abandonAudioFocus error: \" + _ttsFocusReleaseError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _initTts() {").append(EOL);
        sb.append("if (_ttsReady) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_ttsGivingUp) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS: ningún motor disponible, init ignorado\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        if (usePiper) {
            sb.append("if (_ttsUsePiper && _piperInit()) {").append(EOL);
            sb.append("_ttsReady = true;").append(EOL);
            sb.append("if (_ttsPendingText != null) {").append(EOL);
            sb.append("String _piperPendingText = _ttsPendingText;").append(EOL);
            sb.append("String _piperPendingRate = _ttsPendingRate;").append(EOL);
            sb.append("_ttsPendingText = null;").append(EOL);
            sb.append("_speakTtsNow(_piperPendingText, _piperPendingRate);").append(EOL);
            sb.append("}").append(EOL);
            sb.append("return;").append(EOL);
            sb.append("}").append(EOL);
        }
        sb.append("if (_tts == null) {").append(EOL);
        sb.append("_startTtsEngine(null, false);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _startTtsEngine(final String _enginePkg, final boolean _acceptLanguageFallback) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS intentando motor=\" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg) + \" acceptFallback=\" + _acceptLanguageFallback);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("TextToSpeech.OnInitListener _listener = new TextToSpeech.OnInitListener() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onInit(int _status) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS onInit status=\" + _status + \" motor=\" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg));").append(EOL);
        sb.append("if (_tts != null) {").append(EOL);
        sb.append("try { _ttsDefaultEngine = _tts.getDefaultEngine(); } catch (Throwable ignored) { }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_status != TextToSpeech.SUCCESS) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS onInit FAILED status=\" + _status + \" motor=\" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg));").append(EOL);
        sb.append("_ttsEngineFailed(_enginePkg);").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsEngine = _enginePkg;").append(EOL);
        sb.append("try { _ttsLangAvail = _tts != null ? _tts.isLanguageAvailable(_ttsRequestedLocale()) : Integer.MIN_VALUE; } catch (Throwable _ttsAvailError) { android.util.Log.e(\"AscodeTTS\", \"TTS isLanguageAvailable error: \" + _ttsAvailError); }").append(EOL);
        sb.append("boolean _supportsLang = _ttsLangAvail == TextToSpeech.LANG_AVAILABLE || _ttsLangAvail == TextToSpeech.LANG_COUNTRY_AVAILABLE || _ttsLangAvail == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS motor=\" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg) + \" isLanguageAvailable(").append(lang).append(")=\" + _ttsLangAvail);").append(EOL);
        sb.append("if (_supportsLang) {").append(EOL);
        sb.append("_onTtsReady(true);").append(EOL);
        sb.append("} else if (_acceptLanguageFallback) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS ning\u00fan motor con ").append(lang).append(": sigo con \" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg) + \" y locale de reserva\");").append(EOL);
        sb.append("_onTtsReady(false);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS motor=\" + (_enginePkg == null ? \"(por defecto)\" : _enginePkg) + \" no soporta ").append(lang).append(" (\" + _ttsLangAvail + \"), probando otro motor\");").append(EOL);
        sb.append("_ttsEngineRejected(_enginePkg);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("};").append(EOL);
        sb.append("if (_enginePkg == null) {").append(EOL);
        sb.append("_tts = new TextToSpeech(getApplicationContext(), _listener);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_tts = new TextToSpeech(getApplicationContext(), _listener, _enginePkg);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _ttsCreateError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS constructor error motor=\" + _enginePkg + \": \" + _ttsCreateError);").append(EOL);
        sb.append("_ttsEngineFailed(_enginePkg);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsEngineFailed(String _failedPkg) {").append(EOL);
        sb.append("_ttsRememberFailure(_failedPkg, _ttsTriedEngines);").append(EOL);
        sb.append("_ttsShutdownCurrent();").append(EOL);
        sb.append("String _next = _ttsNextEngine();").append(EOL);
        sb.append("if (_next == null) {").append(EOL);
        sb.append("_ttsStartFinalFallback();").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS fallback: reintentando con motor=\" + _next);").append(EOL);
        sb.append("_startTtsEngine(_next, false);").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsEngineRejected(String _rejectedPkg) {").append(EOL);
        sb.append("_ttsRememberFailure(_rejectedPkg, _ttsRejectedEngines);").append(EOL);
        sb.append("_ttsShutdownCurrent();").append(EOL);
        sb.append("String _next = _ttsNextEngine();").append(EOL);
        sb.append("if (_next == null) {").append(EOL);
        sb.append("_ttsStartFinalFallback();").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS motor sin el idioma: probando motor=\" + _next);").append(EOL);
        sb.append("_startTtsEngine(_next, false);").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsRememberFailure(String _pkg, java.util.ArrayList<String> _bucket) {").append(EOL);
        sb.append("String _name = _pkg;").append(EOL);
        sb.append("if (_name == null && _tts != null) {").append(EOL);
        sb.append("try { _name = _tts.getDefaultEngine(); } catch (Throwable ignored) { }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_name == null) { _name = \"<default>\"; }").append(EOL);
        sb.append("if (!_bucket.contains(_name)) { _bucket.add(_name); }").append(EOL);
        sb.append("_ttsRememberEngines();").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsShutdownCurrent() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_tts != null) {").append(EOL);
        sb.append("_tts.shutdown();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable ignored) {").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_tts = null;").append(EOL);
        sb.append("_ttsReady = false;").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsStartFinalFallback() {").append(EOL);
        sb.append("if (_ttsFinalFallbackStarted) {").append(EOL);
        sb.append("_ttsGivingUp = true;").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS: ning\u00fan motor disponible\");").append(EOL);
        sb.append("_ttsPendingText = null;").append(EOL);
        sb.append("_ttsEmitDiag(\"givingUp\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsFinalFallbackStarted = true;").append(EOL);
        sb.append("boolean _defaultFailed = _ttsTriedEngines.contains(\"<default>\") || (_ttsDefaultEngine != null && _ttsTriedEngines.contains(_ttsDefaultEngine));").append(EOL);
        sb.append("if (!_defaultFailed) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS sin datos del idioma en ning\u00fan motor: motor por defecto + locale de reserva\");").append(EOL);
        sb.append("_startTtsEngine(null, true);").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _recover = _ttsRejectedEngines.isEmpty() ? null : _ttsRejectedEngines.get(0);").append(EOL);
        sb.append("if (_recover != null) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS motor por defecto no arranc\u00f3: reutilizo motor=\" + _recover + \" con locale de reserva\");").append(EOL);
        sb.append("_startTtsEngine(_recover, true);").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsGivingUp = true;").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS: ning\u00fan motor disponible\");").append(EOL);
        sb.append("_ttsPendingText = null;").append(EOL);
        sb.append("_ttsEmitDiag(\"givingUp\");").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsRememberEngines() {").append(EOL);
        sb.append("if (!_ttsEnginePkgs.isEmpty()) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("java.util.ArrayList<String> _pkgs = new java.util.ArrayList<String>();").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_tts != null) {").append(EOL);
        sb.append("java.util.List<TextToSpeech.EngineInfo> _engines = _tts.getEngines();").append(EOL);
        sb.append("if (_engines != null) {").append(EOL);
        sb.append("for (TextToSpeech.EngineInfo _info : _engines) {").append(EOL);
        sb.append("if (_info != null && _info.name != null && !_pkgs.contains(_info.name)) {").append(EOL);
        sb.append("_pkgs.add(_info.name);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _enginesError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS getEngines error: \" + _enginesError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_pkgs.isEmpty()) {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("java.util.List<android.content.pm.ResolveInfo> _ris = getPackageManager().queryIntentServices(new android.content.Intent(\"android.intent.action.TTS_SERVICE\"), 0);").append(EOL);
        sb.append("if (_ris != null) {").append(EOL);
        sb.append("for (android.content.pm.ResolveInfo _ri : _ris) {").append(EOL);
        sb.append("if (_ri != null && _ri.serviceInfo != null && _ri.serviceInfo.packageName != null && !_pkgs.contains(_ri.serviceInfo.packageName)) {").append(EOL);
        sb.append("_pkgs.add(_ri.serviceInfo.packageName);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _pmError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS queryIntentServices error: \" + _pmError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (!_pkgs.isEmpty()) { _ttsEnginePkgs = _pkgs; }").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS motores instalados=\" + _ttsEnginePkgs);").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private String _ttsNextEngine() {").append(EOL);
        sb.append("_ttsRememberEngines();").append(EOL);
        sb.append("String _google = \"com.google.android.tts\";").append(EOL);
        sb.append("if (_ttsEnginePkgs.contains(_google) && !_ttsTriedEngines.contains(_google) && !_ttsRejectedEngines.contains(_google)) {").append(EOL);
        sb.append("return _google;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("for (String _pkg : _ttsEnginePkgs) {").append(EOL);
        sb.append("if (_pkg != null && !_ttsTriedEngines.contains(_pkg) && !_ttsRejectedEngines.contains(_pkg)) {").append(EOL);
        sb.append("return _pkg;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("return null;").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private boolean _ttsEngineInstalled(String _pkg) {").append(EOL);
        sb.append("_ttsRememberEngines();").append(EOL);
        sb.append("return _ttsEnginePkgs.contains(_pkg);").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _onTtsReady(boolean _languageSupported) {").append(EOL);
        sb.append("if (_tts == null) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsRememberEngines();").append(EOL);
        sb.append("_ttsLangSupported = _languageSupported;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS activo engine=\" + (_ttsEngine == null ? \"(por defecto)\" : _ttsEngine) + \" idiomaSoportado=\" + _languageSupported);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onStart(String _utteranceId) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS onStart id=\" + _utteranceId);").append(EOL);
        sb.append("_ttsEmitDiag(\"onStart\");").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onDone(String _utteranceId) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS onDone id=\" + _utteranceId);").append(EOL);
        sb.append("_ttsEmitDiag(\"onDone\");").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onError(String _utteranceId) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS onError id=\" + _utteranceId);").append(EOL);
        sb.append("_ttsEmitDiag(\"onError\");").append(EOL);
        sb.append("_ttsWarnNoVoice();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void onStop(String _utteranceId, boolean _interrupted) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS onStop id=\" + _utteranceId + \" interrupted=\" + _interrupted);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("Locale _ttsLocale = ").append(localeCode).append(";").append(EOL);
        sb.append("int _langResult;").append(EOL);
        sb.append("if (_languageSupported) {").append(EOL);
        sb.append("_langResult = _tts.setLanguage(_ttsLocale);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("Locale _ttsFallback = Locale.getDefault();").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS ").append(lang).append(" no disponible (\" + _ttsLangAvail + \"), fallback a \" + _ttsFallback);").append(EOL);
        sb.append("_langResult = _tts.setLanguage(_ttsFallback);").append(EOL);
        sb.append("if (_langResult == TextToSpeech.LANG_MISSING_DATA || _langResult == TextToSpeech.LANG_NOT_SUPPORTED) {").append(EOL);
        sb.append("_langResult = _tts.setLanguage(Locale.US);").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS fallback final Locale.US result=\" + _langResult);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsSetLanguageResult = _langResult;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS setLanguage result=\" + _langResult);").append(EOL);
        sb.append("if (_langResult == TextToSpeech.LANG_MISSING_DATA || _langResult == TextToSpeech.LANG_NOT_SUPPORTED) {").append(EOL);
        sb.append("_ttsWarnNoVoice();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("int _ttsRateResult = _tts.setSpeechRate(").append(rateLiteral).append(");").append(EOL);
        sb.append("int _ttsPitchResult = _tts.setPitch(1.0f);").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS setSpeechRate=").append(rate).append(" -> \" + _ttsRateResult + \", setPitch=1.0 -> \" + _ttsPitchResult);").append(EOL);
        sb.append("} catch (Throwable _ttsInitError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS init error: \" + _ttsInitError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsReady = true;").append(EOL);
        sb.append("_ttsEmitDiag(\"ready\");").append(EOL);
        sb.append("if (_ttsPendingText != null) {").append(EOL);
        sb.append("String _pendingText = _ttsPendingText;").append(EOL);
        sb.append("String _pendingRate = _ttsPendingRate;").append(EOL);
        sb.append("_ttsPendingText = null;").append(EOL);
        sb.append("_speakTtsNow(_pendingText, _pendingRate);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _speakTtsNow(String _text, String _rate) {").append(EOL);
        if (usePiper) {
            sb.append("if (_ttsUsePiper && _piperReady) {").append(EOL);
            sb.append("_piperSpeak(_text, _rate);").append(EOL);
            sb.append("return;").append(EOL);
            sb.append("}").append(EOL);
        }
        sb.append("if (_ttsGivingUp) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS speak ignorado: ningún motor disponible\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_tts == null || !_ttsReady) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS speak skipped ready=\" + _ttsReady + \" tts=\" + (_tts != null));").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("float _speechRate = ").append(rateLiteral).append(";").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_speechRate = Float.parseFloat(_rate);").append(EOL);
        sb.append("} catch (Throwable _rateError) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS rate parse failed for '\" + _rate + \"', usando ").append(rate).append("\");").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_speechRate <= 0f) {").append(EOL);
        sb.append("_speechRate = ").append(rateLiteral).append(";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (_speechRate > 2f) {").append(EOL);
        sb.append("_speechRate = 2f;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _speakText = (_text == null) ? \"\" : _text;").append(EOL);
        sb.append("if (_speakText.trim().isEmpty()) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS speak skipped: texto vacio\");").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("String _utteranceId = \"ascode_tts_\" + (++_ttsUtteranceSeq);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_tts.setSpeechRate(_speechRate);").append(EOL);
        sb.append("int _ttsQueued = _tts.speak(_speakText, TextToSpeech.QUEUE_FLUSH, null, _utteranceId);").append(EOL);
        sb.append("_ttsLastQueued = _ttsQueued;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS speak id=\" + _utteranceId + \" rate=\" + _speechRate + \" queued=\" + _ttsQueued + \" len=\" + _speakText.length());").append(EOL);
        sb.append("_ttsEmitDiag(\"speak\");").append(EOL);
        sb.append("if (_ttsQueued == TextToSpeech.ERROR) {").append(EOL);
        sb.append("_ttsWarnNoVoice();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable _ttsSpeakError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS speak error: \" + _ttsSpeakError);").append(EOL);
        sb.append("_ttsEmitDiag(\"speakError\");").append(EOL);
        sb.append("_ttsWarnNoVoice();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private Locale _ttsRequestedLocale() {").append(EOL);
        sb.append("return ").append(localeCode).append(";").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private int _ttsVoiceCount() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_tts != null) {").append(EOL);
        sb.append("java.util.Set _voices = _tts.getVoices();").append(EOL);
        sb.append("return _voices == null ? 0 : _voices.size();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable ignored) { }").append(EOL);
        sb.append("return 0;").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsWarnNoVoice() {").append(EOL);
        sb.append("if (_ttsNoVoiceWarned) { return; }").append(EOL);
        sb.append("_ttsNoVoiceWarned = true;").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS sin voz para ").append(lang).append(": avisando al usuario\");").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("final android.content.Context _ttsToastContext = getApplicationContext();").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("android.widget.Toast.makeText(_ttsToastContext, \"No hay voz para ").append(lang).append(" instalada. Ajustes > Texto a voz > Instalar datos de voz\", android.widget.Toast.LENGTH_LONG).show();").append(EOL);
        sb.append("} catch (Throwable ignored) { }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("} catch (Throwable ignored) { }").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private String _ttsDiagnostics() {").append(EOL);
        sb.append("StringBuilder _d = new StringBuilder(192);").append(EOL);
        sb.append("_d.append(\"engine=\").append(_ttsEngine == null ? \"(default)\" : _ttsEngine);").append(EOL);
        sb.append("_d.append(\", lang=").append(lang).append("\");").append(EOL);
        sb.append("_d.append(\", requestedAvail=\").append(_ttsLangAvail);").append(EOL);
        sb.append("_d.append(\", languageSupported=\").append(_ttsLangSupported);").append(EOL);
        sb.append("_d.append(\", setLanguage=\").append(_ttsSetLanguageResult);").append(EOL);
        sb.append("_d.append(\", engines=\").append(_ttsEnginePkgs.size());").append(EOL);
        sb.append("_d.append(\", voices=\").append(_ttsVoiceCount());").append(EOL);
        sb.append("_d.append(\", ready=\").append(_ttsReady);").append(EOL);
        sb.append("_d.append(\", last=\").append(_ttsLastEvent);").append(EOL);
        sb.append("_d.append(\", queued=\").append(_ttsLastQueued);").append(EOL);
        sb.append("_d.append(\", noVoiceWarned=\").append(_ttsNoVoiceWarned);").append(EOL);
        sb.append("_d.append(\", speakCalls=\").append(_ttsBridgeSpeakCalls);").append(EOL);
        sb.append("_d.append(\", stopCalls=\").append(_ttsBridgeStopCalls);").append(EOL);
        sb.append("_d.append(\", lastRate=\").append(_ttsBridgeLastRate);").append(EOL);
        sb.append("_d.append(\", lastText=\").append(_ttsBridgeLastText);").append(EOL);
        sb.append("return _d.toString();").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsMaybeDiagToast() {").append(EOL);
        sb.append("if (!_ttsShowDiagToasts) { return; }").append(EOL);
        sb.append("long _diagNow = android.os.SystemClock.elapsedRealtime();").append(EOL);
        sb.append("if (_diagNow - _ttsLastDiagToastAt < 800L) { return; }").append(EOL);
        sb.append("_ttsLastDiagToastAt = _diagNow;").append(EOL);
        sb.append("final android.content.Context _diagToastContext = getApplicationContext();").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("android.widget.Toast.makeText(_diagToastContext, _ttsDiagnostics(), android.widget.Toast.LENGTH_SHORT).show();").append(EOL);
        sb.append("} catch (Throwable ignored) { }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _ttsEmitDiag(String _event) {").append(EOL);
        sb.append("_ttsLastEvent = _event;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"DIAG event=\" + _event + \" \" + _ttsDiagnostics());").append(EOL);
        sb.append("_ttsMaybeDiagToast();").append(EOL);
        sb.append("final android.webkit.WebView _diagWebView = _ttsWebView;").append(EOL);
        sb.append("if (_diagWebView == null) { return; }").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("final String _diagJson = \"{\\\"event\\\":\\\"\" + _event + \"\\\",\\\"engine\\\":\\\"\" + (_ttsEngine == null ? \"default\" : _ttsEngine) + \"\\\",\\\"lang\\\":\\\"").append(lang).append("\\\",\\\"avail\\\":\" + _ttsLangAvail + \",\\\"supported\\\":\" + _ttsLangSupported + \",\\\"setLanguage\\\":\" + _ttsSetLanguageResult + \",\\\"engines\\\":\" + _ttsEnginePkgs.size() + \",\\\"voices\\\":\" + _ttsVoiceCount() + \",\\\"ready\\\":\" + _ttsReady + \",\\\"queued\\\":\" + _ttsLastQueued + \"}\";").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_diagWebView.evaluateJavascript(\"window.__ascodeTtsDiag&&window.__ascodeTtsDiag(\" + _diagJson + \");\", null);").append(EOL);
        sb.append("} catch (Throwable _diagError) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS diag emit error: \" + _diagError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("} catch (Throwable _diagError) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"TTS diag emit error: \" + _diagError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append("private void _stopTts() {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"TTS stop()\");").append(EOL);
        if (usePiper) {
            sb.append("if (_ttsUsePiper && _piperReady) { _piperStop(); }").append(EOL);
        }
        sb.append("_ttsEmitDiag(\"stop\");").append(EOL);
        sb.append("_ttsAbandonAudioFocus();").append(EOL);
        sb.append("if (_tts != null) {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("_tts.stop();").append(EOL);
        sb.append("} catch (Throwable _ttsStopError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"TTS stop error: \" + _ttsStopError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append(EOL);
        if (usePiper) {
            appendWebViewPiperTtsEngine(sb, EOL);
        }
        sb.append("private void _injectTtsShim(WebView _webView) {").append(EOL);
        sb.append("if (_webView == null) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsWebView = _webView;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"SHIM inject on \" + _webView.getUrl());").append(EOL);
        sb.append("_ttsEmitDiag(\"shim\");").append(EOL);
        sb.append("String _shim = \"(function(){\"").append(EOL);
        sb.append("+ \"if(window.__ascodeTtsShim){return;}\"").append(EOL);
        sb.append("+ \"window.__ascodeTtsShim=true;\"").append(EOL);
        sb.append("+ \"function _Utterance(t){this.text=(t===undefined||t===null)?'':String(t);this.lang='").append(lang).append("';this.rate=").append(rate).append(";this.pitch=1;this.volume=1;this.voice=null;this.onstart=null;this.onend=null;this.onerror=null;}\"").append(EOL);
        sb.append("+ \"if(typeof window.SpeechSynthesisUtterance==='undefined'){window.SpeechSynthesisUtterance=_Utterance;}\"").append(EOL);
        sb.append("+ \"var _shimNeeded=(typeof window.speechSynthesis==='undefined');\"").append(EOL);
        sb.append("+ \"if(_shimNeeded){\"").append(EOL);
        sb.append("+ \"var _s={speaking:false,paused:false,onvoiceschanged:null,voices:[{name:'AndroidBridge TTS',lang:'").append(lang).append("',default:true,localService:true,voiceURI:'AndroidBridge'}],\"").append(EOL);
        sb.append("+ \"getVoices:function(){return this.voices;},\"").append(EOL);
        sb.append("+ \"speak:function(u){\"").append(EOL);
        sb.append("+ \"var t=(u&&typeof u==='object')?u.text:u;t=(t===undefined||t===null)?'':String(t);\"").append(EOL);
        sb.append("+ \"var r=(u&&typeof u==='object'&&u.rate)?String(u.rate):'").append(rate).append("';\"").append(EOL);
        sb.append("+ \"this.speaking=true;\"").append(EOL);
        sb.append("+ \"try{if(typeof u.onstart==='function')u.onstart();}catch(e){}\"").append(EOL);
        sb.append("+ \"try{var _b=window.AndroidBridge;if(_b&&typeof _b.speak==='function'){_b.speak(t,r);}}catch(e){}\"").append(EOL);
        sb.append("+ \"var _self=this;var _dur=Math.min(28000,Math.max(900,t.length*65));\"").append(EOL);
        sb.append("+ \"setTimeout(function(){_self.speaking=false;try{if(typeof u.onend==='function')u.onend();}catch(e){}}, _dur);\"").append(EOL);
        sb.append("+ \"},\"").append(EOL);
        sb.append("+ \"cancel:function(){this.speaking=false;try{var _b2=window.AndroidBridge;if(_b2&&typeof _b2.stop==='function'){_b2.stop();}}catch(e){}},\"").append(EOL);
        sb.append("+ \"pause:function(){this.paused=true;},resume:function(){this.paused=false;}};\"").append(EOL);
        sb.append("+ \"window.speechSynthesis=_s;\"").append(EOL);
        if (showDiagnosticsToasts) {
            sb.append("+ \"window.__ascodeTtsShimInstalled=true;\"").append(EOL);
        }
        sb.append("+ \"try{if(typeof _s.onvoiceschanged==='function'){setTimeout(function(){try{_s.onvoiceschanged();}catch(e){}},350);}}catch(e){}\"").append(EOL);
        sb.append("+ \"}\"").append(EOL);
        sb.append("+ \"})();\";").append(EOL);
        sb.append("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {").append(EOL);
        sb.append("_webView.evaluateJavascript(_shim, null);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_webView.loadUrl(\"javascript:\" + _shim);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        if (showDiagnosticsToasts) {
            appendWebViewTtsDiagnosticsOverlay(sb, EOL);
        }

        sb.append(EOL);
        sb.append("private class _TtsBridge {").append(EOL);
        sb.append("@JavascriptInterface").append(EOL);
        sb.append("public String getDiagnostics() {").append(EOL);
        sb.append("return _ttsDiagnostics();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@JavascriptInterface").append(EOL);
        sb.append("public void speak(final String _text, final String _rate) {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"BRIDGE speak len=\" + (_text == null ? 0 : _text.length()) + \" rate=\" + _rate);").append(EOL);
        sb.append("_ttsBridgeSpeakCalls++; _ttsBridgeLastText = (_text == null) ? \"\" : _text; _ttsBridgeLastRate = (_rate == null) ? \"\" : _rate;").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("_initTts();").append(EOL);
        sb.append("if (_ttsReady) {").append(EOL);
        sb.append("_speakTtsNow(_text, _rate);").append(EOL);
        sb.append("} else if (_ttsGivingUp) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"BRIDGE speak ignorado: ningún motor disponible\");").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_ttsPendingText = _text;").append(EOL);
        sb.append("_ttsPendingRate = _rate;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@JavascriptInterface").append(EOL);
        sb.append("public void stop() {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"BRIDGE stop()\");").append(EOL);
        sb.append("_ttsBridgeStopCalls++;").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("_stopTts();").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("}").append(EOL);
        sb.append("@JavascriptInterface").append(EOL);
        sb.append("public void probe() {").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"BRIDGE probe()\");").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("_initTts();").append(EOL);
        sb.append("if (_ttsReady) {").append(EOL);
        sb.append("_speakTtsNow(\"Hola, prueba de voz\", _ttsPendingRate);").append(EOL);
        sb.append("} else if (_ttsGivingUp) {").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"BRIDGE probe ignorado: ningún motor disponible\");").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_ttsPendingText = \"Hola, prueba de voz\";").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("});").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append(WEBVIEW_TTS_HELPER_END_MARKER).append(EOL);
    }

    /**
     * Emits the optional on-screen diagnostics overlay used when the project enabled the TTS
     * diagnostics switch. The IDE injects it after each page load (see
     * {@code _injectTtsDiagnosticsOverlay(WebView)}); it renders a small, semi-transparent,
     * non-interactive panel at the top of the page (only its &quot;Probar voz&quot; button
     * receives touches) showing whether {@code window.AndroidBridge} exists and the
     * {@code typeof speak}/{@code stop} the HTML would see, the shim state, the Java diagnostics
     * (engine, language, {@code isLanguageAvailable}, {@code setLanguage} result, {@code queued},
     * voices, last event) plus the bridge {@code speak()}/{@code stop()} call counters exposed
     * through {@code getDiagnostics()}, and the first captured page JavaScript errors
     * ({@code window.onerror} + {@code console.error}). Its button calls
     * {@code AndroidBridge.probe()}, which drives the engine directly (bundled Piper when active,
     * otherwise the system engine) with a fixed phrase, bypassing the page, so a broken engine can
     * be told apart from a page that never calls the bridge.
     *
     * <p>Only emitted when {@code showDiagnosticsToasts} is {@code true}: with the switch off the
     * generated helper stays behaviourally identical to the previous version.</p>
     */
    private static void appendWebViewTtsDiagnosticsOverlay(StringBuilder sb, String EOL) {
        sb.append(EOL);
        sb.append("private void _injectTtsDiagnosticsOverlay(WebView _webView) {").append(EOL);
        sb.append("if (_webView == null) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("if (!_ttsShowDiagToasts) {").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsWebView = _webView;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"OVERLAY inject on \" + _webView.getUrl());").append(EOL);
        sb.append("String _overlayJs = \"(function(){\"").append(EOL);
        sb.append("+ \"if(window.__ascodeTtsOverlayInjected){if(window.__ascodeTtsOverlayEnsure){window.__ascodeTtsOverlayEnsure();}return;}\"").append(EOL);
        sb.append("+ \"window.__ascodeTtsOverlayInjected=true;\"").append(EOL);
        sb.append("+ \"var _ID='__ascodeTtsOverlay';var _MAX=3;var _errs=[];var _diag='';var _evt='init';var _panel=null;\"").append(EOL);
        sb.append("+ \"function _txt(id,value){var _n=document.getElementById(_ID+id);if(_n){_n.textContent=value;}}\"").append(EOL);
        sb.append("+ \"function _pushError(msg){try{_errs.push(String(msg));if(_errs.length>_MAX){_errs.shift();}_update();}catch(_e){}}\"").append(EOL);
        sb.append("+ \"try{var _prevOnError=window.onerror;window.onerror=function(_m,_u,_l,_c,_e){_pushError('error: '+_m+' @'+_l+':'+_c);if(typeof _prevOnError==='function'){try{_prevOnError(_m,_u,_l,_c,_e);}catch(_x){}}return false;};}catch(_e){}\"").append(EOL);
        sb.append("+ \"try{var _prevConsoleError=console.error;console.error=function(){try{_pushError('console: '+Array.prototype.join.call(arguments,' '));}catch(_e){}if(typeof _prevConsoleError==='function'){_prevConsoleError.apply(console,arguments);}};}catch(_e){}\"").append(EOL);
        sb.append("+ \"window.__ascodeTtsDiag=function(_json){try{if(_json&&_json.event){_evt=_json.event;}}catch(_e){}_update();};\"").append(EOL);
        sb.append("+ \"function _mk(tag,styles){var _n=document.createElement(tag);if(styles){for(var _k in styles){try{_n.style[_k]=styles[_k];}catch(_e){}}}return _n;}\"").append(EOL);
        sb.append("+ \"function _build(){if(_panel&&_panel.parentNode){return;}\"").append(EOL);
        sb.append("+ \"_panel=_mk('div',{position:'fixed',left:'4px',right:'4px',top:'4px',zIndex:'2147483647',background:'rgba(0,0,0,0.60)',color:'#ffffff',fontFamily:'monospace',fontSize:'10px',lineHeight:'1.3',padding:'5px 7px',borderRadius:'6px',pointerEvents:'none',whiteSpace:'pre-wrap',wordBreak:'break-word',textShadow:'0 1px 2px #000000'});\"").append(EOL);
        sb.append("+ \"_panel.id=_ID;\"").append(EOL);
        sb.append("+ \"var _title=_mk('div',{fontWeight:'bold',color:'#88ffff'});_title.textContent='TTS diag (IDE)';_panel.appendChild(_title);\"").append(EOL);
        sb.append("+ \"var _l1=_mk('div');_l1.id=_ID+'b';_panel.appendChild(_l1);\"").append(EOL);
        sb.append("+ \"var _l2=_mk('div');_l2.id=_ID+'s';_panel.appendChild(_l2);\"").append(EOL);
        sb.append("+ \"var _l3=_mk('div');_l3.id=_ID+'c';_panel.appendChild(_l3);\"").append(EOL);
        sb.append("+ \"var _l4=_mk('div');_l4.id=_ID+'j';_panel.appendChild(_l4);\"").append(EOL);
        sb.append("+ \"var _l5=_mk('div',{color:'#ff9999'});_l5.id=_ID+'e';_panel.appendChild(_l5);\"").append(EOL);
        sb.append("+ \"var _btn=_mk('button',{pointerEvents:'auto',marginTop:'4px',fontSize:'10px',padding:'2px 8px'});_btn.id=_ID+'p';_btn.textContent='Probar voz';\"").append(EOL);
        sb.append("+ \"_btn.onclick=function(){try{window.AndroidBridge.probe();}catch(_e){_pushError('probe: '+_e);}};\"").append(EOL);
        sb.append("+ \"_panel.appendChild(_btn);if(document.body){document.body.appendChild(_panel);}}\"").append(EOL);
        sb.append("+ \"function _update(){_build();\"").append(EOL);
        sb.append("+ \"var _b=window.AndroidBridge;var _has=!!_b;\"").append(EOL);
        sb.append("+ \"_txt('b','AndroidBridge: '+(_has?'SI':'NO')+' | typeof speak: '+(_has?(typeof _b.speak):'n/a')+' | typeof stop: '+(_has?(typeof _b.stop):'n/a'));\"").append(EOL);
        sb.append("+ \"var _inst=!!window.__ascodeTtsShimInstalled;var _synth=_inst?'shim (IDE)':(typeof window.speechSynthesis==='undefined'?'ninguno':'nativo');\"").append(EOL);
        sb.append("+ \"_txt('s','__ascodeTtsShim: '+(window.__ascodeTtsShim?'si':'no')+' | speechSynthesis: '+_synth);\"").append(EOL);
        sb.append("+ \"if(_b&&typeof _b.getDiagnostics==='function'){try{_diag=String(_b.getDiagnostics());}catch(_e){_diag='getDiagnostics error: '+_e;}}\"").append(EOL);
        sb.append("+ \"_txt('j','java: '+_diag);\"").append(EOL);
        sb.append("+ \"var _sc=/speakCalls=([^,]*)/.exec(_diag);var _stc=/stopCalls=([^,]*)/.exec(_diag);var _lr=/lastRate=([^,]*)/.exec(_diag);var _lt=/lastText=(.*)$/.exec(_diag);\"").append(EOL);
        sb.append("+ \"_txt('c','speak='+(_sc?_sc[1]:'?')+' stop='+(_stc?_stc[1]:'?')+' rate='+(_lr?_lr[1]:'?')+' text='+(_lt?_lt[1]:''));\"").append(EOL);
        sb.append("+ \"_txt('e','last event: '+_evt+(_errs.length?(' | err: '+_errs.join(' ; ')):' | err: (ninguno)'));}\"").append(EOL);
        sb.append("+ \"window.__ascodeTtsOverlayEnsure=function(){if(!document.getElementById(_ID)){_panel=null;_build();_update();}};\"").append(EOL);
        sb.append("+ \"if(document.body){_update();}else{document.addEventListener('DOMContentLoaded',function(){_update();});}\"").append(EOL);
        sb.append("+ \"setInterval(function(){if(!document.getElementById(_ID)){_panel=null;_build();_update();}else{_update();}},1000);\"").append(EOL);
        sb.append("+ \"})();\";").append(EOL);
        sb.append("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {").append(EOL);
        sb.append("_webView.evaluateJavascript(_overlayJs, null);").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_webView.loadUrl(\"javascript:\" + _overlayJs);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_ttsEmitDiag(\"overlay\");").append(EOL);
        sb.append("}").append(EOL);
    }

    /**
     * @return {@code true} when {@code javaCode} already contains the current version of the
     * WebView TTS helper: it carries the version marker of the current
     * {@link #WEBVIEW_TTS_HELPER_VERSION}.
     */
    public static boolean hasCurrentWebViewTtsHelper(String javaCode) {
        return javaCode != null
                && javaCode.contains(WEBVIEW_TTS_HELPER_BEGIN_MARKER);
    }

    /**
     * @return {@code true} when {@code javaCode} carries a helper whose baked-in diagnostics flag
     * equals {@code enabled} (matched through {@link #WEBVIEW_TTS_DIAG_MARKER_PREFIX}). Used by
     * {@code yq} to re-emit the helper when the project's switch was toggled even though the
     * helper version did not change.
     */
    public static boolean hasWebViewTtsDiagnosticsSetting(String javaCode, boolean enabled) {
        return javaCode != null
                && javaCode.contains(WEBVIEW_TTS_DIAG_MARKER_PREFIX + enabled);
    }

    /**
     * @return {@code true} when {@code javaCode}'s baked-in Piper-engine flag equals {@code enabled}
     * (matched through {@link #WEBVIEW_TTS_PIPER_MARKER_PREFIX}; an absent marker means the system
     * engine). Used by {@code yq} to re-emit the helper when the project's switch was toggled even
     * though the helper version did not change.
     */
    public static boolean hasWebViewTtsPiperSetting(String javaCode, boolean enabled) {
        if (javaCode == null) {
            return false;
        }
        boolean bakedOn = javaCode.contains(WEBVIEW_TTS_PIPER_MARKER_PREFIX + "true");
        return enabled == bakedOn;
    }

    /**
     * @return {@code true} when {@code javaCode} carries a WebView TTS helper that is not the
     * current version (for example one injected by an older release, which lacks the version
     * marker / {@code setAudioAttributes}), so {@code yq} knows it must upgrade it in place.
     */
    public static boolean hasLegacyWebViewTtsHelper(String javaCode) {
        if (javaCode == null || javaCode.isEmpty() || hasCurrentWebViewTtsHelper(javaCode)) {
            return false;
        }
        return javaCode.contains("\"AndroidBridge\"")
                || javaCode.contains("private class _TtsBridge")
                || javaCode.contains("TextToSpeech _tts;");
    }

    /**
     * Replaces an outdated WebView TTS helper block (fields + speechSynthesis shim +
     * {@code _TtsBridge}) found in {@code javaCode} with the current one emitted by
     * {@link #appendWebViewTtsHelpers(StringBuilder, String)}, leaving every other line of the
     * file untouched. Used by {@code yq} to upgrade projects that were patched by an older
     * release, as a surgical alternative to regenerating the whole activity (which would drop the
     * user's customizations).
     * <p>
     * The legacy block starts at the helper's first field declaration ({@code TextToSpeech _tts;})
     * and ends at the closing brace of the {@code _TtsBridge} inner class, so any code that follows
     * the helper (such as {@code onActivityResult}) is preserved.
     *
     * @return the upgraded source, or {@code null} when the block could not be located
     */
    public static String replaceLegacyWebViewTtsHelper(String javaCode, String EOL) {
        return replaceLegacyWebViewTtsHelper(javaCode, EOL,
                ProjectWebViewSettings.DEFAULT_TTS_RATE, ProjectWebViewSettings.DEFAULT_TTS_LANG, false);
    }

    /**
     * Same as {@link #replaceLegacyWebViewTtsHelper(String, String)} but re-emitting the helper
     * with the project's configured speech rate, language and diagnostic-toast flag, so an upgrade
     * never silently drops those values.
     */
    public static String replaceLegacyWebViewTtsHelper(String javaCode, String EOL, String defaultRate,
                                                       String defaultLang, boolean showDiagnosticsToasts) {
        return replaceLegacyWebViewTtsHelper(javaCode, EOL, defaultRate, defaultLang, showDiagnosticsToasts, false);
    }

    /**
     * Same as {@link #replaceLegacyWebViewTtsHelper(String, String, String, String, boolean)} but
     * also choosing the synthesis backend (see
     * {@link #appendWebViewTtsHelpers(StringBuilder, String, String, String, boolean, boolean)}), so
     * an in-place upgrade never silently drops the offline Piper route either.
     */
    public static String replaceLegacyWebViewTtsHelper(String javaCode, String EOL, String defaultRate,
                                                       String defaultLang, boolean showDiagnosticsToasts,
                                                       boolean usePiper) {
        if (javaCode == null || javaCode.isEmpty()) {
            return null;
        }
        int helperFieldIndex = javaCode.indexOf("TextToSpeech _tts;");
        if (helperFieldIndex < 0) {
            return null;
        }
        int helperStart = javaCode.lastIndexOf('\n', helperFieldIndex) + 1;

        int bridgeClassIndex = javaCode.indexOf("class _TtsBridge");
        if (bridgeClassIndex < 0) {
            return null;
        }
        int braceOpenIndex = javaCode.indexOf('{', bridgeClassIndex);
        if (braceOpenIndex < 0) {
            return null;
        }
        int blockEndIndex = findMatchingBrace(javaCode, braceOpenIndex);
        if (blockEndIndex < 0) {
            return null;
        }
        int replaceEnd = blockEndIndex + 1;

        StringBuilder helpersBuilder = new StringBuilder(8192);
        appendWebViewTtsHelpers(helpersBuilder, EOL, defaultRate, defaultLang, showDiagnosticsToasts, usePiper);
        String newHelper = helpersBuilder.toString();
        if (newHelper.startsWith(EOL)) {
            newHelper = newHelper.substring(EOL.length());
        }
        return javaCode.substring(0, helperStart) + newHelper + javaCode.substring(replaceEnd);
    }

    /**
     * Emits the optional, fully-offline Piper engine used by the WebView TTS helper when the
     * project enabled it. It is additive: the helper still carries the complete system
     * {@code TextToSpeech} implementation, and this block only makes it <em>prefer</em> the bundled
     * voice (fields {@code _piper*} + {@code _piperInit()/_piperSpeak()/_piperStop()} invoked
     * conditionally from {@code _initTts()}, {@code _speakTtsNow()} and {@code _stopTts()}).
     *
     * <p>The model is read from {@code assets/piper/model.onnx}, {@code assets/piper/tokens.txt} and
     * {@code assets/piper/espeak-ng-data/}; sherpa-onnx needs the espeak-ng-data directory as a real
     * filesystem path, so it is copied into {@code getFilesDir()} on first use. If anything fails
     * (missing assets, unsupported ABI, synthesis error) the code disables itself and the system
     * engine takes over.</p>
     */
    private static void appendWebViewPiperTtsEngine(StringBuilder sb, String EOL) {
        sb.append(EOL);
        sb.append("private com.k2fsa.sherpa.onnx.OfflineTts _piperTts = null;").append(EOL);
        sb.append("private boolean _piperReady = false;").append(EOL);
        sb.append("private boolean _piperFailed = false;").append(EOL);
        sb.append("private boolean _piperDataDirReady = false;").append(EOL);
        sb.append("private android.media.AudioTrack _piperTrack = null;").append(EOL);
        sb.append("private final Object _piperLock = new Object();").append(EOL);

        sb.append(EOL);
        sb.append("private boolean _piperInit() {").append(EOL);
        sb.append("synchronized (_piperLock) {").append(EOL);
        sb.append("if (_piperReady) { return true; }").append(EOL);
        sb.append("if (_piperFailed) { return false; }").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("String _piperDataDir = _piperEnsureDataDir();").append(EOL);
        sb.append("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig _piperVits = new com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig();").append(EOL);
        sb.append("_piperVits.setModel(\"piper/model.onnx\");").append(EOL);
        sb.append("_piperVits.setTokens(\"piper/tokens.txt\");").append(EOL);
        sb.append("_piperVits.setDataDir(_piperDataDir);").append(EOL);
        sb.append("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig _piperModel = new com.k2fsa.sherpa.onnx.OfflineTtsModelConfig();").append(EOL);
        sb.append("_piperModel.setVits(_piperVits);").append(EOL);
        sb.append("_piperModel.setNumThreads(2);").append(EOL);
        sb.append("_piperModel.setDebug(false);").append(EOL);
        sb.append("_piperModel.setProvider(\"cpu\");").append(EOL);
        sb.append("com.k2fsa.sherpa.onnx.OfflineTtsConfig _piperConfig = new com.k2fsa.sherpa.onnx.OfflineTtsConfig();").append(EOL);
        sb.append("_piperConfig.setModel(_piperModel);").append(EOL);
        sb.append("_piperConfig.setMaxNumSentences(1);").append(EOL);
        sb.append("_piperTts = new com.k2fsa.sherpa.onnx.OfflineTts(getAssets(), _piperConfig);").append(EOL);
        sb.append("_piperReady = true;").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"PIPER listo sampleRate=\" + _piperTts.sampleRate());").append(EOL);
        sb.append("_ttsEmitDiag(\"piperReady\");").append(EOL);
        sb.append("return true;").append(EOL);
        sb.append("} catch (Throwable _piperInitError) {").append(EOL);
        sb.append("_piperFailed = true;").append(EOL);
        sb.append("_piperTts = null;").append(EOL);
        sb.append("android.util.Log.w(\"AscodeTTS\", \"PIPER no disponible, uso el motor del sistema: \" + _piperInitError);").append(EOL);
        sb.append("_ttsEmitDiag(\"piperUnavailable\");").append(EOL);
        sb.append("return false;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private String _piperEnsureDataDir() throws java.io.IOException {").append(EOL);
        sb.append("java.io.File _piperDir = new java.io.File(getFilesDir(), \"piper\");").append(EOL);
        sb.append("java.io.File _piperEspeak = new java.io.File(_piperDir, \"espeak-ng-data\");").append(EOL);
        sb.append("if (!_piperDataDirReady || !_piperEspeak.isDirectory()) {").append(EOL);
        sb.append("_piperCopyAssetDir(\"piper/espeak-ng-data\", _piperEspeak);").append(EOL);
        sb.append("_piperDataDirReady = true;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("return _piperEspeak.getAbsolutePath();").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _piperCopyAssetDir(String _assetPath, java.io.File _piperDest) throws java.io.IOException {").append(EOL);
        sb.append("String[] _piperChildren = getAssets().list(_assetPath);").append(EOL);
        sb.append("if (_piperChildren == null || _piperChildren.length == 0) {").append(EOL);
        sb.append("java.io.File _piperParent = _piperDest.getParentFile();").append(EOL);
        sb.append("if (_piperParent != null) { _piperParent.mkdirs(); }").append(EOL);
        sb.append("java.io.InputStream _piperIn = getAssets().open(_assetPath);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("java.io.FileOutputStream _piperOut = new java.io.FileOutputStream(_piperDest);").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("byte[] _piperBuf = new byte[16384];").append(EOL);
        sb.append("int _piperRead; while ((_piperRead = _piperIn.read(_piperBuf)) > 0) { _piperOut.write(_piperBuf, 0, _piperRead); }").append(EOL);
        sb.append("} finally { _piperOut.close(); }").append(EOL);
        sb.append("} finally { _piperIn.close(); }").append(EOL);
        sb.append("return;").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_piperDest.mkdirs();").append(EOL);
        sb.append("for (String _piperChild : _piperChildren) { _piperCopyAssetDir(_assetPath + \"/\" + _piperChild, new java.io.File(_piperDest, _piperChild)); }").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _piperSpeak(final String _text, final String _rate) {").append(EOL);
        sb.append("final String _piperText = (_text == null) ? \"\" : _text;").append(EOL);
        sb.append("if (_piperText.trim().isEmpty()) { return; }").append(EOL);
        sb.append("float _piperRate = 1.0f;").append(EOL);
        sb.append("try { _piperRate = Float.parseFloat(_rate); } catch (Throwable _piperRateError) { _piperRate = 1.0f; }").append(EOL);
        sb.append("if (_piperRate <= 0f) { _piperRate = 1.0f; }").append(EOL);
        sb.append("if (_piperRate > 2f) { _piperRate = 2f; }").append(EOL);
        sb.append("final float _piperFinalRate = _piperRate;").append(EOL);
        sb.append("_ttsRequestAudioFocus();").append(EOL);
        sb.append("new Thread(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_piperTts == null && !_piperInit()) { _piperFallbackToSystem(_piperText, _rate); return; }").append(EOL);
        sb.append("long _piperStart = android.os.SystemClock.elapsedRealtime();").append(EOL);
        sb.append("com.k2fsa.sherpa.onnx.GeneratedAudio _piperAudio = _piperTts.generate(_piperText, 0, _piperFinalRate);").append(EOL);
        sb.append("float[] _piperSamples = _piperAudio.getSamples();").append(EOL);
        sb.append("int _piperSampleRate = _piperAudio.getSampleRate();").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"PIPER synth ms=\" + (android.os.SystemClock.elapsedRealtime() - _piperStart) + \" samples=\" + _piperSamples.length + \" rate=\" + _piperSampleRate);").append(EOL);
        sb.append("_ttsEmitDiag(\"piperSynth\");").append(EOL);
        sb.append("_piperPlay(_piperSamples, _piperSampleRate);").append(EOL);
        sb.append("} catch (Throwable _piperSpeakError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"PIPER synth error: \" + _piperSpeakError);").append(EOL);
        sb.append("_piperFailed = true;").append(EOL);
        sb.append("_piperFallbackToSystem(_piperText, _rate);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}, \"ascode-piper-tts\").start();").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _piperFallbackToSystem(final String _text, final String _rate) {").append(EOL);
        sb.append("new Handler(Looper.getMainLooper()).post(new Runnable() {").append(EOL);
        sb.append("@Override").append(EOL);
        sb.append("public void run() {").append(EOL);
        sb.append("_ttsUsePiper = false;").append(EOL);
        sb.append("_initTts();").append(EOL);
        sb.append("if (_ttsReady) { _speakTtsNow(_text, _rate); }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("})").append(EOL);
        sb.append(";}").append(EOL);

        sb.append(EOL);
        sb.append("private void _piperPlay(float[] _piperSamples, int _piperSampleRate) {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("short[] _piperPcm = new short[_piperSamples.length];").append(EOL);
        sb.append("for (int _piperI = 0; _piperI < _piperSamples.length; _piperI++) {").append(EOL);
        sb.append("float _piperValue = _piperSamples[_piperI];").append(EOL);
        sb.append("if (_piperValue > 1f) { _piperValue = 1f; } else if (_piperValue < -1f) { _piperValue = -1f; }").append(EOL);
        sb.append("_piperPcm[_piperI] = (short) (_piperValue * 32767f);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("int _piperBytes = _piperPcm.length * 2;").append(EOL);
        sb.append("int _piperMinBuf = android.media.AudioTrack.getMinBufferSize(_piperSampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT);").append(EOL);
        sb.append("android.media.AudioTrack _piperNewTrack;").append(EOL);
        sb.append("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {").append(EOL);
        sb.append("_piperNewTrack = new android.media.AudioTrack.Builder()").append(EOL);
        sb.append(".setAudioAttributes(new android.media.AudioAttributes.Builder()").append(EOL);
        sb.append(".setUsage(android.media.AudioAttributes.USAGE_MEDIA)").append(EOL);
        sb.append(".setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())").append(EOL);
        sb.append(".setAudioFormat(new android.media.AudioFormat.Builder()").append(EOL);
        sb.append(".setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)").append(EOL);
        sb.append(".setSampleRate(_piperSampleRate)").append(EOL);
        sb.append(".setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())").append(EOL);
        sb.append(".setBufferSizeInBytes(Math.max(_piperBytes, _piperMinBuf))").append(EOL);
        sb.append(".setTransferMode(android.media.AudioTrack.MODE_STATIC).build();").append(EOL);
        sb.append("} else {").append(EOL);
        sb.append("_piperNewTrack = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, _piperSampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, Math.max(_piperBytes, _piperMinBuf), android.media.AudioTrack.MODE_STATIC);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("_piperStop();").append(EOL);
        sb.append("_piperTrack = _piperNewTrack;").append(EOL);
        sb.append("_piperNewTrack.write(_piperPcm, 0, _piperPcm.length);").append(EOL);
        sb.append("_piperNewTrack.play();").append(EOL);
        sb.append("android.util.Log.i(\"AscodeTTS\", \"PIPER play session=\" + _piperNewTrack.getAudioSessionId() + \" durMs=\" + (_piperPcm.length * 1000L / Math.max(1, _piperSampleRate)));").append(EOL);
        sb.append("_ttsEmitDiag(\"piperPlay\");").append(EOL);
        sb.append("Thread.sleep(_piperPcm.length * 1000L / Math.max(1, _piperSampleRate));").append(EOL);
        sb.append("} catch (Throwable _piperPlayError) {").append(EOL);
        sb.append("android.util.Log.e(\"AscodeTTS\", \"PIPER play error: \" + _piperPlayError);").append(EOL);
        sb.append("}").append(EOL);
        sb.append("}").append(EOL);

        sb.append(EOL);
        sb.append("private void _piperStop() {").append(EOL);
        sb.append("try {").append(EOL);
        sb.append("if (_piperTrack != null) {").append(EOL);
        sb.append("try { _piperTrack.stop(); } catch (Throwable _piperStopError) { }").append(EOL);
        sb.append("try { _piperTrack.release(); } catch (Throwable _piperReleaseError) { }").append(EOL);
        sb.append("}").append(EOL);
        sb.append("} catch (Throwable ignored) { }").append(EOL);
        sb.append("_piperTrack = null;").append(EOL);
        sb.append("}").append(EOL);
        sb.append(EOL);
    }

    /**
     * Returns the index of the closing brace matching the {@code '{'} at {@code openBraceIndex},
     * ignoring braces inside strings, characters and comments. Returns {@code -1} when unbalanced.
     */
    private static int findMatchingBrace(String source, int openBraceIndex) {
        int len = source.length();
        int depth = 0;
        boolean opened = false;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = openBraceIndex; i < len; i++) {
            char c = source.charAt(i);
            char next = i + 1 < len ? source.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            if (c == '{') {
                depth++;
                opened = true;
            } else if (c == '}') {
                depth--;
                if (opened && depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
