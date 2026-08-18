package com.example.instrumentsandnav;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final double KNOTS_PER_MPS = 1.9438444924406;
    private static final double FPM_PER_MPS = 196.8503937008;
    private static final long GPS_STALE_MS = 6000L;
    // Working cockpit mounting reference from the pre-NAV Instrumentpannel build.
    // With the phone held about 45 degrees forward, the attitude indicator reads level.
    private static final float LEVEL_REFERENCE_FORWARD_TILT_DEG = 45.0f;

    private WebView webView;
    private SensorManager sensorManager;
    private Sensor attitudeSensor;
    private boolean accelerometerFallback = false;
    private final float[] filteredAccel = new float[]{0f, 0f, 0f};
    private boolean accelInitialized = false;

    private LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private boolean pageReady = false;

    private float pitchDeg = 0f;
    private float rollDeg = 0f;

    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private double altitudeM = Double.NaN;
    private double speedKt = 0.0;
    private double trackDeg = Double.NaN;
    private double lastValidTrackDeg = Double.NaN;
    private double vsiFpm = 0.0;

    // Strict source separation: all instruments except attitude are fed only by GPS Location fixes.
    private boolean hasGpsFix = false;
    private boolean hasGpsAltitude = false;
    private boolean hasGpsSpeed = false;
    private boolean hasGpsVsi = false;
    private boolean gpsProviderEnabled = false;
    private long lastGpsFixElapsedMs = -1L;

    private double previousAltitudeM = Double.NaN;
    private long previousAltitudeTimeMs = 0L;

    private final Runnable dataPump = new Runnable() {
        @Override
        public void run() {
            pushFlightDataToPanel();
            handler.postDelayed(this, 100L); // 10 Hz display update
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);

        // Native HTTPS bridge for route lookup. Doing the network request in Android
        // avoids WebView CORS restrictions and keeps the HTML focused on presentation.
        webView.addJavascriptInterface(new RouteBridge(), "AndroidRoute");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                pushFlightDataToPanel();
            }
        });

        setContentView(webView);

        // On some OxygenOS/OnePlus builds the WindowInsetsController is not
        // available until the decor view has been attached. Apply immersive
        // mode on the next UI pass instead of during early Activity creation.
        webView.post(this::hideSystemUi);

        webView.loadUrl("file:///android_asset/instruments_nav.html");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            attitudeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (attitudeSensor == null) {
                attitudeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                accelerometerFallback = attitudeSensor != null;
            }
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
        }

        handler.post(dataPump);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().post(this::hideSystemUi);

        if (sensorManager != null && attitudeSensor != null) {
            sensorManager.registerListener(
                    this,
                    attitudeSensor,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }

        startGpsIfPermitted();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(dataPump);
        routeExecutor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void hideSystemUi() {
        Window window = getWindow();
        View decorView = window.getDecorView();

        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);

                // Get the controller from the attached decor view. This is safer
                // on OxygenOS than calling PhoneWindow.getInsetsController()
                // during early Activity startup.
                WindowInsetsController controller = decorView.getWindowInsetsController();
                if (controller != null) {
                    controller.hide(
                            WindowInsets.Type.statusBars()
                                    | WindowInsets.Type.navigationBars()
                    );
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                setLegacyImmersiveFlags(decorView);
            }
        } catch (RuntimeException ignored) {
            // OEM window implementations can briefly have no insets controller.
            // Never let full-screen decoration crash the flight-instrument app.
            setLegacyImmersiveFlags(decorView);
        }
    }

    private void setLegacyImmersiveFlags(View decorView) {
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().post(this::hideSystemUi);
        }
    }

    private void startGpsIfPermitted() {
        if (locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        try {
            gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            locationManager.removeUpdates(this);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    250L,
                    0f,
                    this,
                    Looper.getMainLooper()
            );
        } catch (SecurityException ignored) {
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGpsIfPermitted();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 3) return;

        float gx = event.values[0];
        float gy = event.values[1];
        float gz = event.values[2];

        if (accelerometerFallback) {
            if (!accelInitialized) {
                filteredAccel[0] = gx;
                filteredAccel[1] = gy;
                filteredAccel[2] = gz;
                accelInitialized = true;
            } else {
                final float alpha = 0.12f;
                filteredAccel[0] += alpha * (gx - filteredAccel[0]);
                filteredAccel[1] += alpha * (gy - filteredAccel[1]);
                filteredAccel[2] += alpha * (gz - filteredAccel[2]);
            }
            gx = filteredAccel[0];
            gy = filteredAccel[1];
            gz = filteredAccel[2];
        }

        // Restore the attitude mapping from the last working pre-NAV build.
        // Treat the gravity vector as the phone's local up-vector and normalize it.
        // This avoids the previous atan2(gx,-gy) mapping, which produced ~180 degrees
        // of roll at the normal OnePlus mounting attitude and turned the ball upside down.
        double magnitude = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (magnitude < 0.001) return;

        double upX = gx / magnitude;
        double upY = gy / magnitude;
        double upZ = gz / magnitude;

        double forwardTiltDeg = Math.toDegrees(Math.atan2(upZ, upY));
        double newPitch = LEVEL_REFERENCE_FORWARD_TILT_DEG - forwardTiltDeg;
        double newRoll = Math.toDegrees(
                Math.atan2(upX, Math.sqrt(upY * upY + upZ * upZ))
        );

        newPitch = Math.max(-85.0, Math.min(85.0, newPitch));
        newRoll = Math.max(-89.0, Math.min(89.0, newRoll));

        pitchDeg = smoothAngle(pitchDeg, (float) newPitch, 0.20f);
        rollDeg = smoothAngle(rollDeg, (float) newRoll, 0.20f);
    }

    private float smoothAngle(float current, float target, float factor) {
        float delta = target - current;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        return current + delta * factor;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action required for the current test build.
    }

    @Override
    public void onLocationChanged(Location location) {
        // This callback is registered exclusively with LocationManager.GPS_PROVIDER.
        // Therefore every value below is GPS-derived; no network location or magnetometer is used.
        hasGpsFix = true;
        gpsProviderEnabled = true;
        lastGpsFixElapsedMs = SystemClock.elapsedRealtime();
        latitude = location.getLatitude();
        longitude = location.getLongitude();

        if (location.hasAltitude()) {
            altitudeM = location.getAltitude();
            hasGpsAltitude = true;
            updateVsi(altitudeM, location.getTime());
        }

        if (location.hasSpeed()) {
            speedKt = Math.max(0.0, location.getSpeed() * KNOTS_PER_MPS);
            hasGpsSpeed = true;
        }

        // Heading instrument is GPS track only. Do not substitute magnetic compass.
        if (location.hasBearing() && location.getSpeed() >= 1.0f) {
            lastValidTrackDeg = normalize360(location.getBearing());
            trackDeg = lastValidTrackDeg;
        } else if (Double.isFinite(lastValidTrackDeg)) {
            // At low/zero groundspeed GPS course is unreliable: freeze last valid track.
            trackDeg = lastValidTrackDeg;
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            gpsProviderEnabled = true;
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            gpsProviderEnabled = false;
        }
    }

    private void updateVsi(double newAltitudeM, long fixTimeMs) {
        if (!Double.isFinite(previousAltitudeM) || previousAltitudeTimeMs == 0L) {
            previousAltitudeM = newAltitudeM;
            previousAltitudeTimeMs = fixTimeMs;
            return;
        }

        double dt = (fixTimeMs - previousAltitudeTimeMs) / 1000.0;
        if (dt >= 0.25 && dt <= 10.0) {
            double rawFpm = ((newAltitudeM - previousAltitudeM) / dt) * FPM_PER_MPS;
            rawFpm = Math.max(-6000.0, Math.min(6000.0, rawFpm));
            vsiFpm += 0.18 * (rawFpm - vsiFpm);
            hasGpsVsi = true;
        }

        previousAltitudeM = newAltitudeM;
        previousAltitudeTimeMs = fixTimeMs;
    }

    private double normalize360(double degrees) {
        double v = degrees % 360.0;
        return v < 0.0 ? v + 360.0 : v;
    }

    private void pushFlightDataToPanel() {
        if (!pageReady || webView == null) return;

        try {
            JSONObject data = new JSONObject();
            // Artificial horizon: phone gravity/accelerometer sensor only.
            data.put("pitchDeg", pitchDeg);
            data.put("rollDeg", rollDeg);

            // GPS status is green only while the GPS provider is enabled and fixes are fresh.
            // A six-second timeout avoids flicker between normal 1 Hz fixes while still
            // turning the label red quickly if satellite reception is lost.
            long nowElapsed = SystemClock.elapsedRealtime();
            boolean gpsOk = hasGpsFix
                    && gpsProviderEnabled
                    && lastGpsFixElapsedMs >= 0L
                    && (nowElapsed - lastGpsFixElapsedMs) <= GPS_STALE_MS;
            data.put("gpsOk", gpsOk);

            // Every remaining flight instrument below is GPS-only.
            if (hasGpsFix && Double.isFinite(latitude)) data.put("lat", latitude);
            if (hasGpsFix && Double.isFinite(longitude)) data.put("lon", longitude);
            if (hasGpsAltitude && Double.isFinite(altitudeM)) data.put("altitudeM", altitudeM);
            if (hasGpsSpeed) data.put("speedKt", speedKt);
            if (hasGpsVsi) data.put("vsiFpm", vsiFpm);
            if (Double.isFinite(trackDeg)) data.put("trackDeg", trackDeg);

            String js = "window.updateFlightData && window.updateFlightData(" + data + ");";
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Online route bridge used by the NAV window.
     *
     * Stage 1 deliberately uses only Flight Plan Database endpoints that do not require
     * an API key: airport/navaid search, stored-plan search, and fetch-plan. If a matching
     * stored plan exists, its route nodes are returned to JavaScript with coordinates.
     * A later stage can add the authenticated /auto/generate endpoint for on-demand routes.
     */
    private final class RouteBridge {
        @JavascriptInterface
        public void requestRoute(String originCode, String destinationCode) {
            final String origin = sanitizeAirportCode(originCode);
            final String destination = sanitizeAirportCode(destinationCode);

            if (origin.length() < 3 || destination.length() < 3) {
                sendRouteError(origin, destination, "ENTER 3-4 LETTER AIRPORT CODES");
                return;
            }

            routeExecutor.execute(() -> {
                try {
                    JSONObject fromAirport = resolveAirport(origin);
                    JSONObject toAirport = resolveAirport(destination);
                    if (fromAirport == null || toAirport == null) {
                        sendRouteError(origin, destination, "AIRPORT NOT FOUND");
                        return;
                    }

                    String fromICAO = fromAirport.optString("ICAO", "").toUpperCase(Locale.US);
                    String toICAO = toAirport.optString("ICAO", "").toUpperCase(Locale.US);
                    if (fromICAO.isEmpty() || toICAO.isEmpty()) {
                        sendRouteError(origin, destination, "AIRPORT CODE RESOLUTION FAILED");
                        return;
                    }

                    JSONObject planSummary = findBestStoredPlan(fromICAO, toICAO);
                    if (planSummary == null) {
                        sendRouteError(origin, destination, "NO ONLINE ROUTE FOUND");
                        return;
                    }

                    long planId = planSummary.optLong("id", -1L);
                    if (planId < 0) {
                        sendRouteError(origin, destination, "ONLINE ROUTE ID MISSING");
                        return;
                    }

                    JSONObject plan = getJsonObject(
                            "https://api.flightplandatabase.com/plan/" + planId
                    );
                    if (plan == null) {
                        sendRouteError(origin, destination, "ROUTE DOWNLOAD FAILED");
                        return;
                    }

                    JSONObject route = plan.optJSONObject("route");
                    JSONArray nodes = route != null ? route.optJSONArray("nodes") : null;
                    if (nodes == null || nodes.length() < 2) {
                        sendRouteError(origin, destination, "ROUTE HAS NO WAYPOINT DATA");
                        return;
                    }

                    JSONObject result = buildRouteResult(
                            origin, destination, fromAirport, toAirport, plan, nodes
                    );
                    sendRouteResult(result);
                } catch (Exception e) {
                    sendRouteError(origin, destination, "ONLINE ROUTE ERROR");
                }
            });
        }
    }

    private String sanitizeAirportCode(String value) {
        if (value == null) return "";
        String cleaned = value.toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        return cleaned.substring(0, Math.min(4, cleaned.length()));
    }

    private JSONObject resolveAirport(String code) throws Exception {
        // Four-letter entries are treated as ICAO first and resolved directly through
        // Flight Plan Database.
        if (code.length() == 4) {
            JSONObject direct = getJsonObject(
                    "https://api.flightplandatabase.com/nav/airport/" + enc(code)
            );
            if (direct != null && code.equalsIgnoreCase(direct.optString("ICAO"))) {
                return direct;
            }
        }

        // Important: Flight Plan Database /search/nav searches navaid identifiers and
        // names; for airports its ident is the ICAO code, so a passenger IATA code such
        // as LHR or PVK is not a reliable lookup key there. Resolve 3-letter IATA codes
        // with Airport-Data.com first, then fetch the matching Flight Plan Database
        // airport object by ICAO. This keeps arbitrary IATA input working without an API key.
        if (code.length() == 3) {
            JSONObject iataLookup = getJsonObject(
                    "https://airport-data.com/api/ap_info.json?iata=" + enc(code)
            );
            if (iataLookup != null && iataLookup.optInt("status", 200) == 200) {
                String icao = iataLookup.optString("icao", "").toUpperCase(Locale.US);
                String iata = iataLookup.optString("iata", "").toUpperCase(Locale.US);
                if (code.equalsIgnoreCase(iata) && icao.length() == 4) {
                    JSONObject detail = getJsonObject(
                            "https://api.flightplandatabase.com/nav/airport/" + enc(icao)
                    );
                    if (detail != null) return detail;

                    // Fallback: normalize Airport-Data's response into the shape used
                    // elsewhere in this class if the FPDB airport detail request fails.
                    JSONObject normalized = new JSONObject();
                    normalized.put("ICAO", icao);
                    normalized.put("IATA", iata);
                    normalized.put("name", iataLookup.optString("name", icao));
                    normalized.put("lat", parseDoubleOrNaN(iataLookup.optString("latitude", "")));
                    normalized.put("lon", parseDoubleOrNaN(iataLookup.optString("longitude", "")));
                    return normalized;
                }
            }
        }

        // Final fallback for ICAO-like identifiers or airport names that happen to match
        // the Flight Plan Database navaid search. Do not assume this can resolve IATA.
        JSONArray candidates = getJsonArray(
                "https://api.flightplandatabase.com/search/nav?q=" + enc(code) + "&types=APT"
        );
        if (candidates == null) return null;

        int max = Math.min(candidates.length(), 12);
        for (int i = 0; i < max; i++) {
            JSONObject item = candidates.optJSONObject(i);
            if (item == null) continue;
            String ident = item.optString("ident", "").toUpperCase(Locale.US);
            if (ident.length() != 4) continue;

            JSONObject detail = getJsonObject(
                    "https://api.flightplandatabase.com/nav/airport/" + enc(ident)
            );
            if (detail == null) continue;

            String icao = detail.optString("ICAO", "");
            String iata = detail.optString("IATA", "");
            if (code.equalsIgnoreCase(icao) || code.equalsIgnoreCase(iata)) {
                return detail;
            }
        }
        return null;
    }

    private double parseDoubleOrNaN(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private JSONObject findBestStoredPlan(String fromICAO, String toICAO) throws Exception {
        JSONArray plans = getJsonArray(
                "https://api.flightplandatabase.com/search/plans?fromICAO=" + enc(fromICAO)
                        + "&toICAO=" + enc(toICAO) + "&limit=50"
        );
        if (plans == null || plans.length() == 0) return null;

        JSONObject best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            int waypoints = p.optInt("waypoints", 0);
            double popularity = p.optDouble("popularity", 0.0);
            int likes = p.optInt("likes", 0);
            int downloads = p.optInt("downloads", 0);
            JSONArray tags = p.optJSONArray("tags");

            double score = popularity * 1000.0 + likes * 100.0 + downloads + Math.min(waypoints, 80);
            if (hasTag(tags, "Real")) score += 1_000_000.0;
            if (hasTag(tags, "Commercial")) score += 500_000.0;
            if (waypoints <= 2) score -= 100_000.0;

            if (best == null || score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean hasTag(JSONArray tags, String wanted) {
        if (tags == null) return false;
        for (int i = 0; i < tags.length(); i++) {
            if (wanted.equalsIgnoreCase(tags.optString(i))) return true;
        }
        return false;
    }

    private JSONObject buildRouteResult(
            String requestOrigin,
            String requestDestination,
            JSONObject fromAirport,
            JSONObject toAirport,
            JSONObject plan,
            JSONArray nodes
    ) throws Exception {
        JSONObject result = new JSONObject();
        result.put("requestOrigin", requestOrigin);
        result.put("requestDestination", requestDestination);
        result.put("source", "Flight Plan Database");
        result.put("planId", plan.optLong("id", -1));
        result.put("fromICAO", fromAirport.optString("ICAO", requestOrigin));
        result.put("toICAO", toAirport.optString("ICAO", requestDestination));
        result.put("fromIATA", fromAirport.optString("IATA", ""));
        result.put("toIATA", toAirport.optString("IATA", ""));
        result.put("fromName", fromAirport.optString("name", ""));
        result.put("toName", toAirport.optString("name", ""));

        JSONObject first = nodes.optJSONObject(0);
        JSONObject last = nodes.optJSONObject(nodes.length() - 1);
        result.put("originPoint", pointFromNode(
                first, result.optString("fromICAO"), fromAirport.optDouble("lat"), fromAirport.optDouble("lon")
        ));
        result.put("destinationPoint", pointFromNode(
                last, result.optString("toICAO"), toAirport.optDouble("lat"), toAirport.optDouble("lon")
        ));

        JSONArray waypoints = new JSONArray();
        StringBuilder routeString = new StringBuilder();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n == null) continue;
            String ident = n.optString("ident", "").trim();
            if (!ident.isEmpty()) {
                if (routeString.length() > 0) routeString.append(' ');
                routeString.append(ident);
            }

            if (i == 0 || i == nodes.length() - 1) continue;
            if (!Double.isFinite(n.optDouble("lat", Double.NaN))
                    || !Double.isFinite(n.optDouble("lon", Double.NaN))) continue;

            JSONObject w = new JSONObject();
            w.put("name", ident.isEmpty() ? "WPT" : ident);
            w.put("type", n.optString("type", "FIX"));
            w.put("lat", n.optDouble("lat"));
            w.put("lon", n.optDouble("lon"));
            JSONObject via = n.optJSONObject("via");
            if (via != null) {
                w.put("via", via.optString("ident", ""));
                w.put("viaType", via.optString("type", ""));
            }
            waypoints.put(w);
        }
        result.put("waypoints", waypoints);
        result.put("routeString", routeString.toString());
        double planDistance = plan.optDouble("distance", Double.NaN);
        if (Double.isFinite(planDistance)) result.put("distanceNm", planDistance);
        return result;
    }

    private JSONObject pointFromNode(JSONObject node, String fallbackName, double fallbackLat, double fallbackLon)
            throws Exception {
        JSONObject p = new JSONObject();
        String name = fallbackName;
        double lat = fallbackLat;
        double lon = fallbackLon;
        if (node != null) {
            String ident = node.optString("ident", "").trim();
            if (!ident.isEmpty()) name = ident;
            double nodeLat = node.optDouble("lat", Double.NaN);
            double nodeLon = node.optDouble("lon", Double.NaN);
            if (Double.isFinite(nodeLat)) lat = nodeLat;
            if (Double.isFinite(nodeLon)) lon = nodeLon;
        }
        p.put("name", name);
        p.put("lat", lat);
        p.put("lon", lon);
        return p;
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private JSONObject getJsonObject(String url) throws Exception {
        String text = httpGet(url);
        if (text == null || text.trim().isEmpty()) return null;
        return new JSONObject(text);
    }

    private JSONArray getJsonArray(String url) throws Exception {
        String text = httpGet(url);
        if (text == null || text.trim().isEmpty()) return null;
        return new JSONArray(text);
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "InstrumentsAndNav/1.0 Android");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            reader.close();
            if (status < 200 || status >= 300) return null;
            return out.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void sendRouteResult(JSONObject result) {
        handler.post(() -> {
            if (webView == null) return;
            String js = "window.receiveOnlineRoute && window.receiveOnlineRoute(" + result.toString() + ");";
            webView.evaluateJavascript(js, null);
        });
    }

    private void sendRouteError(String origin, String destination, String message) {
        handler.post(() -> {
            if (webView == null) return;
            try {
                JSONObject error = new JSONObject();
                error.put("requestOrigin", origin);
                error.put("requestDestination", destination);
                error.put("message", message);
                String js = "window.receiveOnlineRouteError && window.receiveOnlineRouteError(" + error.toString() + ");";
                webView.evaluateJavascript(js, null);
            } catch (Exception ignored) {
            }
        });
    }

}
