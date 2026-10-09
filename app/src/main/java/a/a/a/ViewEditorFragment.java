package a.a.a;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.LogicEditorActivity;
import com.besome.sketch.editor.PropertyActivity;
import com.besome.sketch.editor.manage.view.AddViewActivity;
import com.besome.sketch.editor.view.DraggingListener;
import com.besome.sketch.editor.view.ViewEditor;
import com.besome.sketch.editor.view.ViewProperty;
import com.besome.sketch.editor.view.palette.PaletteWidget;

import java.util.ArrayList;

import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.utility.AscodeUtil;
import com.ascode.android.widgets.WidgetsCreatorManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.ascode.android.ScreenDesignCopier;

/**
 * Request code used to create a brand-new screen from the "copy design and
 * logic" dialog. Kept distinct from 213 (property editor) and from the codes
 * used by AddViewActivity (264/265/276).
 */

public class ViewEditorFragment extends qA {

    public ViewEditor viewEditor;
    private ProjectFileBean projectFileBean;
    private boolean isFabEnabled = false;
    private ViewProperty viewProperty;
    private ObjectAnimator showPropertyViewAnimator;
    private ObjectAnimator hidePropertyViewAnimator;
    private boolean isPropertyViewVisible;
    private boolean isDragging = false;
    private String sc_id;

    /**
     * Request code used to create a brand-new screen from the "copy design and
     * logic" dialog. Kept distinct from 213 (property editor) and from the codes
     * used by AddViewActivity (264/265/276).
     */
    private static final int REQUEST_CODE_CREATE_SCREEN = 279;

    private WidgetsCreatorManager widgetsCreatorManager;

    public ViewEditorFragment() {
    }

    private void initialize(ViewGroup viewGroup) {
        setHasOptionsMenu(true);
        viewEditor = viewGroup.findViewById(R.id.view_editor);
        viewEditor.setScreenType(getResources().getConfiguration().orientation);
        widgetsCreatorManager = new WidgetsCreatorManager(this);
        viewEditor.widgetsCreatorManager = widgetsCreatorManager;
        viewProperty = requireActivity().findViewById(R.id.view_property);
        viewProperty.setOnPropertyListener(new Iw() {
            @Override
            public void a() {
                viewEditor.setFavoriteData(Rp.h().f());
            }

            @Override
            public void a(String s, ViewBean viewBean) {
                openPropertyActivity(viewBean);
            }
        });
        viewProperty.setOnPropertyValueChangedListener(viewBean -> {
            a(viewBean.id);
            viewProperty.e();
            invalidateOptionsMenu();
        });
        viewProperty.setOnPropertyDeleted(viewBean -> {
            viewEditor.deleteWidget(viewBean);
            if (requireActivity() instanceof DesignActivity designActivity) {
                designActivity.hideViewPropertyView();
            }
            AscodeUtil.toast(Helper.getResString(R.string.common_word_deleted));
        });
        viewProperty.setOnEventClickListener(eventBean -> toLogicEditorActivity(eventBean.targetId, eventBean.eventName, eventBean.eventName));
        viewProperty.setOnPropertyTargetChangeListener(viewEditor::updateSelection);
        viewEditor.setOnWidgetSelectedListener(new cy() {
            @Override
            public void a() {
                n();
                viewProperty.e();
            }

            @Override
            public void a(String viewId) {
                n();
                viewProperty.a(viewId);
            }

            @Override
            public void a(boolean var1, String viewId) {
                if (!viewId.isEmpty()) {
                    a();
                    viewProperty.a(viewId);
                    viewProperty.e();
                }

                ViewEditorFragment.this.a(var1);
            }
        });
        viewEditor.setOnDraggingListener(new DraggingListener() {
            @Override
            public boolean isAdmobEnabled() {
                return jC.c(sc_id).b().isEnabled();
            }

            @Override
            public void b() {
                isDragging = true;
                ((DesignActivity) requireActivity()).setTouchEventEnabled(false);
            }

            @Override
            public boolean isGoogleMapEnabled() {
                return jC.c(sc_id).e().isEnabled();
            }

            @Override
            public void d() {
                isDragging = false;
                ((DesignActivity) requireActivity()).setTouchEventEnabled(true);
            }
        });
        viewEditor.setOnHistoryChangeListener(this::invalidateOptionsMenu);
        viewEditor.setFavoriteData(Rp.h().f());
    }

