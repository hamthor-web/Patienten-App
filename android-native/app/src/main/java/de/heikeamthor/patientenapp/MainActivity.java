package de.heikeamthor.patientenapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements RecognitionListener {

  private static final int REQ_AUDIO = 1001;
  private static final int BG = Color.rgb(239, 236, 228);
  private static final int TEXT = Color.rgb(34, 48, 56);
  private static final int MUTED = Color.rgb(109, 119, 125);
  private static final int ACCENT = Color.rgb(83, 111, 119);

  private SpeechRecognizer recognizer;
  private TextToSpeech tts;
  private boolean ttsReady = false;
  private boolean listenAfterSpeech = false;
  private boolean testingSetup = false;
  private boolean gameListening = false;
  private boolean gameVisible = false;
  private boolean pendingFirstRound = false;
  private boolean usingOnDeviceRecognizer = false;
  private boolean recognizerFallbackTried = false;

  private SharedPreferences prefs;
  private LinearLayout root;
  private TextView setupStatus;
  private TextView testStatus;
  private TextView gameStatus;
  private Button testButton;
  private Button finishButton;
  private ColorGameView gameView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences("patienten_app", MODE_PRIVATE);
    initTts();
    initRecognizer();

    if (prefs.getBoolean("setup_complete", false) &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      showGame();
    } else {
      showSetup();
    }
  }

  private void initTts() {
    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        ttsReady = true;
        tts.setLanguage(Locale.GERMANY);
        tts.setSpeechRate(0.90f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
          @Override public void onStart(String utteranceId) {}
          @Override public void onError(String utteranceId) {
            if (utteranceId.startsWith("listen_") && listenAfterSpeech) {
              listenAfterSpeech = false;
              runOnUiThread(MainActivity.this::startListeningNow);
            }
          }
          @Override public void onDone(String utteranceId) {
            if (utteranceId.startsWith("listen_") && listenAfterSpeech) {
              listenAfterSpeech = false;
              runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(
                  MainActivity.this::startListeningNow, 250));
            }
          }
        });
        if (pendingFirstRound && gameVisible) {
          pendingFirstRound = false;
          runOnUiThread(MainActivity.this::beginGameListening);
        }
      }
    });
  }

  private void initRecognizer() {
    if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
    try {
      if (android.os.Build.VERSION.SDK_INT >= 31 &&
          SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        usingOnDeviceRecognizer = true;
      } else {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
      }
      recognizer.setRecognitionListener(this);
    } catch (Exception ex) {
      try {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        usingOnDeviceRecognizer = false;
        recognizer.setRecognitionListener(this);
      } catch (Exception ignored) {
        recognizer = null;
      }
    }
  }

  private TextView text(String value, int sp, boolean bold) {
    TextView tv = new TextView(this);
    tv.setText(value);
    tv.setTextColor(TEXT);
    tv.setTextSize(sp);
    tv.setPadding(dp(4), dp(8), dp(4), dp(8));
    if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
    return tv;
  }

  private Button button(String label) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(18);
    b.setTextColor(TEXT);
    b.setAllCaps(false);
    b.setMinHeight(dp(60));
    return b;
  }

  private void baseRoot() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(22), dp(22), dp(22), dp(22));
    root.setBackgroundColor(BG);
    setContentView(root);
  }

  private void showSetup() {
    gameVisible = false;
    stopListening();
    baseRoot();
    root.addView(text("Einrichtung", 30, true));
    root.addView(text("Diese Einrichtung ist nur einmal nötig. Danach startet die Übung direkt.", 18, false));

    root.addView(text("1   Mikrofon freigeben", 21, true));
    root.addView(text("Die App braucht das Mikrofon nur für die Spracherkennung.", 17, false));

    Button micButton = button("Mikrofon aktivieren");
    root.addView(micButton, fullWidth());
    setupStatus = text(hasMicPermission() ? "Mikrofon ist bereits freigegeben." : "Noch nicht aktiviert.", 17, false);
    setupStatus.setTextColor(hasMicPermission() ? ACCENT : MUTED);
    root.addView(setupStatus);

    root.addView(text("2   Sprachsteuerung testen", 21, true));
    root.addView(text("Beim Test sagen Sie einmal „Rot“.", 17, false));

    testButton = button("Sprachsteuerung testen");
    testButton.setEnabled(hasMicPermission() && recognizer != null);
    root.addView(testButton, fullWidth());

    testStatus = text(recognizer == null ? "Auf diesem Gerät ist keine Spracherkennung verfügbar." : "Noch nicht getestet.", 17, false);
    root.addView(testStatus);

    finishButton = button("Einrichtung abschließen");
    finishButton.setEnabled(false);
    root.addView(finishButton, fullWidth());

    micButton.setOnClickListener(v -> {
      if (hasMicPermission()) {
        setupStatus.setText("Mikrofon ist freigegeben.");
        testButton.setEnabled(recognizer != null);
        speak("Mikrofon ist aktiviert.");
      } else {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
      }
    });

    testButton.setOnClickListener(v -> {
      stopListening();
      testingSetup = true;
      gameListening = false;
      testStatus.setText("Ich höre gleich zu …");
      speakThenListen("Sagen Sie jetzt Rot.");
    });

    finishButton.setOnClickListener(v -> {
      prefs.edit().putBoolean("setup_complete", true).apply();
      speak("Einrichtung abgeschlossen.");
      showGame();
    });

    if (!hasMicPermission() && !prefs.getBoolean("permission_requested", false)) {
      prefs.edit().putBoolean("permission_requested", true).apply();
      root.post(() -> requestPermissions(
          new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO));
    }
  }

  private void showGame() {
    gameVisible = true;
    stopListening();
    baseRoot();
    root.addView(text("Farbspiel", 30, true));

    TextView mode = text(recognitionModeText(), 15, false);
    mode.setTextColor(MUTED);
    root.addView(mode);

    gameView = new ColorGameView();
    LinearLayout.LayoutParams gameLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    gameLp.setMargins(0, dp(8), 0, dp(8));
    root.addView(gameView, gameLp);

    LinearLayout controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER);

    Button newRound = button("Neue Runde");
    Button repeat = button("Frage wiederholen");
    controls.addView(newRound, new LinearLayout.LayoutParams(0, dp(64), 1f));
    controls.addView(repeat, new LinearLayout.LayoutParams(0, dp(64), 1f));
    root.addView(controls);

    gameStatus = text("Die erste Runde startet gleich.", 18, false);
    gameStatus.setGravity(Gravity.CENTER);
    root.addView(gameStatus);

    Button setupAgain = button("Einrichtung erneut öffnen");
    root.addView(setupAgain, fullWidth());

    newRound.setOnClickListener(v -> beginGameListening());
    repeat.setOnClickListener(v -> beginGameListening());
    setupAgain.setOnClickListener(v -> showSetup());

    gameView.setOnCorrectListener(() -> {
      gameStatus.setText("Ziel gefunden.");
      speak("Ziel gefunden.");
      new Handler(Looper.getMainLooper()).postDelayed(() -> {
        if (gameView != null) gameView.resetRound();
        if (gameStatus != null) gameStatus.setText("Bereit für die nächste Runde.");
      }, 1100);
    });

    gameView.post(() -> {
      if (!gameVisible) return;
      if (ttsReady) beginGameListening();
      else pendingFirstRound = true;
    });
  }

  private void beginGameListening() {
    if (!hasMicPermission()) {
      prefs.edit().putBoolean("setup_complete", false).apply();
      showSetup();
      return;
    }
    if (recognizer == null) {
      gameStatus.setText("Spracherkennung ist auf diesem Gerät nicht verfügbar.");
      return;
    }
    stopListening();
    testingSetup = false;
    gameListening = true;
    gameStatus.setText("Ich höre gleich zu …");
    speakThenListen("Nennen Sie eine Farbe. Rot, Blau oder Grün.");
  }

  private void speak(String message) {
    if (!ttsReady) return;
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "say_" + System.nanoTime());
  }

  private void speakThenListen(String message) {
    if (!ttsReady) {
      startListeningNow();
      return;
    }
    listenAfterSpeech = true;
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "listen_" + System.nanoTime());
  }

  private void startListeningNow() {
    if (recognizer == null || !hasMicPermission() || isFinishing() || isDestroyed()) return;
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE");
    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
    try {
      recognizer.cancel();
      recognizer.startListening(intent);
      if (testingSetup && testStatus != null) testStatus.setText("Ich höre zu … sagen Sie „Rot“.");
      if (gameListening && gameStatus != null) gameStatus.setText("Ich höre zu …");
    } catch (Exception ex) {
      statusMessage("Spracherkennung konnte nicht gestartet werden.");
    }
  }

  private void stopListening() {
    listenAfterSpeech = false;
    if (recognizer != null) {
      try { recognizer.cancel(); } catch (Exception ignored) {}
    }
  }

  private String recognizedColor(List<String> results) {
    if (results == null) return null;
    for (String s : results) {
      String x = s.toLowerCase(Locale.GERMAN).trim();
      if (x.contains("rot")) return "rot";
      if (x.contains("blau")) return "blau";
      if (x.contains("grün") || x.contains("gruen") || x.contains("grun")) return "grün";
    }
    return null;
  }

  private void handleRecognition(List<String> results) {
    String color = recognizedColor(results);

    if (testingSetup) {
      testingSetup = false;
      if ("rot".equals(color)) {
        testStatus.setText("Sprachsteuerung funktioniert. „Rot“ wurde erkannt.");
        testStatus.setTextColor(ACCENT);
        finishButton.setEnabled(true);
        speak("Sprachsteuerung funktioniert.");
      } else {
        testStatus.setText("„Rot“ wurde noch nicht erkannt. Bitte noch einmal testen.");
      }
      return;
    }

    if (gameListening) {
      gameListening = false;
      if (color == null) {
        gameStatus.setText("Keine Farbe erkannt. Bitte noch einmal versuchen.");
        speak("Rot, Blau oder Grün?");
        return;
      }
      int c = "rot".equals(color) ? Color.rgb(200,75,75) :
              "blau".equals(color) ? Color.rgb(79,120,184) :
              Color.rgb(79,138,98);
      gameView.startRound(c);
      gameStatus.setText("Jetzt den grauen Kreis zum farbigen Ziel schieben.");
      speak(("rot".equals(color) ? "Rot" : "blau".equals(color) ? "Blau" : "Grün") +
          ". Schieben Sie den Kreis zum passenden Ziel.");
    }
  }

  private boolean hasMicPermission() {
    return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
  }

  private LinearLayout.LayoutParams fullWidth() {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
    lp.setMargins(0, dp(6), 0, dp(6));
    return lp;
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private String recognitionModeText() {
    if (usingOnDeviceRecognizer) {
      return "Spracherkennung: auf diesem Gerät";
    }
    return "Spracherkennung: Android-Dienst";
  }

  private void statusMessage(String msg) {
    if (testingSetup && testStatus != null) testStatus.setText(msg);
    else if (gameStatus != null) gameStatus.setText(msg);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_AUDIO) {
      boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
      if (setupStatus != null) {
        setupStatus.setText(ok ? "Mikrofon ist freigegeben." :
            "Mikrofon wurde nicht freigegeben. Sie können die Freigabe erneut versuchen.");
        setupStatus.setTextColor(ok ? ACCENT : MUTED);
      }
      if (testButton != null) testButton.setEnabled(ok && recognizer != null);
      if (ok) speak("Mikrofon ist aktiviert.");
    }
  }

  @Override public void onReadyForSpeech(Bundle params) {}
  @Override public void onBeginningOfSpeech() {}
  @Override public void onRmsChanged(float rmsdB) {}
  @Override public void onBufferReceived(byte[] buffer) {}
  @Override public void onEndOfSpeech() {}
  @Override public void onEvent(int eventType, Bundle params) {}
  @Override public void onPartialResults(Bundle partialResults) {}

  @Override
  public void onError(int error) {
    if (usingOnDeviceRecognizer && !recognizerFallbackTried &&
        (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
         error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
         error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
      boolean retrySetup = testingSetup;
      boolean retryGame = gameListening;
      recognizerFallbackTried = true;
      if (switchToSystemRecognizer()) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
          testingSetup = retrySetup;
          gameListening = retryGame;
          startListeningNow();
        }, 250);
        return;
      }
    }
    if (testingSetup) {
      testingSetup = false;
      if (testStatus != null) testStatus.setText("Diesmal wurde nichts erkannt. Bitte erneut testen.");
    } else if (gameListening) {
      gameListening = false;
      if (gameStatus != null) gameStatus.setText("Diesmal wurde nichts erkannt. Bitte erneut versuchen.");
    }
  }

  private boolean switchToSystemRecognizer() {
    try {
      if (recognizer != null) {
        recognizer.cancel();
        recognizer.destroy();
      }
      recognizer = SpeechRecognizer.createSpeechRecognizer(this);
      recognizer.setRecognitionListener(this);
      usingOnDeviceRecognizer = false;
      return true;
    } catch (Exception ignored) {
      recognizer = null;
      return false;
    }
  }

  @Override
  public void onResults(Bundle results) {
    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    handleRecognition(list);
  }

  @Override
  protected void onDestroy() {
    gameVisible = false;
    if (recognizer != null) {
      recognizer.cancel();
      recognizer.destroy();
    }
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }

  private class ColorGameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float targetRadius = dp(55);
    private final float dragRadius = dp(46);
    private final int neutral = Color.rgb(144,154,160);
    private int targetColor = Color.LTGRAY;
    private int activeIndex = -1;
    private float dragX, dragY;
    private boolean draggingCircle = false;
    private boolean roundActive = false;
    private Runnable onCorrect;

    ColorGameView() {
      super(MainActivity.this);
      setBackgroundColor(Color.rgb(255,253,248));
      setOnTouchListener((v, event) -> handleTouch(event));
      post(this::resetRound);
    }

    void setOnCorrectListener(Runnable r) { onCorrect = r; }

    void startRound(int color) {
      targetColor = color;
      activeIndex = random.nextInt(3);
      roundActive = true;
      resetDragOnly();
      invalidate();
    }

    void resetRound() {
      roundActive = false;
      activeIndex = -1;
      targetColor = Color.LTGRAY;
      resetDragOnly();
      invalidate();
    }

    private void resetDragOnly() {
      dragX = getWidth() > 0 ? getWidth() / 2f : dp(160);
      dragY = getHeight() > 0 ? getHeight() - dp(74) : dp(280);
    }

    private float tx(int i) {
      if (i == 0) return getWidth() * .18f;
      if (i == 1) return getWidth() * .50f;
      return getWidth() * .82f;
    }

    private float ty() { return dp(82); }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);

      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 3; i++) {
        paint.setColor(i == activeIndex ? targetColor : Color.rgb(248,247,243));
        canvas.drawCircle(tx(i), ty(), targetRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(i == activeIndex ? targetColor : Color.rgb(186,194,198));
        canvas.drawCircle(tx(i), ty(), targetRadius, paint);
        paint.setStyle(Paint.Style.FILL);
      }

      paint.setColor(neutral);
      canvas.drawCircle(dragX, dragY, dragRadius, paint);

      paint.setColor(Color.WHITE);
      paint.setTextSize(dp(15));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      canvas.drawText("Schieben", dragX, dragY + dp(5), paint);
    }

    private boolean handleTouch(MotionEvent e) {
      if (!roundActive) return true;
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (distance(e.getX(), e.getY(), dragX, dragY) <= dragRadius * 1.25f) {
            draggingCircle = true;
            getParent().requestDisallowInterceptTouchEvent(true);
          }
          return true;
        case MotionEvent.ACTION_MOVE:
          if (draggingCircle) {
            dragX = clamp(e.getX(), dragRadius, getWidth() - dragRadius);
            dragY = clamp(e.getY(), dragRadius, getHeight() - dragRadius);
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (draggingCircle) {
            draggingCircle = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            if (activeIndex >= 0 &&
                distance(dragX, dragY, tx(activeIndex), ty()) <= targetRadius + dragRadius * .55f) {
              dragX = tx(activeIndex);
              dragY = ty();
              roundActive = false;
              invalidate();
              if (onCorrect != null) onCorrect.run();
            }
          }
          return true;
        case MotionEvent.ACTION_CANCEL:
          draggingCircle = false;
          getParent().requestDisallowInterceptTouchEvent(false);
          resetDragOnly();
          invalidate();
          return true;
      }
      return true;
    }

    private float distance(float x1, float y1, float x2, float y2) {
      float dx = x1 - x2, dy = y1 - y2;
      return (float)Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float v, float lo, float hi) {
      return Math.max(lo, Math.min(v, hi));
    }
  }
}
