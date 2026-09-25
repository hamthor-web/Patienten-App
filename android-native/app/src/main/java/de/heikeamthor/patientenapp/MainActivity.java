package de.heikeamthor.patientenapp;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

  private static final int BG = Color.rgb(238, 234, 224);
  private static final int PANEL = Color.rgb(253, 250, 244);
  private static final int TEXT = Color.rgb(37, 48, 54);
  private static final int MUTED = Color.rgb(101, 111, 116);
  private static final int ACCENT = Color.rgb(92, 120, 111);

  private final Handler handler = new Handler(Looper.getMainLooper());
  private TextToSpeech tts;
  private boolean ttsReady = false;
  private LinearLayout root;
  private TextView taskText;
  private TextView statusText;
  private KitchenGameView gameView;
  private int taskIndex = 0;

  private final String[] tasks = {
      "Stellen Sie die Tasse an einen Platz, der Ihnen passend erscheint.",
      "Schieben Sie den Kochtopf an einen guten Platz in der Küche.",
      "Stellen Sie den Besen dorthin, wo er gut stehen kann.",
      "Schauen Sie sich die Küche in Ruhe an. Wo steht die Tasse?",
      "Räumen Sie weiter so auf, wie es für Sie stimmig ist."
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initTts();
    showKitchenGame();
  }

  private void initTts() {
    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        ttsReady = true;
        tts.setLanguage(Locale.GERMANY);
        tts.setSpeechRate(0.86f);
        handler.postDelayed(() -> speak(currentTask()), 450);
      }
    });
  }

  private void showKitchenGame() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(18), dp(18), dp(18), dp(16));
    root.setBackgroundColor(BG);
    setContentView(root);

    TextView title = text("Küchenübung", 28, true);
    root.addView(title);

    taskText = text(currentTask(), 22, true);
    taskText.setGravity(Gravity.CENTER_VERTICAL);
    taskText.setMinHeight(dp(112));
    taskText.setBackgroundColor(PANEL);
    taskText.setPadding(dp(18), dp(14), dp(18), dp(14));
    root.addView(taskText, fullWidthWrap());

    statusText = text("Die Aufgabe bleibt sichtbar. Sie können sich Zeit lassen.", 17, false);
    statusText.setTextColor(MUTED);
    root.addView(statusText);

    gameView = new KitchenGameView();
    LinearLayout.LayoutParams gameLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    gameLp.setMargins(0, dp(10), 0, dp(10));
    root.addView(gameView, gameLp);

    LinearLayout controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER);

    Button repeat = button("Vorlesen");
    Button next = button("Nächste Aufgabe");
    controls.addView(repeat, new LinearLayout.LayoutParams(0, dp(68), 1f));
    controls.addView(next, new LinearLayout.LayoutParams(0, dp(68), 1f));
    root.addView(controls);

    repeat.setOnClickListener(v -> speak(currentTask()));
    next.setOnClickListener(v -> nextTask());

    gameView.setOnObjectPlacedListener(name -> {
      statusText.setText(name + " steht jetzt an diesem Platz.");
      speak(name + " steht jetzt an diesem Platz.");
    });
  }

  private void nextTask() {
    taskIndex = (taskIndex + 1) % tasks.length;
    taskText.setText(currentTask());
    statusText.setText("Die Aufgabe bleibt sichtbar. Sie können sich Zeit lassen.");
    speak(currentTask());
  }

  private String currentTask() {
    return tasks[taskIndex];
  }

  private TextView text(String value, int sp, boolean bold) {
    TextView tv = new TextView(this);
    tv.setText(value);
    tv.setTextColor(TEXT);
    tv.setTextSize(sp);
    tv.setPadding(dp(4), dp(6), dp(4), dp(6));
    tv.setLineSpacing(dp(2), 1.0f);
    if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
    return tv;
  }

  private Button button(String label) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(18);
    b.setTextColor(TEXT);
    b.setAllCaps(false);
    b.setMinHeight(dp(64));
    return b;
  }

  private LinearLayout.LayoutParams fullWidthWrap() {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, dp(8), 0, dp(4));
    return lp;
  }

  private void speak(String message) {
    if (!ttsReady || tts == null) return;
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "task_" + System.nanoTime());
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }

  private class KitchenGameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private Draggable cup;
    private Draggable pot;
    private Draggable broom;
    private Draggable active;
    private float grabDx;
    private float grabDy;
    private int lastW = -1;
    private int lastH = -1;
    private ObjectPlacedListener listener;

    KitchenGameView() {
      super(MainActivity.this);
      setBackgroundColor(PANEL);
    }

    void setOnObjectPlacedListener(ObjectPlacedListener l) {
      listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      if (w != lastW || h != lastH || cup == null) {
        lastW = w;
        lastH = h;
        cup = new Draggable("Die Tasse", w * .21f, h * .70f, dp(42), Color.rgb(112, 148, 165));
        pot = new Draggable("Der Kochtopf", w * .52f, h * .73f, dp(50), Color.rgb(96, 104, 108));
        broom = new Draggable("Der Besen", w * .82f, h * .68f, dp(54), Color.rgb(164, 116, 72));
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      drawKitchen(canvas);
      drawSoftTargets(canvas);
      drawCup(canvas, cup);
      drawPot(canvas, pot);
      drawBroom(canvas, broom);
    }

    private void drawKitchen(Canvas canvas) {
      int w = getWidth();
      int h = getHeight();

      paint.setStyle(Paint.Style.FILL);
      paint.setShader(new LinearGradient(0, 0, 0, h,
          Color.rgb(255, 252, 245), Color.rgb(238, 232, 220), Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, w, h, paint);
      paint.setShader(null);

      paint.setColor(Color.rgb(224, 214, 198));
      canvas.drawRect(0, h * .58f, w, h, paint);

      paint.setColor(Color.rgb(232, 226, 215));
      for (int i = 0; i < 5; i++) {
        float y = h * .64f + i * dp(34);
        canvas.drawRect(0, y, w, y + dp(2), paint);
      }

      paint.setColor(Color.rgb(218, 227, 225));
      rect.set(w * .08f, h * .08f, w * .35f, h * .31f);
      canvas.drawRoundRect(rect, dp(8), dp(8), paint);
      paint.setColor(Color.rgb(250, 248, 242));
      rect.inset(dp(7), dp(7));
      canvas.drawRoundRect(rect, dp(6), dp(6), paint);
      paint.setColor(Color.rgb(184, 201, 198));
      canvas.drawRect(w * .215f - dp(1), h * .09f, w * .215f + dp(1), h * .30f, paint);
      canvas.drawRect(w * .09f, h * .195f - dp(1), w * .34f, h * .195f + dp(1), paint);

      paint.setColor(Color.rgb(204, 188, 165));
      rect.set(w * .44f, h * .08f, w * .86f, h * .25f);
      canvas.drawRoundRect(rect, dp(8), dp(8), paint);
      paint.setColor(Color.rgb(186, 166, 139));
      canvas.drawRect(w * .45f, h * .17f, w * .85f, h * .19f, paint);
      drawHandle(canvas, w * .55f, h * .155f);
      drawHandle(canvas, w * .74f, h * .155f);

      paint.setColor(Color.rgb(171, 149, 121));
      canvas.drawRect(w * .06f, h * .46f, w * .94f, h * .51f, paint);
      paint.setColor(Color.rgb(198, 177, 146));
      canvas.drawRect(w * .08f, h * .50f, w * .91f, h * .84f, paint);

      paint.setColor(Color.rgb(231, 228, 220));
      rect.set(w * .45f, h * .55f, w * .75f, h * .79f);
      canvas.drawRoundRect(rect, dp(10), dp(10), paint);
      paint.setColor(Color.rgb(91, 99, 101));
      canvas.drawCircle(w * .54f, h * .62f, dp(25), paint);
      canvas.drawCircle(w * .66f, h * .62f, dp(25), paint);

      paint.setColor(Color.rgb(172, 141, 103));
      canvas.drawRect(w * .08f, h * .58f, w * .35f, h * .84f, paint);
      paint.setColor(Color.rgb(151, 119, 83));
      canvas.drawRect(w * .205f, h * .59f, w * .21f, h * .83f, paint);
      drawHandle(canvas, w * .16f, h * .70f);
      drawHandle(canvas, w * .28f, h * .70f);

      paint.setColor(Color.rgb(126, 149, 104));
      canvas.drawCircle(w * .89f, h * .37f, dp(18), paint);
      canvas.drawCircle(w * .84f, h * .39f, dp(14), paint);
      canvas.drawCircle(w * .91f, h * .43f, dp(15), paint);
      paint.setColor(Color.rgb(159, 122, 94));
      rect.set(w * .84f, h * .45f, w * .92f, h * .52f);
      canvas.drawRoundRect(rect, dp(7), dp(7), paint);

      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(2));
      paint.setColor(Color.rgb(218, 207, 190));
      canvas.drawLine(w * .40f, h * .30f, w * .92f, h * .30f, paint);
      canvas.drawLine(w * .40f, h * .38f, w * .92f, h * .38f, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    private void drawSoftTargets(Canvas canvas) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.argb(42, 92, 120, 111));
      rect.set(getWidth() * .08f, getHeight() * .51f, getWidth() * .35f, getHeight() * .84f);
      canvas.drawRoundRect(rect, dp(12), dp(12), paint);
      rect.set(getWidth() * .44f, getHeight() * .53f, getWidth() * .76f, getHeight() * .80f);
      canvas.drawRoundRect(rect, dp(12), dp(12), paint);
      rect.set(getWidth() * .76f, getHeight() * .38f, getWidth() * .95f, getHeight() * .84f);
      canvas.drawRoundRect(rect, dp(12), dp(12), paint);
    }

    private void drawHandle(Canvas canvas, float x, float y) {
      paint.setColor(Color.rgb(93, 84, 74));
      rect.set(x - dp(12), y - dp(2), x + dp(12), y + dp(2));
      canvas.drawRoundRect(rect, dp(2), dp(2), paint);
    }

    private void drawCup(Canvas canvas, Draggable d) {
      if (d == null) return;
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(d.color);
      rect.set(d.x - d.r * .58f, d.y - d.r * .48f, d.x + d.r * .44f, d.y + d.r * .48f);
      canvas.drawRoundRect(rect, dp(9), dp(9), paint);

      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(5));
      paint.setColor(d.color);
      rect.set(d.x + d.r * .18f, d.y - d.r * .16f, d.x + d.r * .78f, d.y + d.r * .25f);
      canvas.drawArc(rect, -80, 170, false, paint);

      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.argb(95, 255, 255, 255));
      rect.set(d.x - d.r * .44f, d.y - d.r * .36f, d.x + d.r * .02f, d.y - d.r * .21f);
      canvas.drawRoundRect(rect, dp(8), dp(8), paint);
    }

    private void drawPot(Canvas canvas, Draggable d) {
      if (d == null) return;
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(d.color);
      rect.set(d.x - d.r * .70f, d.y - d.r * .35f, d.x + d.r * .70f, d.y + d.r * .42f);
      canvas.drawRoundRect(rect, dp(12), dp(12), paint);

      paint.setColor(Color.rgb(75, 82, 86));
      rect.set(d.x - d.r * .52f, d.y - d.r * .55f, d.x + d.r * .52f, d.y - d.r * .30f);
      canvas.drawRoundRect(rect, dp(10), dp(10), paint);
      canvas.drawCircle(d.x, d.y - d.r * .59f, dp(5), paint);

      paint.setStrokeWidth(dp(5));
      paint.setStyle(Paint.Style.STROKE);
      rect.set(d.x - d.r * .96f, d.y - d.r * .14f, d.x - d.r * .55f, d.y + d.r * .18f);
      canvas.drawArc(rect, 120, 170, false, paint);
      rect.set(d.x + d.r * .55f, d.y - d.r * .14f, d.x + d.r * .96f, d.y + d.r * .18f);
      canvas.drawArc(rect, -110, 170, false, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    private void drawBroom(Canvas canvas, Draggable d) {
      if (d == null) return;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(8));
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(Color.rgb(126, 83, 48));
      canvas.drawLine(d.x + d.r * .22f, d.y - d.r * .90f, d.x - d.r * .16f, d.y + d.r * .32f, paint);

      paint.setStrokeCap(Paint.Cap.BUTT);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.rgb(178, 135, 72));
      path.reset();
      path.moveTo(d.x - d.r * .50f, d.y + d.r * .24f);
      path.lineTo(d.x + d.r * .22f, d.y + d.r * .07f);
      path.lineTo(d.x + d.r * .42f, d.y + d.r * .72f);
      path.lineTo(d.x - d.r * .38f, d.y + d.r * .86f);
      path.close();
      canvas.drawPath(path, paint);

      paint.setStrokeWidth(dp(2));
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(Color.rgb(126, 90, 48));
      for (int i = 0; i < 5; i++) {
        float x = d.x - d.r * .35f + i * d.r * .17f;
        canvas.drawLine(x, d.y + d.r * .32f, x + d.r * .06f, d.y + d.r * .78f, paint);
      }
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          active = findTouched(event.getX(), event.getY());
          if (active != null) {
            grabDx = active.x - event.getX();
            grabDy = active.y - event.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_MOVE:
          if (active != null) {
            active.x = clamp(event.getX() + grabDx, active.r * .75f, getWidth() - active.r * .75f);
            active.y = clamp(event.getY() + grabDy, active.r, getHeight() - active.r * .50f);
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (active != null) {
            String name = active.name;
            active = null;
            getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
            if (listener != null) listener.onPlaced(name);
          }
          return true;
        case MotionEvent.ACTION_CANCEL:
          active = null;
          getParent().requestDisallowInterceptTouchEvent(false);
          invalidate();
          return true;
      }
      return true;
    }

    private Draggable findTouched(float x, float y) {
      if (broom != null && broom.hit(x, y)) return broom;
      if (pot != null && pot.hit(x, y)) return pot;
      if (cup != null && cup.hit(x, y)) return cup;
      return null;
    }

    private float clamp(float v, float lo, float hi) {
      return Math.max(lo, Math.min(v, hi));
    }
  }

  private static class Draggable {
    final String name;
    float x;
    float y;
    final float r;
    final int color;

    Draggable(String name, float x, float y, float r, int color) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.r = r;
      this.color = color;
    }

    boolean hit(float px, float py) {
      float dx = px - x;
      float dy = py - y;
      return Math.sqrt(dx * dx + dy * dy) <= r * 1.25f;
    }
  }

  private interface ObjectPlacedListener {
    void onPlaced(String name);
  }
}
