package app.sitara.music;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Places the home-screen widget the way a launcher does (an AppWidgetHost binding Sitara's provider) in the build under
 * test, which CI makes minified like a release, and waits for it to leave Glance's loading layout and offer Play
 * something. Prints "WIDGET DRAWN" or "WIDGET CHECK FAILED" for {@code am instrument -w}, and keeps a picture of the
 * placed widget. Needs the bind permission: adb shell appwidget grantbind --package app.sitara.music.
 * A plain Instrumentation in Java that uses no app code, so CI drives the minified app with the debug test APK.
 */
public class WidgetCheckInstrumentation extends Instrumentation {
    private static final String RECEIVER = "app.sitara.music.widget.SitaraWidgetReceiver";
    private static final int HOST_ID = 7_297;
    private static final long TIMEOUT_MS = 10_000;
    private static final int WIDTH_DP = 300;
    private static final int HEIGHT_DP = 72;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle report = new Bundle();
        boolean drawn = false;
        try {
            long ms = placeAndWait();
            drawn = ms >= 0;
            report.putString(REPORT_KEY_STREAMRESULT, drawn ? "WIDGET DRAWN in " + ms + " ms\n" : "WIDGET CHECK FAILED: still loading after " + TIMEOUT_MS + " ms\n");
        } catch (Throwable t) {
            report.putString(REPORT_KEY_STREAMRESULT, "WIDGET CHECK FAILED: " + Log.getStackTraceString(t) + "\n");
        }
        finish(drawn ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }

    /** Milliseconds until Play something showed, or -1 when it did not within the timeout. */
    private long placeAndWait() throws Exception {
        Context context = getTargetContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetHost host = new AppWidgetHost(context, HOST_ID);
        AppWidgetHostView[] view = new AppWidgetHostView[1];
        int[] id = new int[1];
        boolean[] bound = new boolean[1];
        runOnMainSync(() -> {
            host.startListening();
            id[0] = host.allocateAppWidgetId();
            Bundle size = new Bundle();
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, WIDTH_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, WIDTH_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, HEIGHT_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, HEIGHT_DP);
            bound[0] = manager.bindAppWidgetIdIfAllowed(id[0], new ComponentName(context, RECEIVER), size);
            if (bound[0]) view[0] = host.createView(context, id[0], manager.getAppWidgetInfo(id[0]));
        });
        if (!bound[0]) throw new IllegalStateException("No bind permission: run adb shell appwidget grantbind --package app.sitara.music");
        try {
            long start = System.currentTimeMillis();
            boolean[] drawn = new boolean[1];
            while (!drawn[0] && System.currentTimeMillis() - start < TIMEOUT_MS) {
                Thread.sleep(250);
                runOnMainSync(() -> drawn[0] = hasText(view[0], "Play something"));
            }
            long took = System.currentTimeMillis() - start;
            runOnMainSync(() -> save(view[0], new File(context.getExternalFilesDir(null), "widget-placed.png")));
            return drawn[0] ? took : -1;
        } finally {
            runOnMainSync(() -> {
                host.deleteAppWidgetId(id[0]);
                host.stopListening();
            });
        }
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    /** Lays the host view out at the widget's 4 x 1 size and keeps a picture of it for the CI artifact. */
    private static void save(View view, File file) {
        float density = view.getResources().getDisplayMetrics().density;
        int width = Math.round(WIDTH_DP * density);
        int height = Math.round(HEIGHT_DP * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Log.w("WidgetCheck", "Could not save the widget's picture", e);
        }
    }
}