    public void initialize(ProjectFileBean projectFileBean) {
        this.projectFileBean = projectFileBean;
        isFabEnabled = projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB);
        viewEditor.initialize(sc_id, projectFileBean);
        /* The Visual editor applies the Toolbar/StatusBar placement from onLayout (only when
         * isLayoutChanged is set), so an already-laid-out editor must be asked to lay out again.
         * Without this, re-initialising the fragment after editing a screen option changed the
         * internal state but the preview kept the old layout until the project was reopened. */
        viewEditor.requestLayout();
        viewEditor.h();
        viewProperty.a(sc_id, this.projectFileBean);
        e();
        i();
        invalidateOptionsMenu();
    }

    private void a(ViewBean viewBean) {
        viewEditor.removeFab();
        if (isFabEnabled) viewEditor.addFab(viewBean);
    }

    private void a(String viewId) {
        ViewBean viewBean;
        if (viewId.equals("_fab")) {
            viewBean = jC.a(sc_id).h(projectFileBean.getXmlName());
        } else {
            viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), viewId);
        }
        c(viewBean);
        viewProperty.e();
    }

    private void toLogicEditorActivity(String eventId, String eventName, String eventName2) {
        Intent intent = new Intent(requireContext(), LogicEditorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("id", eventId);
        intent.putExtra("event", eventName);
        intent.putExtra("project_file", projectFileBean);
        intent.putExtra("event_text", eventName2);
        requireContext().startActivity(intent);
    }

    public void a(ArrayList<ViewBean> viewBeans) {
        viewEditor.h();
        viewEditor.a(eC.a(viewBeans));
    }

    public void a(boolean var1) {
        startAnimation();
        if (!isPropertyViewVisible || !var1) {
            cancelAnimations();
            if (var1) {
                showPropertyViewAnimator.start();
            } else if (isPropertyViewVisible) {
                hidePropertyViewAnimator.start();
            }

            isPropertyViewVisible = var1;
        }
    }

    public void openPropertyActivity(ViewBean viewBean) {
        Intent intent = new Intent(requireContext(), PropertyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("bean", viewBean);
        intent.putExtra("project_file", projectFileBean);
        startActivityForResult(intent, 213);
    }

    private void b(ArrayList<ViewBean> viewBeans) {
        l();
        a(viewBeans);
    }

    private void cancelAnimations() {
        if (showPropertyViewAnimator.isRunning()) showPropertyViewAnimator.cancel();
        if (hidePropertyViewAnimator.isRunning()) hidePropertyViewAnimator.cancel();
    }

    public void c(ViewBean var1) {
        viewEditor.e(var1);
    }

    public void showHidePropertyView(boolean shouldShow) {
        viewProperty.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    public ProjectFileBean d() {
        return projectFileBean;
    }

    public void e() {
        viewEditor.removeWidgetsAndLayouts();
        viewEditor.setPaletteLayoutVisible(View.VISIBLE);
        viewEditor.addWidgetLayout(PaletteWidget.a.a, "");
        viewEditor.addWidgetLayout(PaletteWidget.a.b, "");
        viewEditor.addWidget(PaletteWidget.b.b, "", "TextView", "TextView");
        viewEditor.addWidgetLayout(PaletteWidget.a.c, "");
        viewEditor.addWidgetLayout(PaletteWidget.a.d, "");
        viewEditor.extraWidgetLayout("", "RadioGroup");
        viewEditor.extraWidgetLayout("", "RelativeLayout");
        widgetsCreatorManager.addWidgetsByTitle("Layouts");

        viewEditor.paletteWidget.extraTitle("AndroidX", 0);
        viewEditor.extraWidgetLayout("", "TabLayout");
        viewEditor.extraWidgetLayout("", "BottomNavigationView");
        viewEditor.extraWidgetLayout("", "CollapsingToolbarLayout");
        viewEditor.extraWidgetLayout("", "CardView");
        viewEditor.extraWidgetLayout("", "TextInputLayout");
        viewEditor.extraWidgetLayout("", "SwipeRefreshLayout");
        widgetsCreatorManager.addWidgetsByTitle("AndroidX");

        viewEditor.addWidget(PaletteWidget.b.c, "", "EditText", "Edit Text");
        viewEditor.extraWidget("", "AutoCompleteTextView", "AutoCompleteTextView");
        viewEditor.extraWidget("", "MultiAutoCompleteTextView", "MultiAutoCompleteTextView");
        viewEditor.addWidget(PaletteWidget.b.a, "", "Button", "Button");
        viewEditor.extraWidget("", "MaterialButton", "MaterialButton");
        viewEditor.addWidget(PaletteWidget.b.d, "", "ImageView", "default_image");
        viewEditor.extraWidget("", "CircleImageView", "default_image");
        viewEditor.addWidget(PaletteWidget.b.g, "", "CheckBox", "CheckBox");
        viewEditor.extraWidget("", "RadioButton", "RadioButton");
        viewEditor.addWidget(PaletteWidget.b.i, "", "Switch", "Switch");
        viewEditor.addWidget(PaletteWidget.b.j, "", "SeekBar", "SeekBar");
        viewEditor.addWidget(PaletteWidget.b.m, "", "ProgressBar", "ProgressBar");
        viewEditor.extraWidget("", "RatingBar", "RatingBar");
        viewEditor.extraWidget("", "SearchView", "SearchView");
        viewEditor.extraWidget("", "VideoView", "VideoView");
        viewEditor.addWidget(PaletteWidget.b.h, "", "WebView", "WebView");
        widgetsCreatorManager.addWidgetsByTitle("Widgets");

        viewEditor.paletteWidget.extraTitle("List", 1);
        viewEditor.addWidget(PaletteWidget.b.e, "", "ListView", "ListView");
        viewEditor.extraWidget("", "GridView", "GridView");
        viewEditor.extraWidget("", "RecyclerView", "RecyclerView");
        viewEditor.addWidget(PaletteWidget.b.f, "", "Spinner", "Spinner");
        viewEditor.extraWidget("", "ViewPager", "ViewPager");
        widgetsCreatorManager.addWidgetsByTitle("List");

        viewEditor.paletteWidget.extraTitle("Library", 1);
        viewEditor.extraWidget("", "WaveSideBar", "WaveSideBar");
        viewEditor.extraWidget("", "PatternLockView", "PatternLockView");
        viewEditor.extraWidget("", "CodeView", "CodeView");
        viewEditor.extraWidget("", "LottieAnimation", "LottieAnimation");
        viewEditor.extraWidget("", "OTPView", "OTPView");
        widgetsCreatorManager.addWidgetsByTitle("Library");

        viewEditor.paletteWidget.extraTitle("Google", 1);
        viewEditor.addWidget(PaletteWidget.b.l, "", "AdView", "AdView");
        viewEditor.addWidget(PaletteWidget.b.n, "", "MapView", "MapView");
        viewEditor.extraWidget("", "SignInButton", "SignInButton");
        viewEditor.extraWidget("", "YoutubePlayer", "YoutubePlayer");
        widgetsCreatorManager.addWidgetsByTitle("Google");

        viewEditor.paletteWidget.extraTitle("Date & Time", 1);
        viewEditor.extraWidget("", "AnalogClock", "AnalogClock");
        viewEditor.extraWidget("", "DigitalClock", "DigitalClock");
        viewEditor.extraWidget("", "TimePicker", "TimePicker");
        viewEditor.extraWidget("", "DatePicker", "DatePicker");
        viewEditor.addWidget(PaletteWidget.b.k, "", "CalendarView", "CalendarView");
        widgetsCreatorManager.addWidgetsByTitle("Date & Time");
        widgetsCreatorManager.addExtraClasses();
    }

    private void startAnimation() {
        if (showPropertyViewAnimator == null) {
            showPropertyViewAnimator = ObjectAnimator.ofFloat(viewProperty, View.TRANSLATION_Y, 0.0F);
            showPropertyViewAnimator.setDuration(700L);
            showPropertyViewAnimator.setInterpolator(new DecelerateInterpolator());
        }

        if (hidePropertyViewAnimator == null) {
            if (getActivity() == null) return;
            hidePropertyViewAnimator = ObjectAnimator.ofFloat(viewProperty, View.TRANSLATION_Y, wB.a(requireActivity(), (float) viewProperty.getHeight()));
            hidePropertyViewAnimator.setDuration(300L);
            hidePropertyViewAnimator.setInterpolator(new DecelerateInterpolator());
        }
    }

    public boolean isPropertyViewVisible() {
        return isPropertyViewVisible;
    }

    private void onRedo() {
        if (!isDragging) {
            HistoryViewBean historyViewBean = cC.c(sc_id).h(projectFileBean.getXmlName());
            if (historyViewBean != null) {
                int actionType = historyViewBean.getActionType();
                if (actionType == HistoryViewBean.ACTION_TYPE_ADD) {
                    for (ViewBean viewBean : historyViewBean.getAddedData()) {
                        jC.a(sc_id).a(projectFileBean.getXmlName(), viewBean);
                    }
                    viewEditor.a(viewEditor.a(historyViewBean.getAddedData(), false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_UPDATE) {
                    ViewBean prevUpdateData = historyViewBean.getPrevUpdateData();
                    ViewBean currentUpdateData = historyViewBean.getCurrentUpdateData();
                    if (!prevUpdateData.id.equals(currentUpdateData.id)) {
                        currentUpdateData.preId = prevUpdateData.id;
                    }

                    if (currentUpdateData.id.equals("_fab")) {
                        jC.a(sc_id).h(projectFileBean.getXmlName()).copy(currentUpdateData);
                    } else {
                        jC.a(sc_id).c(projectFileBean.getXmlName(), prevUpdateData.id).copy(currentUpdateData);
                    }

                    viewEditor.a(viewEditor.e(currentUpdateData), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_REMOVE) {
                    for (ViewBean viewBean : historyViewBean.getRemovedData()) {
                        jC.a(sc_id).a(projectFileBean, viewBean);
                    }
                    viewEditor.b(historyViewBean.getRemovedData(), false);
                    viewEditor.i();
                } else if (actionType == HistoryViewBean.ACTION_TYPE_MOVE) {
                    ViewBean movedData = historyViewBean.getMovedData();
                    ViewBean viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), movedData.id);
                    viewBean.copy(movedData);
                    viewEditor.a(viewEditor.b(viewBean, false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_OVERRIDE) {
                    jC.a(sc_id).c.put(projectFileBean.getXmlName(), historyViewBean.getAddedData());
                    i();
                }
            }
            invalidateOptionsMenu();
        }
    }

    public void i() {
        invalidateOptionsMenu();
        if (projectFileBean != null) {
            b(jC.a(sc_id).d(projectFileBean.getXmlName()));
            a(jC.a(sc_id).h(projectFileBean.getXmlName()));
        }
    }

    public void j() {
        viewEditor.setFavoriteData(Rp.h().f());
    }

    private void invalidateOptionsMenu() {
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    public void l() {
        viewEditor.j();
    }

    private void onUndo() {
        if (!isDragging) {
            HistoryViewBean historyViewBean = cC.c(sc_id).i(projectFileBean.getXmlName());
            if (historyViewBean != null) {
                int actionType = historyViewBean.getActionType();
                if (actionType == HistoryViewBean.ACTION_TYPE_ADD) {
                    for (ViewBean view : historyViewBean.getAddedData()) {
                        jC.a(sc_id).a(projectFileBean, view);
                    }
                    viewEditor.b(historyViewBean.getAddedData(), false);
                    viewEditor.i();
                } else if (actionType == HistoryViewBean.ACTION_TYPE_UPDATE) {
                    ViewBean prevUpdateData = historyViewBean.getPrevUpdateData();
                    ViewBean currentUpdateData = historyViewBean.getCurrentUpdateData();
                    if (!prevUpdateData.id.equals(currentUpdateData.id)) {
                        prevUpdateData.preId = currentUpdateData.id;
                    }
                    if (currentUpdateData.id.equals("_fab")) {
                        jC.a(sc_id).h(projectFileBean.getXmlName()).copy(prevUpdateData);
                    } else {
                        jC.a(sc_id).c(projectFileBean.getXmlName(), currentUpdateData.id).copy(prevUpdateData);
                    }
                    viewEditor.a(viewEditor.e(prevUpdateData), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_REMOVE) {
                    for (ViewBean view : historyViewBean.getRemovedData()) {
                        jC.a(sc_id).a(projectFileBean.getXmlName(), view);
                    }
                    viewEditor.a(viewEditor.a(historyViewBean.getRemovedData(), false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_MOVE) {
                    ViewBean movedData = historyViewBean.getMovedData();
                    ViewBean viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), movedData.id);
                    viewBean.preIndex = movedData.index;
                    viewBean.index = movedData.preIndex;
                    viewBean.parent = movedData.preParent;
                    viewBean.preParent = movedData.parent;
                    viewBean.parentType = movedData.preParentType;
                    viewBean.preParentType = movedData.parentType;
                    viewEditor.a(viewEditor.b(viewBean, false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_OVERRIDE) {
                    jC.a(sc_id).c.put(projectFileBean.getXmlName(), historyViewBean.getRemovedData());
                    i();
                }
            }
            invalidateOptionsMenu();
        }
    }

    public void n() {
        ArrayList<ViewBean> viewBeanArrayList = eC.a(jC.a(sc_id).d(projectFileBean.getXmlName()));
        ViewBean viewBean;
        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) {
            viewBean = jC.a(sc_id).h(projectFileBean.getXmlName());
        } else {
            viewBean = null;
        }
        viewProperty.addActivityViews(viewBeanArrayList, viewBean);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        invalidateOptionsMenu();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 213) {
            if (resultCode == -1) {
                c(data.getParcelableExtra("bean"));
            }

            if (data != null && data.getBooleanExtra("is_edit_image", false)) {
                for (ViewBean viewBean : jC.a(sc_id).d(projectFileBean.getXmlName())) {
                    c(viewBean);
                }
                if (isFabEnabled) {
                    c(jC.a(sc_id).h(projectFileBean.getXmlName()));
                }
            }
            invalidateOptionsMenu();
        } else if (requestCode == REQUEST_CODE_CREATE_SCREEN
                && resultCode == android.app.Activity.RESULT_OK) {
            ProjectFileBean newScreen = data != null
                    ? data.getParcelableExtra("project_file") : null;
            if (newScreen != null) {
                registerNewScreenAndCopy(newScreen);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
        super.onConfigurationChanged(newConfiguration);
        viewEditor.setScreenType(newConfiguration.orientation);
        viewEditor.isLayoutChanged = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.design_view_menu, menu);
        menu.findItem(R.id.menu_view_redo).setEnabled(false);
        menu.findItem(R.id.menu_view_undo).setEnabled(false);
        if (projectFileBean != null) {
            menu.findItem(R.id.menu_view_redo).setEnabled(cC.c(sc_id).f(projectFileBean.getXmlName()));
            menu.findItem(R.id.menu_view_undo).setEnabled(cC.c(sc_id).g(projectFileBean.getXmlName()));
        }
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup parent, Bundle bundle) {
        ViewGroup viewGroup = (ViewGroup) layoutInflater.inflate(R.layout.fr_graphic_editor, parent, false);
        initialize(viewGroup);
        if (bundle != null) {
            sc_id = bundle.getString("sc_id");
        } else {
            sc_id = requireActivity().getIntent().getStringExtra("sc_id");
        }

        return viewGroup;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_view_redo) {
            onRedo();
        } else if (itemId == R.id.menu_view_undo) {
            onUndo();
        } else if (itemId == R.id.menu_view_copy_design) {
            showCopyDesignDialog();
        }
        return true;
    }

    /**
     * Shows the screen picker for "Copiar diseño y lógica…". Lists every other
     * activity of the project; custom views are not offered because they have no
     * logic (their Java name is empty).
     */
    private void showCopyDesignDialog() {
        if (projectFileBean == null) {
            return;
        }
        ArrayList<ProjectFileBean> targets = new ArrayList<>();
        ArrayList<ProjectFileBean> projectFiles = jC.b(sc_id).b();
        if (projectFiles != null) {
            for (ProjectFileBean file : projectFiles) {
                if (file != null
                        && file.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                        && !file.getXmlName().equals(projectFileBean.getXmlName())) {
                    targets.add(file);
                }
            }
        }
        // Build the list by hand instead of using AlertDialog#setItems: this app's
        // dialog theme doesn't render the framework list rows.
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Helper.getResString(R.string.design_copy_screen_title))
                .setMessage(Helper.getResString(R.string.design_copy_screen_select_target))
                .setView(scrollView)
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .create();

        // "New screen" is always offered, even when this is the only screen of the
        // project, so a design can always be duplicated into a fresh screen.
        TextView newScreenRow = new TextView(requireContext());
        newScreenRow.setText(Helper.getResString(R.string.design_copy_screen_new));
        newScreenRow.setTextSize(16f);
        newScreenRow.setTypeface(null, Typeface.BOLD);
        newScreenRow.setGravity(Gravity.CENTER_VERTICAL);
        newScreenRow.setPadding(pad, pad, pad, pad);
        newScreenRow.setClickable(true);
        newScreenRow.setFocusable(true);
        newScreenRow.setOnClickListener(v -> {
            dialog.dismiss();
            createNewScreenForCopy();
        });
        list.addView(newScreenRow);

        for (ProjectFileBean target : targets) {
            TextView row = new TextView(requireContext());
            row.setText(target.fileName);
            row.setTextSize(16f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                confirmCopyDesign(target);
            });
            list.addView(row);
        }
        dialog.show();
    }

    /**
     * Launches the normal "add screen" screen ({@link AddViewActivity}) so the user
     * can name and configure the destination, then copies the current design and
     * logic onto it once it has been created.
     */
    private void createNewScreenForCopy() {
        ArrayList<String> screenNames = new ArrayList<>();
        ArrayList<ProjectFileBean> activities = jC.b(sc_id).b();
        if (activities != null) {
            for (ProjectFileBean file : activities) {
                if (file != null) {
                    screenNames.add(file.fileName);
                }
            }
        }
        ArrayList<ProjectFileBean> customViews = jC.b(sc_id).c();
        if (customViews != null) {
            for (ProjectFileBean file : customViews) {
                if (file != null) {
                    screenNames.add(file.fileName);
                }
            }
        }
        Intent intent = new Intent(requireContext(), AddViewActivity.class);
        intent.putExtra("screen_names", screenNames);
        intent.putExtra("request_code", AddViewActivity.REQUEST_CODE_ADD);
        startActivityForResult(intent, REQUEST_CODE_CREATE_SCREEN);
    }

    /**
     * Registers the freshly created screen in the project structures, then copies
     * the current screen design + logic onto it and refreshes the editor.
     */
    private void registerNewScreenAndCopy(ProjectFileBean newScreen) {
        hC projectFiles = jC.b(sc_id);
        projectFiles.a(newScreen);
        if (newScreen.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            projectFiles.a(2, newScreen.getDrawerName());
        }
        // Rebuild the internal xml/java name lists and persist the project file.
        projectFiles.j();
        projectFiles.l();
        // The new screen is empty (no confirmation needed), so copy right away.
        copyDesignAndLogic(newScreen);
    }

    /**
     * Asks for confirmation when the destination already has a design or logic,
     * then performs the copy.
     */
    private void confirmCopyDesign(ProjectFileBean target) {
        if (!targetHasContent(target)) {
            copyDesignAndLogic(target);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Helper.getResString(R.string.design_copy_screen_confirm_title))
                .setMessage(getString(R.string.design_copy_screen_confirm_message, target.fileName))
                .setPositiveButton(Helper.getResString(R.string.common_word_ok),
                        (dialog, which) -> copyDesignAndLogic(target))
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .show();
    }

    private boolean targetHasContent(ProjectFileBean target) {
        eC data = jC.a(sc_id);
        String xmlName = target.getXmlName();
        String javaName = target.getJavaName();
        ArrayList<ViewBean> views = data.d(xmlName);
        if (views != null && !views.isEmpty()) {
            return true;
        }
        if (javaName == null || javaName.isEmpty()) {
            return false;
        }
        return !data.b(javaName).isEmpty()
                || !data.g(javaName).isEmpty()
                || !data.e(javaName).isEmpty()
                || !data.k(javaName).isEmpty()
                || !data.j(javaName).isEmpty();
    }

    private void copyDesignAndLogic(ProjectFileBean target) {
        try {
            ScreenDesignCopier.Result result = ScreenDesignCopier.copy(sc_id, projectFileBean, target);
            // The destination may have an undo/redo history referencing the old design;
            // drop it so a later Undo can't bring back the replaced content.
            cC history = cC.c(sc_id);
            if (history != null) {
                if (history.c != null) history.c.remove(target.getXmlName());
                if (history.b != null) history.b.remove(target.getXmlName());
            }
            // Reload the editor tabs that may be showing the destination screen.
            if (requireActivity() instanceof DesignActivity designActivity) {
                designActivity.refreshAfterAiActions();
            }
            AscodeUtil.toast(getString(R.string.design_copy_screen_success, target.fileName));
            // Persist design + logic with the normal project save path (background).
            new Thread(() -> {
                try {
                    jC.a(sc_id).j();
                } catch (Exception ignored) {
                }
            }).start();
        } catch (Exception e) {
            AscodeUtil.toast(Helper.getResString(R.string.design_copy_screen_failed));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle newInstanceState) {
        newInstanceState.putString("sc_id", sc_id);
        super.onSaveInstanceState(newInstanceState);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (viewProperty != null) {
            viewProperty.d();
        }
    }
}