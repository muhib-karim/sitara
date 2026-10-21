package app.sitara.music;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Places the home-screen widget the way a launcher does (an AppWidgetHost binding Sitara's provider) in the build under
 * test, which CI makes minified like a release, and waits for it to leave Glance's loading layout and offer Play
 * something. Needs the bind permission: adb shell appwidget grantbind --package app.sitara.music.
 * Written in Java so the test APK needs none of the app's (minified) Kotlin runtime.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetPlacementTest {
    private static final String RECEIVER = "app.sitara.music.widget.SitaraWidgetReceiver";
    private static final int HOST_ID = 7_297;
    private static final long TIMEOUT_MS = 10_000;
    private static final int WIDTH_DP = 300;
    private static final int HEIGHT_DP = 72;

    @Test
    public void aPlacedWidgetDrawsPlaySomethingWithinTenSeconds() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetHost host = new AppWidgetHost(context, HOST_ID);
        AtomicReference<AppWidgetHostView> view = new AtomicReference<>();
        AtomicInteger id = new AtomicInteger();
        instrumentation.runOnMainSync(() -> {
            host.startListening();
            id.set(host.allocateAppWidgetId());
            Bundle size = new Bundle();
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, WIDTH_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, WIDTH_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, HEIGHT_DP);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, HEIGHT_DP);
            assertTrue("bind permission (appwidget grantbind)", manager.bindAppWidgetIdIfAllowed(id.get(), new ComponentName(context, RECEIVER), size));
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id.get());
            view.set(host.createView(context, id.get(), info));
        });
        try {
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            AtomicBoolean drawn = new AtomicBoolean();
            while (!drawn.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(250);
                instrumentation.runOnMainSync(() -> drawn.set(hasText(view.get(), "Play something")));
            }
            instrumentation.runOnMainSync(() -> save(view.get(), new File(context.getExternalFilesDir(null), "widget-placed.png")));
            if (!drawn.get()) fail("The widget still shows Glance's loading layout after " + TIMEOUT_MS + " ms");
        } finally {
            instrumentation.runOnMainSync(() -> {
                host.deleteAppWidgetId(id.get());
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
