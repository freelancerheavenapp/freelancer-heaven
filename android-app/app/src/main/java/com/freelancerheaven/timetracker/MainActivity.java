package com.freelancerheaven.timetracker;

import android.app.*;
import android.animation.*;
import android.os.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private TimerScene timerScene;
    private String installedVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "unknown";}
    }
    private boolean motionWatchRegistered;
    private final android.content.BroadcastReceiver powerWatcher=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context context,Intent intent){refreshMotionPolicy();}
    };
    private final android.database.ContentObserver motionWatcher=new android.database.ContentObserver(new Handler(Looper.getMainLooper())){
        @Override public void onChange(boolean selfChange){refreshMotionPolicy();}
    };
    private void refreshMotionPolicy(){
        if(timerScene!=null)timerScene.state(running,reducedMotion());
        if(reducedMotion()){if(logoAnimation!=null)logoAnimation.cancel();stopLiveAnimation();stopCompactLiveAnimation();stopCardBreathing();}
    }
    private boolean foreground;
    private boolean modern() { return getPreferences(MODE_PRIVATE).getBoolean("modern_ui",false); }
    private boolean reducedMotion() {
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        return getPreferences(MODE_PRIVATE).getBoolean("reduced_motion",false)
            || android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0
            || (power!=null && power.isPowerSaveMode());
    }
    private void showAppearance() {
        final String[] choices={s("Classic interface","Klassische Oberfläche"),s("Premium 3D interface","Premium-3D-Oberfläche")};
        new AlertDialog.Builder(this).setTitle(s("Appearance","Darstellung"))
          .setSingleChoiceItems(choices,modern()?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int index){
            saveTimerState(); getPreferences(MODE_PRIVATE).edit().putBoolean("modern_ui",index==1).apply(); d.dismiss(); recreate();
          }}).setNeutralButton(s("Reduce motion","Bewegung reduzieren"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            new AlertDialog.Builder(MainActivity.this).setTitle(s("Reduce motion","Bewegung reduzieren")).setSingleChoiceItems(new String[]{s("Off","Aus"),s("On","Ein")},getPreferences(MODE_PRIVATE).getBoolean("reduced_motion",false)?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int value){
                getPreferences(MODE_PRIVATE).edit().putBoolean("reduced_motion",value==1).apply(); d.dismiss(); recreate();
            }}).show();
          }}).setNegativeButton(s("Close","Schließen"),null).show();
    }
    private static final int CREATE_BACKUP_REQUEST = 401;
    private static final int IMPORT_BACKUP_REQUEST = 402;
    private static final int CREATE_CSV_REQUEST = 403;
    private static final int CREATE_PDF_REQUEST = 404;
    private static final int BACKUP_VERSION = 1;
    private static final int PBKDF2_ITERATIONS = 600000;
    private TextView timer, total, earnings, status, projectName, rateLabel, startedLabel, liveLabel, projectHeader, premiumLabel, compactSummary;
    private TextView compactTime, compactEarnings, projectChevron;
    private View compactMetrics;
    private ImageView brandLogo;
    private LinearLayout content;
    private View trackerCard, statsRow, premiumCard;
    private View liveDot;
    private View compactLiveDot;
    private LinearLayout detailsPanel, actionsRow, activeControlsPanel;
    private LinearLayout projectsList, projectActionsRow;
    private Button endProject, addProjectButton, manageProjectsButton;
    private boolean detailsExpanded = false;
    private AnimatorSet logoAnimation, liveAnimation, cardBreathingAnimation, compactLiveAnimation;
    private ValueAnimator earningsAnimation, buttonColorAnimation;
    private Button startStop;
    private GradientDrawable startStopBackground;
    private int currentButtonColor = Color.rgb(20,108,148);
    private double displayedEarnings = Double.NaN;
    private String pendingExportPin;
    private Button settingsButton;
    private int pendingReportScope = 0;
    private boolean running = false;
    private long startedAt = 0L, savedSeconds = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { updateTimer(); if (running) { saveTimerState(); handler.postDelayed(this, 1000); } } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        savedSeconds = getPreferences(MODE_PRIVATE).getLong("saved_seconds", 0L);
        running = getPreferences(MODE_PRIVATE).getBoolean("timer_running", false);
        startedAt = getPreferences(MODE_PRIVATE).getLong("timer_started_at", 0L);
        if (running && startedAt == 0L) { startedAt = System.currentTimeMillis(); saveTimerState(); }
        ensureProjectsInitialized();
        getWindow().setStatusBarColor(Color.rgb(246,248,250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        View screen = buildUi();
        setContentView(screen);
        screen.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                if (content != null) content.setPadding(dp(18), insets.getSystemWindowInsetTop() + dp(10), dp(18), dp(10));
                return insets;
            }
        });
        screen.requestApplyInsets();
        renderProject(); renderPremium();
        setTimerVisualState(running, false);
        if (running) handler.post(tick); else updateTimer();
        startLogoAnimation(); playEntryAnimations();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this); content = root;
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(10), dp(18), dp(10));
        root.setBackgroundColor(Color.rgb(246,248,250));

        LinearLayout brandRow = new LinearLayout(this); brandRow.setOrientation(LinearLayout.HORIZONTAL); brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandLogo = new ImageView(this); brandLogo.setImageResource(getResources().getIdentifier("freelancer_heaven_logo", "drawable", getPackageName())); brandLogo.setContentDescription("Freelancer Heaven logo"); brandLogo.setScaleType(ImageView.ScaleType.CENTER_CROP); brandRow.addView(brandLogo, new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView brand = text("FREELANCER HEAVEN", 12, Color.rgb(20,108,148)); brand.setLetterSpacing(.12f); brand.setPadding(dp(10),0,0,0); brandRow.addView(brand, new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        settingsButton=new Button(this); settingsButton.setText("⚙"); settingsButton.setTextSize(23); settingsButton.setTextColor(Color.WHITE); settingsButton.setGravity(Gravity.CENTER); settingsButton.setPadding(0,0,0,dp(1)); settingsButton.setBackground(round(Color.rgb(16,42,67), 14)); settingsButton.setElevation(dp(3)); settingsButton.setContentDescription(s("Settings", "Einstellungen")); settingsButton.setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){ showSettings(); }}); LinearLayout.LayoutParams settingsParams=new LinearLayout.LayoutParams(dp(42),dp(38)); settingsParams.leftMargin=dp(10); brandRow.addView(settingsButton,settingsParams); attachPressAnimation(settingsButton); root.addView(brandRow);
        TextView title = text("Freelancer Time Tracker", 25, Color.rgb(16,42,67)); title.setPadding(0, dp(5), 0, dp(1)); root.addView(title);
        TextView subtitle = text(s("Know where your working hours go.", "Behalte deine Arbeitszeit im Blick."), 15, Color.DKGRAY); root.addView(subtitle);

        Space a = new Space(this); root.addView(a, new LinearLayout.LayoutParams(1,dp(9)));
        ProjectCard card = new ProjectCard(this); trackerCard = card; card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18),dp(14),dp(18),dp(14)); card.setGravity(Gravity.CENTER); card.setBackground(round(Color.WHITE, 20)); card.setElevation(dp(2)); root.addView(card, matchWrap());
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleProjectDetails(); } });
        LinearLayout projectHeaderRow=new LinearLayout(this); projectHeaderRow.setOrientation(LinearLayout.HORIZONTAL); projectHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        compactLiveDot=new View(this); compactLiveDot.setBackground(round(Color.rgb(56,193,114),4)); projectHeaderRow.addView(compactLiveDot,new LinearLayout.LayoutParams(dp(7),dp(7))); compactLiveDot.setVisibility(View.GONE);
        projectHeader = text(s("CURRENT PROJECT  ·  TAP TO EXPAND", "AKTUELLES PROJEKT  ·  ANTIPPEN ZUM ÖFFNEN"), 12, Color.rgb(20,108,148)); projectHeader.setLetterSpacing(.08f); projectHeader.setPadding(dp(7),0,0,0); projectHeaderRow.addView(projectHeader); card.addView(projectHeaderRow);
        projectName = text(getPreferences(MODE_PRIVATE).getString("project_name", "My first project"), 20, Color.rgb(16,42,67)); projectName.setPadding(0,dp(7),0,dp(3)); card.addView(projectName);
        compactSummary = text("00:00:00  ·  €0.00", 14, Color.rgb(20,108,148)); compactSummary.setPadding(0,0,0,dp(4)); card.addView(compactSummary);
        detailsPanel = new LinearLayout(this); detailsPanel.setOrientation(LinearLayout.VERTICAL);
        rateLabel = text(s("Rate: ", "Satz: ") + money(rate()) + s(" / hour", " / Stunde"), 14, Color.DKGRAY); rateLabel.setPadding(0,0,0,dp(8)); detailsPanel.addView(rateLabel);
        startedLabel = text(s("Timer is not running", "Timer läuft nicht"), 13, Color.rgb(20,108,148)); startedLabel.setPadding(0,0,0,dp(8)); detailsPanel.addView(startedLabel);
        LinearLayout liveRow = new LinearLayout(this); liveRow.setOrientation(LinearLayout.HORIZONTAL); liveRow.setGravity(Gravity.CENTER_VERTICAL); liveRow.setPadding(0,0,0,dp(8));
        liveDot = new View(this); liveDot.setBackground(round(Color.GRAY, 5)); liveRow.addView(liveDot, new LinearLayout.LayoutParams(dp(8),dp(8)));
        liveLabel = text("PAUSED", 11, Color.GRAY); liveLabel.setLetterSpacing(.08f); liveLabel.setPadding(dp(7),0,0,0); liveRow.addView(liveLabel); detailsPanel.addView(liveRow);
        Button editProject = new Button(this); editProject.setText(s("EDIT PROJECT","PROJEKT BEARBEITEN")); editProject.setTextSize(11); editProject.setTextColor(Color.rgb(20,108,148)); editProject.setBackgroundColor(Color.TRANSPARENT); detailsPanel.addView(editProject, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))); editProject.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { editProject(); } });
        Button deleteProject = new Button(this); deleteProject.setText(s("DELETE PROJECT","PROJEKT LÖSCHEN")); deleteProject.setTextSize(11); deleteProject.setTextColor(Color.rgb(190,72,60)); deleteProject.setBackgroundColor(Color.TRANSPARENT); detailsPanel.addView(deleteProject, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))); deleteProject.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmDeleteCurrentProject(); } });
        card.addView(detailsPanel); detailsPanel.setVisibility(View.GONE);
        activeControlsPanel = new LinearLayout(this); activeControlsPanel.setOrientation(LinearLayout.VERTICAL); card.addView(activeControlsPanel); activeControlsPanel.setVisibility(View.GONE);
        timer = text("00:00:00", 34, Color.rgb(16,42,67)); timer.setGravity(Gravity.CENTER); activeControlsPanel.addView(timer);
        startStop = new Button(this); startStop.setText(s("START TIMER","TIMER STARTEN")); startStop.setTextColor(Color.WHITE); startStop.setTextSize(15); startStopBackground=round(currentButtonColor, 14); startStop.setBackground(startStopBackground); startStop.setElevation(dp(4));
        actionsRow = new LinearLayout(this); actionsRow.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams actionsParams=matchWrap(); actionsParams.topMargin=dp(8); activeControlsPanel.addView(actionsRow, actionsParams);
        actionsRow.addView(startStop);
        startStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTimer(); }
        });
        attachPressAnimation(startStop);
        endProject = new Button(this); endProject.setText(s("END PROJECT","PROJEKT BEENDEN")); endProject.setTextColor(Color.WHITE); endProject.setTextSize(13); endProject.setBackground(round(Color.rgb(16,42,67), 14)); endProject.setElevation(dp(4)); endProject.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmEndProject(); } }); attachPressAnimation(endProject); actionsRow.addView(endProject); endProject.setVisibility(View.GONE);

        LinearLayout stats = new LinearLayout(this); statsRow = stats; stats.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams statsParams=matchWrap(); statsParams.topMargin=dp(8); activeControlsPanel.addView(stats, statsParams);
        total = text("", 22, Color.rgb(16,42,67));
        LinearLayout totalCard=statCard(s("TOTAL SAVED","GESAMTZEIT"), total); stats.addView(totalCard, new LinearLayout.LayoutParams(0, dp(82), 1)); attachPressAnimation(totalCard);
        Space gap = new Space(this); stats.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        earnings = text("€0.00", 22, Color.rgb(16,42,67)); LinearLayout earningsCard=statCard(s("EST. EARNINGS","GESCH. EINNAHMEN"), earnings); stats.addView(earningsCard, new LinearLayout.LayoutParams(0, dp(82), 1)); attachPressAnimation(earningsCard);

        Space b = new Space(this); root.addView(b, new LinearLayout.LayoutParams(1,dp(10)));
        projectsList = new LinearLayout(this); projectsList.setOrientation(LinearLayout.VERTICAL); root.addView(projectsList, matchWrap());
        projectActionsRow = new LinearLayout(this); projectActionsRow.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams projectActionsParams=matchWrap(); projectActionsParams.topMargin=dp(8); root.addView(projectActionsRow,projectActionsParams);
        addProjectButton = new Button(this); addProjectButton.setText(s("+ ADD PROJECT","+ PROJEKT HINZUFÜGEN")); addProjectButton.setTextSize(13); addProjectButton.setTextColor(Color.rgb(20,108,148)); addProjectButton.setBackground(round(Color.WHITE,14)); addProjectButton.setElevation(dp(2)); addProjectButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addProject(); } }); attachPressAnimation(addProjectButton); projectActionsRow.addView(addProjectButton,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        manageProjectsButton = new Button(this); manageProjectsButton.setText(s("MANAGE","VERWALTEN")); manageProjectsButton.setTextSize(12); manageProjectsButton.setTextColor(Color.WHITE); manageProjectsButton.setBackground(round(Color.rgb(20,108,148),14)); manageProjectsButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showProjectManager(); } }); attachPressAnimation(manageProjectsButton); LinearLayout.LayoutParams manageParams=new LinearLayout.LayoutParams(dp(112),ViewGroup.LayoutParams.WRAP_CONTENT); manageParams.leftMargin=dp(8); projectActionsRow.addView(manageProjectsButton,manageParams);

        status = text(s("Timer active — safe to leave the app.","Timer aktiv — du kannst die App sicher verlassen."), 14, Color.DKGRAY); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(14),dp(8),dp(14),dp(8)); status.setBackground(round(Color.WHITE, 16)); LinearLayout.LayoutParams statusParams=matchWrap(); statusParams.topMargin=dp(10); root.addView(status, statusParams);
        premiumLabel = text(s("GO PREMIUM  •  €1.40 / month\nUnlimited projects · Reports · Backup","PREMIUM  •  €1.40 / Monat\nUnbegrenzte Projekte · Berichte · Backup"), 15, Color.WHITE); premiumCard = premiumLabel; premiumLabel.setPadding(dp(18),dp(18),dp(18),dp(18)); premiumLabel.setBackground(round(Color.rgb(16,42,67), 18));
        LinearLayout.LayoutParams pp=matchWrap();pp.topMargin=dp(10);root.addView(premiumLabel,pp);
        premiumLabel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPremium(); } });
        if(modern()) {
            root.setBackgroundColor(Color.rgb(242,248,250));
            title.setTextSize(24); subtitle.setTextSize(14);
            card.setElevation(dp(5));
            timer.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));timer.setTextSize(36);
            projectName.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
            editProject.setMinHeight(dp(48));deleteProject.setMinHeight(dp(48));
            premiumLabel.setTextSize(13);premiumLabel.setPadding(dp(16),dp(12),dp(16),dp(12));
            startStop.setMinHeight(dp(52));addProjectButton.setMinHeight(dp(48));manageProjectsButton.setMinHeight(dp(48));
            projectHeaderRow.setLayoutParams(matchWrap());
            projectHeader.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            projectChevron=text("⌄",24,Color.rgb(20,108,148)); projectChevron.setGravity(Gravity.CENTER);
            projectHeaderRow.addView(projectChevron,new LinearLayout.LayoutParams(dp(40),dp(40)));
            FrameLayout hero=new FrameLayout(this);
            View fallback=new RingFallback(this);
            hero.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            timerScene=new TimerScene(this);
            hero.addView(timerScene,new FrameLayout.LayoutParams(-1,-1));
            card.addView(hero,2,new LinearLayout.LayoutParams(-1,dp(130)));
            detailsPanel.removeView(rateLabel);card.addView(rateLabel,2,matchWrap());
            detailsPanel.removeView(startedLabel);detailsPanel.removeView(liveRow);
            detailsPanel.removeView(editProject);detailsPanel.removeView(deleteProject);
            actionsRow.removeView(endProject);
            LinearLayout secondary=new LinearLayout(this);secondary.setOrientation(LinearLayout.HORIZONTAL);
            editProject.setAllCaps(false);editProject.setTextSize(13);
            endProject.setAllCaps(false);endProject.setTextSize(13);endProject.setTextColor(Color.rgb(20,108,148));endProject.setBackgroundColor(Color.TRANSPARENT);endProject.setElevation(0);
            deleteProject.setAllCaps(false);deleteProject.setTextSize(13);
            secondary.addView(editProject,new LinearLayout.LayoutParams(0,dp(48),1));
            secondary.addView(endProject,new LinearLayout.LayoutParams(0,dp(48),1));
            detailsPanel.addView(secondary,matchWrap());
            detailsPanel.addView(deleteProject,new LinearLayout.LayoutParams(-1,dp(48)));
            card.removeView(detailsPanel);card.addView(detailsPanel,matchWrap());
            // A single live total and estimated earnings replace the duplicate total.
            activeControlsPanel.removeView(stats);
            TextView timeCaption=text(s("TIME TRACKED","ERFASSTE ZEIT"),11,Color.rgb(20,108,148));timeCaption.setGravity(Gravity.CENTER);timeCaption.setLetterSpacing(.06f);
            activeControlsPanel.addView(timeCaption,0,matchWrap());
            timer.setFontFeatureSettings("tnum");
            LinearLayout earned=new LinearLayout(this);earned.setOrientation(LinearLayout.VERTICAL);earned.setGravity(Gravity.CENTER);
            TextView earnedCaption=text(s("ESTIMATED EARNINGS","GESCHÄTZTE EINNAHMEN"),11,Color.rgb(20,108,148));earnedCaption.setLetterSpacing(.04f);earned.addView(earnedCaption);
            earningsCard.removeView(earnings);earnings.setTextSize(20);earnings.setPadding(0,dp(3),0,0);earned.addView(earnings);
            activeControlsPanel.addView(earned,2,matchWrap());
            compactSummary.setVisibility(View.GONE);
            LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);metrics.setPadding(0,dp(4),0,dp(4));
            LinearLayout metricTime=new LinearLayout(this);metricTime.setOrientation(LinearLayout.VERTICAL);
            TextView timeLabel=text(s("TIME TRACKED","ERFASSTE ZEIT"),10,Color.rgb(20,108,148));timeLabel.setLetterSpacing(.05f);metricTime.addView(timeLabel);
            compactTime=text("00:00:00",18,Color.rgb(16,42,67));compactTime.setFontFeatureSettings("tnum");metricTime.addView(compactTime);
            LinearLayout metricEarned=new LinearLayout(this);metricEarned.setOrientation(LinearLayout.VERTICAL);metricEarned.setGravity(Gravity.RIGHT);
            TextView earningsLabel=text(s("EST. EARNINGS","GESCH. EINNAHMEN"),10,Color.rgb(20,108,148));earningsLabel.setLetterSpacing(.05f);metricEarned.addView(earningsLabel);
            compactEarnings=text("",18,Color.rgb(16,42,67));metricEarned.addView(compactEarnings);
            metrics.addView(metricTime,new LinearLayout.LayoutParams(0,-2,1));metrics.addView(metricEarned,new LinearLayout.LayoutParams(0,-2,1));
            compactMetrics=metrics;card.addView(metrics,card.indexOfChild(compactSummary)+1,matchWrap());
            detailsExpanded=false;
            projectHeaderRow.setMinimumHeight(dp(32));
            projectName.setMinHeight(dp(40));
        }
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        scroll.setClipToPadding(false); scroll.addView(root);
        scroll.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener(){public void onScrollChanged(){if(timerScene!=null && foreground)timerScene.resumeScene();}});
        return scroll;
    }

    private LinearLayout statCard(String label, TextView value) { LinearLayout box=new LinearLayout(this); box.setPadding(dp(15),dp(14),dp(15),dp(12));box.setOrientation(LinearLayout.VERTICAL);box.setBackground(round(Color.WHITE,16)); TextView t=text(label,11,Color.rgb(20,108,148));t.setLetterSpacing(.06f);box.addView(t);value.setPadding(0,dp(10),0,0);box.addView(value);return box; }
    private void toggleTimer() { if (!hasActiveProject() || !projectSetupComplete()) { editProject(); return; } handler.removeCallbacks(tick); if (!running) { running=true; startedAt=System.currentTimeMillis(); saveTimerState(); setTimerVisualState(true, true); handler.post(tick); } else { savedSeconds += (System.currentTimeMillis()-startedAt)/1000; running=false; startedAt=0L; saveTimerState(); setTimerVisualState(false, true); status.setText("Time saved. Choose Start Timer or End Project."); updateTimer(); } }
    private void updateTimer() { long seconds=savedSeconds+(running?(System.currentTimeMillis()-startedAt)/1000:0); timer.setText(format(seconds)); total.setText(format(seconds)); double value=seconds * rate() / 3600.0; compactSummary.setText(!projectSetupComplete() ? s("Add a name and hourly rate","Name und Stundensatz hinzufügen") : (!hasActiveProject() ? s("Tap to create a project","Antippen, um ein Projekt zu erstellen") : format(seconds)+"  ·  "+money(value))); if(compactTime!=null){compactTime.setText(format(seconds));compactEarnings.setText(money(value));} animateEarnings(value); if(startedLabel!=null) startedLabel.setText(running ? "Started: " + formatStarted(startedAt) : "Timer is not running"); if(status!=null) { if(running) status.setText("Timer active — safe to leave the app."); else if(!hasActiveProject()) status.setText("Create a project to start tracking."); else if(savedSeconds==0L) status.setText("Ready to start tracking time."); } }
    private void setTimerVisualState(boolean active, boolean animate) {
        boolean hasProject=hasActiveProject() && projectSetupComplete();
        int targetColor=active ? Color.rgb(231,111,81) : Color.rgb(20,108,148);
        startStop.setText(active ? s("STOP & SAVE","STOPPEN & SPEICHERN") : (hasProject ? s("START TIMER","TIMER STARTEN") : s("NEW PROJECT","NEUES PROJEKT")));
        if (animate) animateButtonColor(targetColor); else { currentButtonColor=targetColor; startStopBackground.setColor(targetColor); }
        if(liveLabel!=null) { liveLabel.setText(active ? "LIVE" : s("PAUSED","PAUSIERT")); liveLabel.setTextColor(active ? Color.rgb(20,108,148) : Color.GRAY); }
        if(liveDot!=null) liveDot.setBackground(round(active ? Color.rgb(79,209,197) : Color.GRAY, 5));
        updateActionLayout(active, hasProject);
        applyProjectExpansion();
        if(timerScene!=null) timerScene.state(active,reducedMotion());
        if(active) { startLiveAnimation(); startCompactLiveAnimation(); startCardBreathing(); } else { stopLiveAnimation(); stopCompactLiveAnimation(); stopCardBreathing(); }
    }
    private void updateActionLayout(boolean active, boolean hasProject) {
        boolean split=!active && hasProject && savedSeconds>0;
        endProject.setVisibility(split ? View.VISIBLE : View.GONE);
        if(modern()) { startStop.setLayoutParams(new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT)); return; }
        LinearLayout.LayoutParams startParams=new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, split ? 1f : 1f);
        if(split) startParams.rightMargin=dp(8); else { startParams.width=ViewGroup.LayoutParams.MATCH_PARENT; startParams.weight=0; }
        startStop.setLayoutParams(startParams);
        if(split) endProject.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
    }
    private void animateButtonColor(int targetColor) {
        if(reducedMotion()){currentButtonColor=targetColor;startStopBackground.setColor(targetColor);return;}
        if(buttonColorAnimation!=null) buttonColorAnimation.cancel();
        buttonColorAnimation=ValueAnimator.ofObject(new ArgbEvaluator(),currentButtonColor,targetColor); buttonColorAnimation.setDuration(260); buttonColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){ @Override public void onAnimationUpdate(ValueAnimator a){ startStopBackground.setColor((Integer)a.getAnimatedValue()); }}); buttonColorAnimation.start(); currentButtonColor=targetColor;
        startStop.animate().scaleX(.96f).scaleY(.96f).setDuration(110).withEndAction(new Runnable(){ @Override public void run(){ startStop.animate().scaleX(1f).scaleY(1f).setDuration(160).start(); }}).start();
    }
    private void animateEarnings(double value) {
        if (Double.isNaN(displayedEarnings)) { displayedEarnings=value; earnings.setText(money(value)); return; }
        if (earningsAnimation!=null) earningsAnimation.cancel();
        if(reducedMotion()){displayedEarnings=value;earnings.setText(money(value));return;}
        earningsAnimation=ValueAnimator.ofFloat((float)displayedEarnings,(float)value); earningsAnimation.setDuration(360); earningsAnimation.setInterpolator(new DecelerateInterpolator()); earningsAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){ @Override public void onAnimationUpdate(ValueAnimator a){ earnings.setText(money(((Float)a.getAnimatedValue()).doubleValue())); }}); earningsAnimation.start(); displayedEarnings=value;
    }
    private void ensureProjectsInitialized() {
        if(getPreferences(MODE_PRIVATE).getInt("project_count",0)>0) return;
        SharedPreferences p=getPreferences(MODE_PRIVATE); SharedPreferences.Editor e=p.edit();
        String code=p.getString("default_currency","EUR");
        e.putInt("project_count",1).putInt("current_project_id",0).putString("project_0_name",p.getString("project_name","My first project")).putLong("project_0_rate",p.getLong("hourly_rate",Double.doubleToRawLongBits(25.0))).putLong("project_0_seconds",savedSeconds).putString("project_0_currency",code).putString("currency_code",code).putBoolean("project_setup_complete",false).putBoolean("project_0_completed",false).apply();
    }
    private int currentProjectId() { return getPreferences(MODE_PRIVATE).getInt("current_project_id",0); }
    private boolean hasActiveProject() { return getPreferences(MODE_PRIVATE).getBoolean("project_active", true); }
    private boolean premiumTestActive() { return getPreferences(MODE_PRIVATE).getBoolean("premium_test", false); }
    private boolean projectSetupComplete() { SharedPreferences p=getPreferences(MODE_PRIVATE); return !p.contains("project_setup_complete") || p.getBoolean("project_setup_complete",true); }
    private void toggleProjectDetails() { if(!projectSetupComplete() || !hasActiveProject()) { editProject(); return; } if(modern() && !reducedMotion()) {
            android.transition.AutoTransition transition=new android.transition.AutoTransition();transition.setDuration(220);
            android.transition.TransitionManager.beginDelayedTransition(content,transition);
        }
        detailsExpanded=!detailsExpanded; applyProjectExpansion(); }
    private void applyProjectExpansion() {
        boolean active=hasActiveProject(), setup=projectSetupComplete();
        boolean expanded=active && setup && detailsExpanded;
        detailsPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        activeControlsPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        compactSummary.setVisibility(modern() && setup && active ? View.GONE : View.VISIBLE);
        if(compactMetrics!=null) compactMetrics.setVisibility(active && setup && !expanded ? View.VISIBLE : View.GONE);
        String prefix=running ? s("LIVE PROJECT","AKTIVES PROJEKT") : s("CURRENT PROJECT","AKTUELLES PROJEKT");
        projectHeader.setText(active ? (setup ? prefix+(detailsExpanded ? s("  ·  TAP TO COLLAPSE","  ·  ZUM SCHLIESSEN ANTIPPEN") : s("  ·  TAP TO EXPAND","  ·  ZUM ÖFFNEN ANTIPPEN")) : s("FIRST PROJECT  ·  TAP TO SET UP","ERSTES PROJEKT  ·  ZUM EINRICHTEN ANTIPPEN")) : s("PROJECTS","PROJEKTE"));
        if(modern()) {
            projectHeader.setText(active ? (setup ? (running ? s("Live project","Aktives Projekt") : s("Current project","Aktuelles Projekt")) : s("First project","Erstes Projekt")) : s("Projects","Projekte"));
            if(projectChevron!=null) { projectChevron.animate().cancel(); if(reducedMotion()) projectChevron.setRotation(expanded?180:0); else projectChevron.animate().rotation(expanded?180:0).setDuration(220).start(); }
        }
        String description=!active || !setup ? s("Set up a project", "Projekt einrichten") : (expanded ? s("Collapse ","Schließen: ") : s("Expand ","Öffnen: ")) + projectName.getText();
        ((ProjectCard)trackerCard).setExpansionState(active && setup,expanded,description);
        projectHeader.setTextColor(Color.rgb(20,108,148));
        if(compactLiveDot!=null) compactLiveDot.setVisibility(running ? View.VISIBLE : View.GONE);
        if(projectsList!=null) projectsList.setVisibility(detailsExpanded && !modern() ? View.GONE : View.VISIBLE);
        if(projectActionsRow!=null) projectActionsRow.setVisibility((detailsExpanded && !modern()) || !premiumTestActive() ? View.GONE : View.VISIBLE);
        if(status!=null) status.setVisibility(View.GONE);
    }
    private void renderProject() {
        boolean active=hasActiveProject();
        if(active && !projectSetupComplete()) { detailsExpanded=false; projectHeader.setText(s("FIRST PROJECT  ·  TAP TO SET UP","ERSTES PROJEKT  ·  ZUM EINRICHTEN ANTIPPEN")); projectName.setText(s("Set up your first project","Richte dein erstes Projekt ein")); compactSummary.setText(s("Add a name and hourly rate","Name und Stundensatz hinzufügen")); rateLabel.setText(s("Set up your project before starting the timer.","Richte das Projekt vor dem Starten ein.")); }
        else if(active) { projectName.setText(getPreferences(MODE_PRIVATE).getString("project_name", "My first project")); rateLabel.setText(s("Rate: ", "Satz: ") + money(rate()) + s(" / hour", " / Stunde")); }
        else { detailsExpanded=false; projectHeader.setText(s("PROJECTS","PROJEKTE")); projectName.setText(s("No active project","Kein aktives Projekt")); rateLabel.setText(s("Create a new project to start tracking.","Erstelle ein Projekt, um Zeit zu erfassen.")); }
        applyProjectExpansion();
        renderOtherProjectCards();
    }
    private String projectMoney(double value,String code) {
        return money(value,code);
    }
    private void renderOtherProjectCards() {
        if(projectsList==null) return;
        projectsList.removeAllViews(); SharedPreferences p=getPreferences(MODE_PRIVATE); int shown=0, hidden=0;
        for(int i=0;i<p.getInt("project_count",0);i++) if(i!=currentProjectId() && !p.getBoolean("project_"+i+"_completed",false)) {
            if(!modern() && shown>=4) { hidden++; continue; } final int id=i; LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setPadding(dp(modern()?20:14),dp(modern()?10:7),dp(14),dp(modern()?10:7)); item.setBackground(round(Color.WHITE,16)); item.setElevation(dp(1));
            if(modern()) { android.graphics.drawable.LayerDrawable background=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{round(Color.WHITE,16),round(Color.rgb(83,183,177),3)}); background.setLayerSize(1,dp(3),dp(26)); background.setLayerGravity(1,Gravity.LEFT|Gravity.CENTER_VERTICAL); background.setLayerInsetLeft(1,dp(9)); item.setBackground(background); }
            TextView name=text(p.getString("project_"+i+"_name","Project"),15,Color.rgb(16,42,67)); item.addView(name);
            double projectRate=Double.longBitsToDouble(p.getLong("project_"+i+"_rate",Double.doubleToRawLongBits(0))); long seconds=p.getLong("project_"+i+"_seconds",0L); TextView summary=text(format(seconds)+"  ·  "+projectMoney(seconds*projectRate/3600.0,p.getString("project_"+i+"_currency","EUR")),12,Color.rgb(20,108,148)); summary.setPadding(0,dp(1),0,0); item.addView(summary);
            item.setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){ selectProject(id); }}); attachPressAnimation(item); LinearLayout.LayoutParams itemParams=matchWrap(); itemParams.topMargin=dp(8); projectsList.addView(item,itemParams); shown++;
        }
        if(hidden>0) { TextView more=text("+ "+hidden+" more projects · tap Manage",13,Color.rgb(20,108,148)); more.setGravity(Gravity.CENTER); more.setPadding(0,dp(8),0,0); more.setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){ showProjectManager(); }}); projectsList.addView(more,matchWrap()); }
    }
    private void renderPremium() { if(premiumTestActive()) { int count=getPreferences(MODE_PRIVATE).getInt("completed_project_count",0); premiumLabel.setText(s("PREMIUM TEST ACTIVE\n", "PREMIUM-TEST AKTIV\n")+count+s(count==1 ? " completed project · Reports · Backup" : " completed projects · Reports · Backup",count==1 ? " abgeschlossenes Projekt · Berichte · Backup" : " abgeschlossene Projekte · Berichte · Backup")); } }
    private void confirmEndProject() {
        if(running || !hasActiveProject()) return;
        new AlertDialog.Builder(this).setTitle("End this project?").setMessage("The project will be saved in your history. You can start a new project afterwards.").setNegativeButton("Cancel",null).setPositiveButton("End Project",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){ endProject(); }}).show();
    }
    private void endProject() {
        int id=currentProjectId(); int count=getPreferences(MODE_PRIVATE).getInt("completed_project_count",0);
        SharedPreferences.Editor e=getPreferences(MODE_PRIVATE).edit(); e.putString("completed_project_"+count+"_name",projectName.getText().toString()).putLong("completed_project_"+count+"_seconds",savedSeconds).putLong("completed_project_"+count+"_rate",Double.doubleToRawLongBits(rate())).putString("completed_project_"+count+"_currency",currency()).putInt("completed_project_count",count+1).putBoolean("project_"+id+"_completed",true).apply();
        int next=findNextActiveProject(id); if(next>=0) { selectProject(next); status.setText("Project ended. Switched to another project."); } else { savedSeconds=0L; running=false; startedAt=0L; getPreferences(MODE_PRIVATE).edit().putBoolean("project_active",false).apply(); saveTimerState(); renderProject(); setTimerVisualState(false,true); status.setText("Project ended and saved in history."); }
        renderPremium();
    }
    private String formatStarted(long time) { return new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(time)); }
    private void startLogoAnimation() {
        if(modern() || reducedMotion() || !foreground) return;
        if (brandLogo == null) return;
        if (logoAnimation != null) logoAnimation.cancel();
        ObjectAnimator turn = ObjectAnimator.ofFloat(brandLogo, "rotationY", -7f, 7f);
        turn.setDuration(2400); turn.setRepeatCount(ValueAnimator.INFINITE); turn.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(brandLogo, "scaleX", 1f, 1.08f);
        pulseX.setDuration(1800); pulseX.setRepeatCount(ValueAnimator.INFINITE); pulseX.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(brandLogo, "scaleY", 1f, 1.08f);
        pulseY.setDuration(1800); pulseY.setRepeatCount(ValueAnimator.INFINITE); pulseY.setRepeatMode(ValueAnimator.REVERSE);
        logoAnimation = new AnimatorSet(); logoAnimation.playTogether(turn, pulseX, pulseY); logoAnimation.start();
    }
    private void startLiveAnimation() {
        if(reducedMotion() || !foreground) return;
        if (liveDot == null) return;
        if (liveAnimation != null) liveAnimation.cancel();
        ObjectAnimator pulseX=ObjectAnimator.ofFloat(liveDot,"scaleX",1f,1.45f); pulseX.setDuration(720); pulseX.setRepeatCount(ValueAnimator.INFINITE); pulseX.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator pulseY=ObjectAnimator.ofFloat(liveDot,"scaleY",1f,1.45f); pulseY.setDuration(720); pulseY.setRepeatCount(ValueAnimator.INFINITE); pulseY.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator glow=ObjectAnimator.ofFloat(liveDot,"alpha",1f,.55f); glow.setDuration(720); glow.setRepeatCount(ValueAnimator.INFINITE); glow.setRepeatMode(ValueAnimator.REVERSE);
        liveAnimation=new AnimatorSet(); liveAnimation.playTogether(pulseX,pulseY,glow); liveAnimation.start();
    }
    private void stopLiveAnimation() { if(liveAnimation!=null) liveAnimation.cancel(); if(liveDot!=null){liveDot.setScaleX(1f);liveDot.setScaleY(1f);liveDot.setAlpha(1f);} }
    private void startCompactLiveAnimation() {
        if(reducedMotion() || !foreground) return;
        if(compactLiveDot==null) return;
        if(compactLiveAnimation!=null) compactLiveAnimation.cancel();
        ObjectAnimator blink=ObjectAnimator.ofFloat(compactLiveDot,"alpha",1f,.35f); blink.setDuration(600); blink.setRepeatCount(ValueAnimator.INFINITE); blink.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator pulseX=ObjectAnimator.ofFloat(compactLiveDot,"scaleX",1f,1.12f); pulseX.setDuration(600); pulseX.setRepeatCount(ValueAnimator.INFINITE); pulseX.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator pulseY=ObjectAnimator.ofFloat(compactLiveDot,"scaleY",1f,1.12f); pulseY.setDuration(600); pulseY.setRepeatCount(ValueAnimator.INFINITE); pulseY.setRepeatMode(ValueAnimator.REVERSE);
        compactLiveAnimation=new AnimatorSet(); compactLiveAnimation.playTogether(blink,pulseX,pulseY); compactLiveAnimation.start();
    }
    private void stopCompactLiveAnimation() { if(compactLiveAnimation!=null)compactLiveAnimation.cancel(); if(compactLiveDot!=null){compactLiveDot.setAlpha(1f);compactLiveDot.setScaleX(1f);compactLiveDot.setScaleY(1f);} }
    private void startCardBreathing() {
        if(reducedMotion() || !foreground || modern()) return;
        if (trackerCard == null) return;
        if (cardBreathingAnimation != null) cardBreathingAnimation.cancel();
        ObjectAnimator lift=ObjectAnimator.ofFloat(trackerCard,"translationZ",0f,dp(5)); lift.setDuration(1400); lift.setRepeatCount(ValueAnimator.INFINITE); lift.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator tilt=ObjectAnimator.ofFloat(trackerCard,"rotationX",0f,1.1f); tilt.setDuration(1400); tilt.setRepeatCount(ValueAnimator.INFINITE); tilt.setRepeatMode(ValueAnimator.REVERSE);
        trackerCard.setCameraDistance(dp(8000)); cardBreathingAnimation=new AnimatorSet(); cardBreathingAnimation.playTogether(lift,tilt); cardBreathingAnimation.start();
    }
    private void stopCardBreathing() { if(cardBreathingAnimation!=null)cardBreathingAnimation.cancel(); if(trackerCard!=null){trackerCard.setTranslationZ(0f);trackerCard.setRotationX(0f);} }
    private void attachPressAnimation(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent event) {
            if(reducedMotion()) return false;
            if(event.getAction()==MotionEvent.ACTION_DOWN){ if(modern())v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); v.animate().scaleX(.97f).scaleY(.97f).translationZ(0f).setDuration(90).start(); }
            else if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL){ v.animate().scaleX(1f).scaleY(1f).translationZ(dp(2)).setDuration(150).setInterpolator(new DecelerateInterpolator()).start(); }
            return false;
        }});
    }
    private void playEntryAnimations() { animateCard(trackerCard, 80); animateCard(statsRow, 180); animateCard(status, 250); animateCard(premiumCard, 330); }
    private void animateCard(View view, long delay) {
        if (view == null || reducedMotion()) return;
        view.setAlpha(0f); view.setTranslationY(dp(20)); view.setScaleX(.96f); view.setScaleY(.96f); view.setRotationX(5f); view.setCameraDistance(dp(8000));
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).rotationX(0f).setStartDelay(delay).setDuration(520).setInterpolator(new DecelerateInterpolator()).start();
    }
    private void saveTimerState() {
        SharedPreferences p=getPreferences(MODE_PRIVATE); SharedPreferences.Editor e=p.edit();
        e.putLong("saved_seconds",savedSeconds).putBoolean("timer_running",running).putLong("timer_started_at",startedAt);
        int id=p.getInt("current_project_id",0); if(p.getInt("project_count",0)>0) e.putString("project_"+id+"_name",p.getString("project_name","My first project")).putLong("project_"+id+"_rate",p.getLong("hourly_rate",Double.doubleToRawLongBits(25.0))).putLong("project_"+id+"_seconds",savedSeconds).putString("project_"+id+"_currency",p.getString("currency_code","EUR")).putBoolean("project_"+id+"_completed",p.getBoolean("project_"+id+"_completed",false));
        e.apply();
    }
    private int findNextActiveProject(int excludedId) { SharedPreferences p=getPreferences(MODE_PRIVATE); for(int i=0;i<p.getInt("project_count",0);i++) if(i!=excludedId && !p.getBoolean("project_"+i+"_completed",false)) return i; return -1; }
    private void selectProject(int id) {
        if(running) { Toast.makeText(this,"Stop the timer before changing project.",Toast.LENGTH_LONG).show(); return; }
        saveTimerState(); SharedPreferences p=getPreferences(MODE_PRIVATE); savedSeconds=p.getLong("project_"+id+"_seconds",0L); startedAt=0L; running=false;
        p.edit().putInt("current_project_id",id).putString("project_name",p.getString("project_"+id+"_name","Project")).putLong("hourly_rate",p.getLong("project_"+id+"_rate",Double.doubleToRawLongBits(25.0))).putString("currency_code",p.getString("project_"+id+"_currency",p.getString("default_currency","EUR"))).putLong("saved_seconds",savedSeconds).putBoolean("timer_running",false).putLong("timer_started_at",0L).putBoolean("project_active",true).apply();
        detailsExpanded=true; renderProject(); setTimerVisualState(false,true); updateTimer();
    }
    private void addProject() {
        if(!premiumTestActive()) { showPremium(); return; }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int p=dp(20); form.setPadding(p,0,p,0);
        final EditText name=new EditText(this); name.setHint("Project name"); form.addView(name);
        final EditText projectRate=new EditText(this); projectRate.setHint("Hourly rate in EUR"); projectRate.setInputType(2|8192); form.addView(projectRate);
        new AlertDialog.Builder(this).setTitle("Add project").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Add",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){
            String n=name.getText().toString().trim(); if(n.length()==0)n="New project"; double r=rate(); try{r=Double.parseDouble(projectRate.getText().toString().replace(",","."));}catch(Exception ignored){} if(r<0)r=0;
            boolean noActiveProject=!hasActiveProject(); saveTimerState(); SharedPreferences p=getPreferences(MODE_PRIVATE); int id=p.getInt("project_count",0); SharedPreferences.Editor e=p.edit(); e.putInt("project_count",id+1).putString("project_"+id+"_name",n).putLong("project_"+id+"_rate",Double.doubleToRawLongBits(r)).putLong("project_"+id+"_seconds",0L).putBoolean("project_"+id+"_completed",false);
            String code=p.getString("default_currency","EUR"); e.putString("project_"+id+"_currency",code);
            if(noActiveProject) { e.putInt("current_project_id",id).putString("project_name",n).putLong("hourly_rate",Double.doubleToRawLongBits(r)).putString("currency_code",code).putLong("saved_seconds",0L).putBoolean("timer_running",false).putLong("timer_started_at",0L).putBoolean("project_active",true); savedSeconds=0L; startedAt=0L; running=false; detailsExpanded=true; }
            e.apply(); renderProject(); if(noActiveProject) { setTimerVisualState(false,true); updateTimer(); } Toast.makeText(MainActivity.this,running ? "Project added. The active timer keeps running." : "Project added.",Toast.LENGTH_SHORT).show();
        }}).show();
    }
    private void showProjectManager() {
        if(!premiumTestActive()) { showPremium(); return; }
        if(running) { Toast.makeText(this,"Stop the timer before changing project.",Toast.LENGTH_LONG).show(); return; }
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); int p=dp(14); list.setPadding(p,0,p,p); SharedPreferences prefs=getPreferences(MODE_PRIVATE); int count=prefs.getInt("project_count",0); boolean found=false;
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Your active projects").setView(list).setNegativeButton("Close",null).create();
        for(int i=0;i<count;i++) if(!prefs.getBoolean("project_"+i+"_completed",false)) { found=true; final int id=i; Button item=new Button(this); item.setAllCaps(false); item.setText(prefs.getString("project_"+i+"_name","Project")+"  ·  "+format(prefs.getLong("project_"+i+"_seconds",0L))); item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); item.setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){ dialog.dismiss(); selectProject(id); }}); list.addView(item,matchWrap()); }
        if(!found) list.addView(text("No active projects. Add a new project.",14,Color.DKGRAY)); dialog.show();
    }
    private void confirmDeleteCurrentProject() {
        if(running) { Toast.makeText(this,s("Stop the timer before deleting a project.","Stoppe den Timer, bevor du ein Projekt löschst."),Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle(s("Delete project?","Projekt löschen?")).setMessage(s("This active project and its tracked time will be permanently deleted.","Dieses aktive Projekt und die erfasste Zeit werden endgültig gelöscht.")).setNegativeButton(s("Cancel","Abbrechen"),null).setPositiveButton(s("Delete","Löschen"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ deleteCurrentProject(); }}).show();
    }
    private void deleteCurrentProject() {
        SharedPreferences p=getPreferences(MODE_PRIVATE); int remove=currentProjectId(), count=p.getInt("project_count",0); SharedPreferences.Editor e=p.edit();
        for(int i=remove;i<count-1;i++){ int next=i+1; e.putString("project_"+i+"_name",p.getString("project_"+next+"_name","Project")).putLong("project_"+i+"_rate",p.getLong("project_"+next+"_rate",0)).putLong("project_"+i+"_seconds",p.getLong("project_"+next+"_seconds",0)).putString("project_"+i+"_currency",p.getString("project_"+next+"_currency","EUR")).putBoolean("project_"+i+"_completed",p.getBoolean("project_"+next+"_completed",false)); }
        int last=count-1; e.remove("project_"+last+"_name").remove("project_"+last+"_rate").remove("project_"+last+"_seconds").remove("project_"+last+"_currency").remove("project_"+last+"_completed");
        if(count<=1){ e.putInt("project_count",0).putBoolean("project_active",false).putLong("saved_seconds",0).putBoolean("timer_running",false).putLong("timer_started_at",0); savedSeconds=0; startedAt=0; running=false; }
        else { int next=Math.min(remove,count-2); String name=p.getString("project_"+(next>=remove?next+1:next)+"_name","Project"); /* records are shifted, values below are read after shift by choosing temporary values */ e.putInt("project_count",count-1).putInt("current_project_id",next).putString("project_name",name).putLong("hourly_rate",p.getLong("project_"+(next>=remove?next+1:next)+"_rate",0)).putString("currency_code",p.getString("project_"+(next>=remove?next+1:next)+"_currency","EUR")).putLong("saved_seconds",p.getLong("project_"+(next>=remove?next+1:next)+"_seconds",0)).putBoolean("project_active",true); savedSeconds=p.getLong("project_"+(next>=remove?next+1:next)+"_seconds",0); startedAt=0; running=false; }
        e.apply(); detailsExpanded=false; renderProject(); setTimerVisualState(false,true); updateTimer(); Toast.makeText(this,s("Project deleted.","Projekt gelöscht."),Toast.LENGTH_SHORT).show();
    }
    private double rate() { return Double.longBitsToDouble(getPreferences(MODE_PRIVATE).getLong("hourly_rate", Double.doubleToRawLongBits(25.0))); }
    private String currency() { return getPreferences(MODE_PRIVATE).getString("currency_code", getPreferences(MODE_PRIVATE).getString("default_currency", "EUR")); }
    private String symbol(String code) { return "USD".equals(code) ? "$" : ("GBP".equals(code) ? "£" : "€"); }
    private String money(double value) { return money(value, currency()); }
    private String money(double value, String code) {
        java.text.NumberFormat format=java.text.NumberFormat.getCurrencyInstance(german()?Locale.GERMANY:Locale.US);
        try { format.setCurrency(java.util.Currency.getInstance(code)); } catch(IllegalArgumentException | NullPointerException ignored) { format.setCurrency(java.util.Currency.getInstance("EUR")); }
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
    private boolean german() { return "de".equals(getPreferences(MODE_PRIVATE).getString("language", Locale.getDefault().getLanguage().equals("de") ? "de" : "en")); }
    private String s(String en, String de) { return german() ? de : en; }
    private void editProject() {
        final boolean firstSetup=!projectSetupComplete(); final boolean creatingNew=!hasActiveProject() || firstSetup;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int p=dp(20); form.setPadding(p,0,p,0);
        final EditText name = new EditText(this); name.setHint("Project name"); name.setText(creatingNew ? "" : projectName.getText()); form.addView(name);
        final EditText hourlyRate = new EditText(this); hourlyRate.setHint("Hourly rate in EUR (example: 25)"); hourlyRate.setInputType(2|8192); hourlyRate.setText(String.format(Locale.US,"%.2f",rate())); form.addView(hourlyRate);
        new AlertDialog.Builder(this).setTitle(firstSetup ? s("Set up your first project","Erstes Projekt einrichten") : (creatingNew ? s("Create new project","Neues Projekt erstellen") : s("Edit current project","Projekt bearbeiten"))).setMessage(firstSetup ? s("Add a project name and an hourly rate before you start tracking.","Füge einen Projektnamen und Stundensatz hinzu, bevor du die Zeiterfassung startest.") : (premiumTestActive() ? s("Premium test is active. You can create unlimited projects.","Premium-Test ist aktiv. Du kannst unbegrenzt Projekte erstellen.") : s("Free plan includes one active project.","Der Free-Plan enthält ein aktives Projekt."))).setView(form).setNegativeButton(s("Cancel","Abbrechen"),null).setPositiveButton(s("Save","Speichern"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d,int w) {
            String n=name.getText().toString().trim(); double r=rate(); try { r=Double.parseDouble(hourlyRate.getText().toString().replace(",",".")); } catch(Exception ignored) {} if(n.length()==0)n="My first project"; if(r<0)r=0;
            if(creatingNew) { savedSeconds=0L; startedAt=0L; running=false; }
            getPreferences(MODE_PRIVATE).edit().putString("project_name",n).putLong("hourly_rate",Double.doubleToRawLongBits(r)).putBoolean("project_active",true).putBoolean("project_setup_complete",true).apply(); saveTimerState(); renderProject(); setTimerVisualState(running,true); updateTimer();
        }}).show();
    }
    private void showSettings() {
        final Dialog dialog=new Dialog(this); LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18),dp(18),dp(18),dp(18)); panel.setBackground(round(Color.WHITE,24));
        LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); TextView icon=text("⚙",22,Color.WHITE); icon.setGravity(Gravity.CENTER); icon.setBackground(round(Color.rgb(20,108,148),13)); heading.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42))); LinearLayout headingText=new LinearLayout(this); headingText.setOrientation(LinearLayout.VERTICAL); headingText.setPadding(dp(12),0,0,0); TextView title=text(s("Settings","Einstellungen"),22,Color.rgb(16,42,67)); headingText.addView(title); TextView hint=text(s("Make the app yours","Passe die App an dich an"),12,Color.rgb(20,108,148)); headingText.addView(hint); heading.addView(headingText); panel.addView(heading,matchWrap());
        Space space=new Space(this); panel.addView(space,new LinearLayout.LayoutParams(1,dp(14)));
        panel.addView(settingRow("✧",s("Appearance","Darstellung"),modern()?s("Premium 3D · switch to classic","Premium 3D · Klassisch wählen"):s("Classic · try Premium 3D","Klassisch · Premium 3D testen"),new View.OnClickListener(){public void onClick(View v){dialog.dismiss();showAppearance();}}));
        panel.addView(settingRow("◉",s("Language","Sprache"),s("English or Deutsch","English oder Deutsch"),new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showLanguageDialog(); }}));
        panel.addView(settingRow("¤",s("Currency","Währung"),s("EUR, USD or GBP","EUR, USD oder GBP"),new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showCurrencyDialog(); }}));
        panel.addView(settingRow("↗",s("Reports","Berichte"),s("Export CSV or PDF","CSV oder PDF exportieren"),new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showReportsDialog(); }}));
        panel.addView(settingRow("⌁",s("Encrypted backup","Verschlüsseltes Backup"),s("Secure local export and import","Lokaler sicherer Export und Import"),new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); if(premiumTestActive()) showBackupDialog(); else showPremium(); }}));
        panel.addView(settingRow("◷",s("Project history","Projektverlauf"),s("Completed projects","Abgeschlossene Projekte"),new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showHistoryDialog(); }}));
        panel.addView(settingRow("?",s("Help & privacy","Hilfe & Datenschutz"),"freelancerheaven.app@gmail.com",new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showHelpPrivacyDialog(); }}));
        TextView close=text(s("CLOSE","SCHLIESSEN"),12,Color.rgb(20,108,148)); close.setGravity(Gravity.CENTER); close.setPadding(0,dp(13),0,0); close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }}); panel.addView(close,matchWrap());
        ScrollView settingsScroll=new ScrollView(this); settingsScroll.addView(panel); dialog.setContentView(settingsScroll); Window window=dialog.getWindow(); if(window!=null){ window.setBackgroundDrawable(round(Color.TRANSPARENT,1)); window.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),ViewGroup.LayoutParams.WRAP_CONTENT); } dialog.show(); if(dialog.getWindow()!=null) dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private void showHelpPrivacyDialog() { new AlertDialog.Builder(this).setTitle(s("Help & privacy","Hilfe & Datenschutz")).setMessage("Freelancer Time Tracker · "+installedVersion()+"\nfreelancerheaven.app@gmail.com").setNegativeButton(s("Privacy policy","Datenschutz"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ openPrivacy(); }}).setPositiveButton(s("Email support","Support per E-Mail"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ openSupport(); }}).show(); }
    private View settingRow(String mark,String title,String detail,View.OnClickListener click) { LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(8),dp(12),dp(8)); row.setBackground(round(Color.rgb(243,248,250),16)); TextView badge=text(mark,18,Color.rgb(20,108,148)); badge.setGravity(Gravity.CENTER); row.addView(badge,new LinearLayout.LayoutParams(dp(32),dp(40))); LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(7),0,0,0); words.addView(text(title,15,Color.rgb(16,42,67))); words.addView(text(detail,11,Color.rgb(82,97,107))); row.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); TextView arrow=text("›",27,Color.rgb(20,108,148)); arrow.setGravity(Gravity.CENTER); row.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(40))); row.setOnClickListener(click); attachPressAnimation(row); LinearLayout.LayoutParams params=matchWrap(); params.topMargin=dp(7); row.setLayoutParams(params); return row; }
    private void showLanguageDialog() {
        final String[] values={"English","Deutsch"}; int selected=german()?1:0;
        new AlertDialog.Builder(this).setTitle(s("Language", "Sprache")).setSingleChoiceItems(values,selected,new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){ getPreferences(MODE_PRIVATE).edit().putString("language",which==1?"de":"en").apply(); d.dismiss(); recreate(); }}).show();
    }
    private void showCurrencyDialog() {
        final String[] values={"EUR (€)","USD ($)","GBP (£)"}; String code=currency(); int selected="USD".equals(code)?1:("GBP".equals(code)?2:0);
        new AlertDialog.Builder(this).setTitle(s("Currency", "Währung")).setSingleChoiceItems(values,selected,new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){ String next=which==1?"USD":(which==2?"GBP":"EUR"); SharedPreferences.Editor e=getPreferences(MODE_PRIVATE).edit().putString("default_currency",next).putString("currency_code",next); if(hasActiveProject()) e.putString("project_"+currentProjectId()+"_currency",next); e.apply(); d.dismiss(); updateTimer(); renderProject(); }}).setNegativeButton(s("Cancel","Abbrechen"),null).show();
    }
    private void showReportsDialog() {
        if(!premiumTestActive()) { showPremium(); return; }
        new AlertDialog.Builder(this).setTitle(s("Reports", "Berichte")).setMessage(s("Choose the projects to include.","Wähle die einzubeziehenden Projekte."))
            .setNegativeButton(s("Current project", "Aktuelles Projekt"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ chooseReportFormat(0); }})
            .setPositiveButton(s("All projects", "Alle Projekte"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ chooseReportFormat(1); }}).show();
    }
    private void chooseReportFormat(final int scope) {
        new AlertDialog.Builder(this).setTitle(s("Export report", "Bericht exportieren")).setItems(new String[]{"CSV","PDF"},new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){ pendingReportScope=scope; Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType(which==0?"text/csv":"application/pdf"); String stamp=new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss",Locale.US).format(new Date()); intent.putExtra(Intent.EXTRA_TITLE,"Freelancer-Heaven-Report-"+stamp+(which==0?".csv":".pdf")); startActivityForResult(intent,which==0?CREATE_CSV_REQUEST:CREATE_PDF_REQUEST); }}).show();
    }
    private void showHistoryDialog() {
        final SharedPreferences p=getPreferences(MODE_PRIVATE); int count=p.getInt("completed_project_count",0); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18),0,dp(18),dp(8));
        if(count==0) list.addView(text(s("No completed projects yet.","Noch keine abgeschlossenen Projekte."),14,Color.DKGRAY));
        for(int i=0;i<count;i++){ final int index=i; long seconds=p.getLong("completed_project_"+i+"_seconds",0); double r=Double.longBitsToDouble(p.getLong("completed_project_"+i+"_rate",Double.doubleToRawLongBits(0))); String code=p.getString("completed_project_"+i+"_currency","EUR"); Button row=new Button(this); row.setAllCaps(false); row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); row.setText(p.getString("completed_project_"+i+"_name","Project")+"\n"+format(seconds)+" · "+money(seconds*r/3600.0,code)); row.setOnLongClickListener(new View.OnLongClickListener(){ @Override public boolean onLongClick(View v){ confirmDeleteHistory(index); return true; }}); list.addView(row,matchWrap()); }
        new AlertDialog.Builder(this).setTitle(s("Completed projects", "Abgeschlossene Projekte")).setMessage(s("Hold a project to delete it.","Projekt gedrückt halten, um es zu löschen.")).setView(list).setPositiveButton(s("Close", "Schließen"),null).show();
    }
    private void confirmDeleteHistory(final int index) { new AlertDialog.Builder(this).setTitle(s("Delete project?", "Projekt löschen?")).setMessage(s("This completed project will be permanently deleted.","Dieses abgeschlossene Projekt wird endgültig gelöscht.")).setNegativeButton(s("Cancel","Abbrechen"),null).setPositiveButton(s("Delete","Löschen"),new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ SharedPreferences p=getPreferences(MODE_PRIVATE); int count=p.getInt("completed_project_count",0); SharedPreferences.Editor e=p.edit(); for(int i=index;i<count-1;i++){ e.putString("completed_project_"+i+"_name",p.getString("completed_project_"+(i+1)+"_name","Project")).putLong("completed_project_"+i+"_rate",p.getLong("completed_project_"+(i+1)+"_rate",0)).putLong("completed_project_"+i+"_seconds",p.getLong("completed_project_"+(i+1)+"_seconds",0)).putString("completed_project_"+i+"_currency",p.getString("completed_project_"+(i+1)+"_currency","EUR")); } e.remove("completed_project_"+(count-1)+"_name").remove("completed_project_"+(count-1)+"_rate").remove("completed_project_"+(count-1)+"_seconds").remove("completed_project_"+(count-1)+"_currency").putInt("completed_project_count",Math.max(0,count-1)).apply(); renderPremium(); Toast.makeText(MainActivity.this,s("Project deleted.","Projekt gelöscht."),Toast.LENGTH_SHORT).show(); }}).show(); }
    private void openSupport() { try { startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:freelancerheaven.app@gmail.com?subject=Freelancer%20Time%20Tracker%20support"))); } catch(Exception e) { Toast.makeText(this,"freelancerheaven.app@gmail.com",Toast.LENGTH_LONG).show(); } }
    private void openPrivacy() { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://freelancerheavenapp.github.io/freelancer-heaven/privacy/"))); } catch(Exception e) { Toast.makeText(this,"Privacy policy link will be available at launch.",Toast.LENGTH_LONG).show(); } }
    private void showPremium() {
        if(premiumTestActive()) { showBackupDialog(); return; }
        new AlertDialog.Builder(this).setTitle("Freelancer Time Tracker Premium").setMessage("€1.40 per month\n\nPremium unlocks unlimited projects, reports and backup.\n\nThis is a test APK. No money will be charged.").setNegativeButton("Not now",null).setPositiveButton("Unlock Test Premium",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int which){ getPreferences(MODE_PRIVATE).edit().putBoolean("premium_test",true).apply(); renderPremium(); renderProject(); Toast.makeText(MainActivity.this,"Premium Test is now active.",Toast.LENGTH_LONG).show(); }}).show();
    }
    private void showBackupDialog() {
        new AlertDialog.Builder(this).setTitle("Encrypted Backup").setMessage("Create or restore a local encrypted backup. Choose a PIN with 4–8 digits. A running timer is saved and paused before export.").setNegativeButton("Close",null).setNeutralButton("Import",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ beginImport(); }}).setPositiveButton("Export",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ showExportPinDialog(); }}).show();
    }
    private void showExportPinDialog() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int p=dp(20); form.setPadding(p,0,p,0);
        final EditText pin=new EditText(this); pin.setHint("PIN: 4–8 digits"); pin.setInputType(2|16); form.addView(pin);
        final EditText confirm=new EditText(this); confirm.setHint("Confirm PIN"); confirm.setInputType(2|16); form.addView(confirm);
        new AlertDialog.Builder(this).setTitle("Encrypt backup").setMessage("Keep this PIN safe. It cannot be recovered.").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Continue",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ String first=pin.getText().toString(); String second=confirm.getText().toString(); if(!validPin(first) || !first.equals(second)){ Toast.makeText(MainActivity.this,"PIN must match and contain 4–8 digits.",Toast.LENGTH_LONG).show(); return; } pendingExportPin=first; pauseTimerForBackup(); Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType("application/octet-stream"); intent.putExtra(Intent.EXTRA_TITLE,"Freelancer-Heaven-Time-Tracker-Backup-"+new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss",Locale.US).format(new Date())+".fhtbackup"); startActivityForResult(intent,CREATE_BACKUP_REQUEST); }}).show();
    }
    private boolean validPin(String pin) { return pin!=null && pin.matches("[0-9]{4,8}"); }
    private void pauseTimerForBackup() { if(running){ savedSeconds+=(System.currentTimeMillis()-startedAt)/1000; running=false; startedAt=0L; saveTimerState(); setTimerVisualState(false,true); updateTimer(); } }
    private void beginImport() { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("application/octet-stream"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent,IMPORT_BACKUP_REQUEST); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        if(requestCode==CREATE_BACKUP_REQUEST){ Uri uri=data.getData(); try{ OutputStream stream=getContentResolver().openOutputStream(uri); if(stream==null) throw new Exception("Cannot write file"); writeEncryptedBackup(stream,pendingExportPin); stream.close(); Toast.makeText(this,"Encrypted backup created.",Toast.LENGTH_LONG).show(); }catch(Exception e){ Toast.makeText(this,"Backup export failed.",Toast.LENGTH_LONG).show(); } finally{ pendingExportPin=null; } }
        else if(requestCode==IMPORT_BACKUP_REQUEST) showImportPinDialog(data.getData());
        else if(requestCode==CREATE_CSV_REQUEST) { try { writeCsvReport(getContentResolver().openOutputStream(data.getData()),pendingReportScope); Toast.makeText(this,s("CSV report created.","CSV-Bericht erstellt."),Toast.LENGTH_LONG).show(); } catch(Exception e) { Toast.makeText(this,s("Report export failed.","Berichtexport fehlgeschlagen."),Toast.LENGTH_LONG).show(); } }
        else if(requestCode==CREATE_PDF_REQUEST) { try { writePdfReport(getContentResolver().openOutputStream(data.getData()),pendingReportScope); Toast.makeText(this,s("PDF report created.","PDF-Bericht erstellt."),Toast.LENGTH_LONG).show(); } catch(Exception e) { Toast.makeText(this,s("Report export failed.","Berichtexport fehlgeschlagen."),Toast.LENGTH_LONG).show(); } }
    }
    private ArrayList<String[]> reportRows(int scope) {
        ArrayList<String[]> rows=new ArrayList<String[]>(); SharedPreferences p=getPreferences(MODE_PRIVATE); int current=currentProjectId();
        for(int i=0;i<p.getInt("project_count",0);i++){ if(scope==0 && i!=current) continue; boolean done=p.getBoolean("project_"+i+"_completed",false); if(scope==1 && done) continue; long sec=p.getLong("project_"+i+"_seconds",0); double r=Double.longBitsToDouble(p.getLong("project_"+i+"_rate",0)); String code=p.getString("project_"+i+"_currency", i==current?currency():"EUR"); rows.add(new String[]{p.getString("project_"+i+"_name","Project"),done?s("Completed","Abgeschlossen"):s("Active","Aktiv"),String.format(Locale.US,"%.2f",r),code,format(sec),money(sec*r/3600.0,code)}); }
        if(scope==1) for(int i=0;i<p.getInt("completed_project_count",0);i++){ long sec=p.getLong("completed_project_"+i+"_seconds",0); double r=Double.longBitsToDouble(p.getLong("completed_project_"+i+"_rate",0)); String code=p.getString("completed_project_"+i+"_currency","EUR"); rows.add(new String[]{p.getString("completed_project_"+i+"_name","Project"),s("Completed","Abgeschlossen"),String.format(Locale.US,"%.2f",r),code,format(sec),money(sec*r/3600.0,code)}); }
        return rows;
    }
    private String csv(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    private void writeCsvReport(OutputStream stream,int scope) throws Exception { if(stream==null) throw new Exception("No output"); OutputStreamWriter out=new OutputStreamWriter(stream,"UTF-8"); out.write("Project,Status,Hourly rate,Currency,Total time,Estimated earnings\n"); for(String[] row:reportRows(scope)){ for(int i=0;i<row.length;i++){ if(i>0) out.write(","); out.write(csv(row[i])); } out.write("\n"); } out.flush(); out.close(); }
    private void writePdfReport(OutputStream stream,int scope) throws Exception { if(stream==null) throw new Exception("No output"); PdfDocument pdf=new PdfDocument(); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); ArrayList<String[]> rows=reportRows(scope); int pageNo=0,rowOnPage=0; PdfDocument.Page page=null; Canvas canvas=null; for(int i=0;i<Math.max(1,rows.size());i++){ if(i==0 || rowOnPage>=31){ if(page!=null) pdf.finishPage(page); page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNo).create()); canvas=page.getCanvas(); paint.setColor(Color.rgb(16,42,67)); paint.setTextSize(20); paint.setFakeBoldText(true); canvas.drawText("Freelancer Time Tracker",42,55,paint); paint.setFakeBoldText(false); paint.setTextSize(11); paint.setColor(Color.rgb(20,108,148)); canvas.drawText(s("Time & earnings report","Zeit- und Einnahmenbericht"),42,76,paint); paint.setColor(Color.DKGRAY); canvas.drawText("Project",42,108,paint); canvas.drawText("Status",235,108,paint); canvas.drawText("Time",330,108,paint); canvas.drawText("Earnings",430,108,paint); rowOnPage=0; }
            if(rows.size()>0){ String[] r=rows.get(i); int y=138+rowOnPage*21; paint.setTextSize(10); paint.setColor(Color.rgb(16,42,67)); canvas.drawText(trimPdf(r[0],27),42,y,paint); canvas.drawText(trimPdf(r[1],13),235,y,paint); canvas.drawText(r[4],330,y,paint); canvas.drawText(r[5],430,y,paint); rowOnPage++; }
        } if(page!=null) pdf.finishPage(page); pdf.writeTo(stream); pdf.close(); stream.close(); }
    private String trimPdf(String value,int length){ return value.length()>length?value.substring(0,length-1)+"…":value; }
    private void showImportPinDialog(final Uri uri) {
        final String key=backupLockKey(uri); long lockedUntil=getPreferences(MODE_PRIVATE).getLong(key+"_until",0L); if(System.currentTimeMillis()<lockedUntil){ long seconds=(lockedUntil-System.currentTimeMillis()+999)/1000; Toast.makeText(this,"Try again in "+seconds+" seconds.",Toast.LENGTH_LONG).show(); return; }
        final EditText pin=new EditText(this); pin.setHint("Backup PIN"); pin.setInputType(2|16); int p=dp(20); pin.setPadding(p,0,p,0);
        new AlertDialog.Builder(this).setTitle("Import encrypted backup").setMessage("Import replaces all local projects after confirmation.").setView(pin).setNegativeButton("Cancel",null).setPositiveButton("Unlock",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ String value=pin.getText().toString(); if(!validPin(value)){ Toast.makeText(MainActivity.this,"Enter a PIN with 4–8 digits.",Toast.LENGTH_LONG).show(); return; } try{ InputStream stream=getContentResolver().openInputStream(uri); if(stream==null) throw new Exception("Cannot read file"); JSONObject backup=readEncryptedBackup(stream,value); stream.close(); getPreferences(MODE_PRIVATE).edit().remove(key+"_failures").remove(key+"_until").apply(); confirmReplaceBackup(backup); }catch(Exception e){ registerFailedPin(key); } }}).show();
    }
    private void registerFailedPin(String key) { SharedPreferences p=getPreferences(MODE_PRIVATE); int failures=p.getInt(key+"_failures",0)+1; long delay=Math.min(600000L,1000L << Math.min(failures-1,9)); p.edit().putInt(key+"_failures",failures).putLong(key+"_until",System.currentTimeMillis()+delay).apply(); Toast.makeText(this,"Incorrect PIN. Try again in "+(delay/1000)+" seconds.",Toast.LENGTH_LONG).show(); }
    private String backupLockKey(Uri uri) { try{ MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] out=digest.digest(uri.toString().getBytes("UTF-8")); StringBuilder b=new StringBuilder("backup_lock_"); for(int i=0;i<8;i++) b.append(String.format(Locale.US,"%02x",out[i])); return b.toString(); }catch(Exception e){ return "backup_lock_default"; } }
    private void writeEncryptedBackup(OutputStream stream,String pin) throws Exception { byte[] salt=new byte[16],nonce=new byte[12]; SecureRandom random=new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,deriveBackupKey(pin,salt),new GCMParameterSpec(128,nonce)); byte[] encrypted=cipher.doFinal(createBackupJson().toString().getBytes("UTF-8")); DataOutputStream out=new DataOutputStream(stream); out.writeInt(0x46485442); out.writeInt(BACKUP_VERSION); out.writeInt(PBKDF2_ITERATIONS); out.writeInt(salt.length); out.write(salt); out.writeInt(nonce.length); out.write(nonce); out.writeInt(encrypted.length); out.write(encrypted); out.flush(); }
    private JSONObject readEncryptedBackup(InputStream stream,String pin) throws Exception { DataInputStream in=new DataInputStream(stream); if(in.readInt()!=0x46485442 || in.readInt()!=BACKUP_VERSION) throw new Exception("Unsupported backup"); int iterations=in.readInt(); int saltLength=in.readInt(); if(saltLength<16 || saltLength>64) throw new Exception("Invalid backup"); byte[] salt=new byte[saltLength]; in.readFully(salt); int nonceLength=in.readInt(); if(nonceLength<12 || nonceLength>32) throw new Exception("Invalid backup"); byte[] nonce=new byte[nonceLength]; in.readFully(nonce); int dataLength=in.readInt(); if(dataLength<16 || dataLength>5*1024*1024) throw new Exception("Invalid backup"); byte[] encrypted=new byte[dataLength]; in.readFully(encrypted); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,deriveBackupKey(pin,salt,iterations),new GCMParameterSpec(128,nonce)); return new JSONObject(new String(cipher.doFinal(encrypted),"UTF-8")); }
    private SecretKeySpec deriveBackupKey(String pin,byte[] salt) throws Exception { return deriveBackupKey(pin,salt,PBKDF2_ITERATIONS); }
    private SecretKeySpec deriveBackupKey(String pin,byte[] salt,int iterations) throws Exception { PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,iterations,256); byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); spec.clearPassword(); return new SecretKeySpec(key,"AES"); }
    private JSONObject createBackupJson() throws Exception {
        SharedPreferences p=getPreferences(MODE_PRIVATE); JSONObject root=new JSONObject(); root.put("schema",1); root.put("projectCount",p.getInt("project_count",0)); root.put("currentProjectId",p.getInt("current_project_id",0)); root.put("projectActive",p.getBoolean("project_active",true)); root.put("premiumTest",p.getBoolean("premium_test",false)); root.put("completedCount",p.getInt("completed_project_count",0));
        JSONArray projects=new JSONArray(); for(int i=0;i<p.getInt("project_count",0);i++){ JSONObject project=new JSONObject(); project.put("id",i); project.put("name",p.getString("project_"+i+"_name","Project")); project.put("rate",p.getLong("project_"+i+"_rate",Double.doubleToRawLongBits(0))); project.put("seconds",p.getLong("project_"+i+"_seconds",0L)); project.put("currency",p.getString("project_"+i+"_currency","EUR")); project.put("completed",p.getBoolean("project_"+i+"_completed",false)); projects.put(project); } root.put("projects",projects);
        JSONArray completed=new JSONArray(); for(int i=0;i<p.getInt("completed_project_count",0);i++){ JSONObject project=new JSONObject(); project.put("name",p.getString("completed_project_"+i+"_name","Project")); project.put("rate",p.getLong("completed_project_"+i+"_rate",Double.doubleToRawLongBits(0))); project.put("seconds",p.getLong("completed_project_"+i+"_seconds",0L)); project.put("currency",p.getString("completed_project_"+i+"_currency","EUR")); completed.put(project); } root.put("completed",completed); return root;
    }
    private void confirmReplaceBackup(final JSONObject backup) {
        new AlertDialog.Builder(this).setTitle("Replace local data?").setMessage("All projects currently on this phone will be replaced by this backup.").setNegativeButton("Cancel",null).setPositiveButton("Replace",new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface d,int w){ try{ replaceWithBackup(backup); Toast.makeText(MainActivity.this,"Backup restored.",Toast.LENGTH_LONG).show(); }catch(Exception e){ Toast.makeText(MainActivity.this,"Backup restore failed.",Toast.LENGTH_LONG).show(); } }}).show();
    }
    private void replaceWithBackup(JSONObject root) throws Exception {
        if(root.getInt("schema")!=1) throw new Exception("Unsupported backup"); JSONArray projects=root.getJSONArray("projects"); JSONArray completed=root.getJSONArray("completed"); if(projects.length()<1 || projects.length()>10000) throw new Exception("Invalid backup"); int current=root.getInt("currentProjectId"); if(current<0 || current>=projects.length()) throw new Exception("Invalid backup");
        SharedPreferences.Editor e=getPreferences(MODE_PRIVATE).edit().clear(); e.putInt("project_count",projects.length()).putInt("current_project_id",current).putBoolean("project_active",root.optBoolean("projectActive",true)).putBoolean("premium_test",root.optBoolean("premiumTest",false)).putInt("completed_project_count",completed.length()).putBoolean("timer_running",false).putLong("timer_started_at",0L);
        for(int i=0;i<projects.length();i++){ JSONObject project=projects.getJSONObject(i); e.putString("project_"+i+"_name",project.getString("name")).putLong("project_"+i+"_rate",project.getLong("rate")).putLong("project_"+i+"_seconds",project.getLong("seconds")).putString("project_"+i+"_currency",project.optString("currency","EUR")).putBoolean("project_"+i+"_completed",project.optBoolean("completed",false)); }
        for(int i=0;i<completed.length();i++){ JSONObject project=completed.getJSONObject(i); e.putString("completed_project_"+i+"_name",project.getString("name")).putLong("completed_project_"+i+"_rate",project.getLong("rate")).putLong("completed_project_"+i+"_seconds",project.getLong("seconds")).putString("completed_project_"+i+"_currency",project.optString("currency","EUR")); }
        JSONObject selected=projects.getJSONObject(current); savedSeconds=selected.getLong("seconds"); startedAt=0L; running=false; e.putString("project_name",selected.getString("name")).putLong("hourly_rate",selected.getLong("rate")).putString("currency_code",selected.optString("currency","EUR")).putString("default_currency",selected.optString("currency","EUR")).putLong("saved_seconds",savedSeconds).apply(); detailsExpanded=false; displayedEarnings=Double.NaN; renderPremium(); renderProject(); setTimerVisualState(false,false); updateTimer();
    }
    private String format(long s) { return String.format(Locale.getDefault(), "%02d:%02d:%02d",s/3600,(s%3600)/60,s%60); }
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onPause(){ foreground=false; saveTimerState(); handler.removeCallbacks(tick);
        if(motionWatchRegistered){unregisterReceiver(powerWatcher);getContentResolver().unregisterContentObserver(motionWatcher);motionWatchRegistered=false;}
        if(logoAnimation!=null)logoAnimation.cancel(); stopLiveAnimation();stopCompactLiveAnimation();stopCardBreathing();
        if(earningsAnimation!=null)earningsAnimation.cancel();if(buttonColorAnimation!=null)buttonColorAnimation.cancel();
        if(timerScene!=null)timerScene.pauseScene();super.onPause(); }
    @Override protected void onResume(){ super.onResume(); foreground=true;
        if(!motionWatchRegistered){registerReceiver(powerWatcher,new android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));getContentResolver().registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),false,motionWatcher);motionWatchRegistered=true;}
        handler.removeCallbacks(tick); if(running)handler.post(tick); if(timerScene!=null){timerScene.state(running,reducedMotion());timerScene.resumeScene();} startLogoAnimation(); if(running){startLiveAnimation();startCompactLiveAnimation();startCardBreathing();} }
    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(timerScene!=null)timerScene.release();if(logoAnimation!=null)logoAnimation.cancel();stopLiveAnimation();stopCompactLiveAnimation();stopCardBreathing();if(earningsAnimation!=null)earningsAnimation.cancel();super.onDestroy();}
}
