package com.qwe7002.telegram_sms_compat;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class WebConfigService extends Service {
    private static final String TAG = "WebConfigService";
    private static final int PORT = 8080;
    private WebServer webServer;
    private Context context;
    private stop_notify_receiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        
        // Start web server
        try {
            webServer = new WebServer(PORT, context);
            webServer.start();
            Log.i(TAG, "Web configuration server started on port " + PORT);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start web server", e);
        }
        
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(public_func.BROADCAST_STOP_SERVICE);
        receiver = new stop_notify_receiver();
        registerReceiver(receiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, 
            "Web Config Server running on http://*:" + PORT);
        startForeground(public_func.WEB_CONFIG_NOTIFY_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (webServer != null) {
            webServer.stop();
            Log.i(TAG, "Web configuration server stopped");
        }
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class stop_notify_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received stop signal");
            stopSelf();
        }
    }

    // NanoHTTPD Web Server
    private static class WebServer extends NanoHTTPD {
        private final Context context;
        private final Gson gson;

        public WebServer(int port, Context context) {
            super(port);
            this.context = context;
            this.gson = new Gson();
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Method method = session.getMethod();
            
            Log.d(TAG, "Request: " + method + " " + uri);

            try {
                // Serve static files
                if (uri.equals("/") || uri.equals("/index.html")) {
                    return serveFile("web/index.html", "text/html");
                } else if (uri.endsWith(".css")) {
                    return serveFile("web/" + uri.substring(1), "text/css");
                } else if (uri.endsWith(".js")) {
                    return serveFile("web/" + uri.substring(1), "application/javascript");
                }
                
                // API endpoints
                if (uri.startsWith("/api/")) {
                    return handleApiRequest(uri, method, session);
                }
                
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
            } catch (Exception e) {
                Log.e(TAG, "Error serving request", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, 
                    "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private Response serveFile(String filename, String mimeType) {
            try {
                InputStream inputStream = context.getAssets().open(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder content = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                
                reader.close();
                return newFixedLengthResponse(Response.Status.OK, mimeType, content.toString());
            } catch (IOException e) {
                Log.e(TAG, "Failed to serve file: " + filename, e);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
            }
        }

        private Response handleApiRequest(String uri, Method method, IHTTPSession session) {
            SharedPreferences prefs = context.getSharedPreferences("data", MODE_PRIVATE);
            
            try {
                // GET /api/config - Get current configuration
                if (uri.equals("/api/config") && method == Method.GET) {
                    Map<String, Object> config = new HashMap<>();
                    config.put("botToken", prefs.getString("bot_token", ""));
                    config.put("chatId", prefs.getString("chat_id", ""));
                    config.put("trustedNumber", prefs.getString("trusted_phone_number", ""));
                    config.put("chatCommand", prefs.getBoolean("chat_command", false));
                    config.put("batteryMonitoring", prefs.getBoolean("battery_monitoring_switch", false));
                    config.put("chargerStatus", prefs.getBoolean("charger_status", false));
                    config.put("fallbackSms", prefs.getBoolean("fallback_sms", false));
                    config.put("verificationCode", prefs.getBoolean("verification_code", true));
                    config.put("privacyMode", prefs.getBoolean("privacy_mode", false));
                    config.put("dohSwitch", prefs.getBoolean("doh_switch", false));
                    
                    return jsonResponse(Response.Status.OK, config);
                }
                
                // POST /api/config - Save configuration
                if (uri.equals("/api/config") && method == Method.POST) {
                    Log.d(TAG, "Processing POST /api/config request");
                    try {
                        // Read the JSON body directly from the request
                        String contentLengthStr = session.getHeaders().get("content-length");
                        Log.d(TAG, "Content-Length header: " + contentLengthStr);
                        if (contentLengthStr == null) {
                            Map<String, String> errorResponse = new HashMap<>();
                            errorResponse.put("error", "Missing content-length header");
                            return jsonResponse(Response.Status.BAD_REQUEST, errorResponse);
                        }

                        int contentLength = Integer.parseInt(contentLengthStr);
                        Log.d(TAG, "Expected content length: " + contentLength);
                        byte[] buffer = new byte[contentLength];
                        int totalBytesRead = 0;
                        int bytesRead;
                        while (totalBytesRead < contentLength) {
                            bytesRead = session.getInputStream().read(buffer, totalBytesRead, contentLength - totalBytesRead);
                            Log.d(TAG, "Read " + bytesRead + " bytes, total so far: " + (totalBytesRead + bytesRead));
                            if (bytesRead == -1) {
                                break; // EOF reached
                            }
                            totalBytesRead += bytesRead;
                        }
                        if (totalBytesRead != contentLength) {
                            Map<String, String> errorResponse = new HashMap<>();
                            errorResponse.put("error", "Incomplete request body: expected " + contentLength + " bytes, got " + totalBytesRead);
                            return jsonResponse(Response.Status.BAD_REQUEST, errorResponse);
                        }

                        String jsonString = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
                        Log.d(TAG, "Received JSON: " + jsonString);
                        JsonObject json = gson.fromJson(jsonString, JsonObject.class);
                        Log.d(TAG, "Parsed JSON successfully");

                        SharedPreferences.Editor editor = prefs.edit();
                        // Use safe getters with defaults for boolean values
                        editor.putString("bot_token", json.has("botToken") ? json.get("botToken").getAsString() : "");
                        editor.putString("chat_id", json.has("chatId") ? json.get("chatId").getAsString() : "");
                        editor.putString("trusted_phone_number", json.has("trustedNumber") ? json.get("trustedNumber").getAsString() : "");
                        editor.putBoolean("chat_command", json.has("chatCommand") && json.get("chatCommand").getAsBoolean());
                        editor.putBoolean("battery_monitoring_switch", json.has("batteryMonitoring") && json.get("batteryMonitoring").getAsBoolean());
                        editor.putBoolean("charger_status", json.has("chargerStatus") && json.get("chargerStatus").getAsBoolean());
                        editor.putBoolean("fallback_sms", json.has("fallbackSms") && json.get("fallbackSms").getAsBoolean());
                        editor.putBoolean("verification_code", json.has("verificationCode") && json.get("verificationCode").getAsBoolean());
                        editor.putBoolean("privacy_mode", json.has("privacyMode") && json.get("privacyMode").getAsBoolean());
                        editor.putBoolean("doh_switch", json.has("dohSwitch") && json.get("dohSwitch").getAsBoolean());
                        editor.putBoolean("initialized", true);
                        editor.apply();

                        Log.d(TAG, "About to restart services (excluding WebConfigService)");
                        // Restart services (but NOT WebConfigService - it should stay running)
                        new Thread(() -> {
                            try {
                                Log.d(TAG, "Stopping other services");
                                // Stop only the other services, not WebConfigService
                                Intent batteryServiceIntent = new Intent(context, com.qwe7002.telegram_sms_compat.battery_service.class);
                                Intent chatCommandServiceIntent = new Intent(context, chat_command_service.class);
                                context.stopService(batteryServiceIntent);
                                context.stopService(chatCommandServiceIntent);

                                // Small delay to ensure services are stopped
                                Thread.sleep(500);

                                Log.d(TAG, "Starting services with batteryMonitoring=" +
                                    (json.has("batteryMonitoring") && json.get("batteryMonitoring").getAsBoolean()) +
                                    ", chatCommand=" + (json.has("chatCommand") && json.get("chatCommand").getAsBoolean()));

                                // Start services based on configuration
                                boolean batteryMonitoring = json.has("batteryMonitoring") && json.get("batteryMonitoring").getAsBoolean();
                                boolean chatCommand = json.has("chatCommand") && json.get("chatCommand").getAsBoolean();

                                if (batteryMonitoring) {
                                    context.startService(batteryServiceIntent);
                                }
                                if (chatCommand) {
                                    context.startService(chatCommandServiceIntent);
                                }

                                Log.d(TAG, "Services restarted successfully");
                            } catch (Exception e) {
                                Log.e(TAG, "Error restarting services", e);
                            }
                        }).start();

                        Map<String, String> response = new HashMap<>();
                        response.put("message", "Configuration saved successfully! Services restarting...");
                        Log.d(TAG, "Configuration saved successfully, returning response");
                        return jsonResponse(Response.Status.OK, response);
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving configuration", e);
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("error", "Failed to save configuration: " + e.getMessage());
                        return jsonResponse(Response.Status.INTERNAL_ERROR, errorResponse);
                    }
                }
                
                // GET /api/info - Get system information
                if (uri.equals("/api/info") && method == Method.GET) {
                    Map<String, Object> info = new HashMap<>();
                    
                    // Check if services are running
                    boolean servicesRunning = prefs.getBoolean("initialized", false);
                    info.put("serviceRunning", servicesRunning);
                    
                    // Android version
                    info.put("androidVersion", "Android " + Build.VERSION.RELEASE + 
                        " (API " + Build.VERSION.SDK_INT + ")");
                    
                    // App version
                    try {
                        PackageInfo pInfo = context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0);
                        info.put("appVersion", pInfo.versionName);
                    } catch (PackageManager.NameNotFoundException e) {
                        info.put("appVersion", "Unknown");
                    }
                    
                    // Battery level
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = context.registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        int batteryPct = (int)((level / (float)scale) * 100);
                        info.put("batteryLevel", batteryPct);
                    }
                    
                    return jsonResponse(Response.Status.OK, info);
                }
                
                // GET /api/test - Test Telegram connection
                if (uri.equals("/api/test") && method == Method.GET) {
                    // TODO: Implement actual Telegram connection test
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Connection test not implemented yet");
                    return jsonResponse(Response.Status.OK, response);
                }
                
                Map<String, String> notFoundResponse = new HashMap<>();
                notFoundResponse.put("error", "API endpoint not found");
                return jsonResponse(Response.Status.NOT_FOUND, notFoundResponse);
                    
            } catch (Exception e) {
                Log.e(TAG, "API error", e);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                return jsonResponse(Response.Status.INTERNAL_ERROR, errorResponse);
            }
        }

        private Response jsonResponse(Response.Status status, Object data) {
            String json = gson.toJson(data);
            return newFixedLengthResponse(status, "application/json", json);
        }
    }
}

