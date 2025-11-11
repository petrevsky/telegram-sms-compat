package com.qwe7002.telegram_sms_compat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility replacement for PaperDB using SharedPreferences
 */
public class PaperCompat {
    private static final String TAG = "PaperCompat";
    private static Context context;
    private static Gson gson = new Gson();

    private final String bookName;
    private final SharedPreferences preferences;

    private PaperCompat(String bookName) {
        this.bookName = bookName;
        this.preferences = context.getSharedPreferences("paperdb_" + bookName, Context.MODE_PRIVATE);
    }

    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
    }

    public static PaperCompat book() {
        return new PaperCompat("default");
    }

    public static PaperCompat book(String bookName) {
        return new PaperCompat(bookName);
    }

    public void write(String key, Object value) {
        try {
            String json = gson.toJson(value);
            preferences.edit().putString(key, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error writing to PaperCompat", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T read(String key, T defaultValue) {
        try {
            String json = preferences.getString(key, null);
            if (json == null) {
                return defaultValue;
            }

            // Handle common types directly
            if (defaultValue instanceof String) {
                return (T) json;
            } else if (defaultValue instanceof Integer) {
                return (T) Integer.valueOf(json);
            } else if (defaultValue instanceof Long) {
                return (T) Long.valueOf(json);
            } else if (defaultValue instanceof Boolean) {
                return (T) Boolean.valueOf(json);
            } else if (defaultValue instanceof Float) {
                return (T) Float.valueOf(json);
            } else if (defaultValue instanceof Double) {
                return (T) Double.valueOf(json);
            } else {
                // Use Gson for complex objects
                Type type = getTypeForDefaultValue(defaultValue);
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading from PaperCompat", e);
            return defaultValue;
        }
    }

    public boolean contains(String key) {
        return preferences.contains(key);
    }

    public void delete(String key) {
        preferences.edit().remove(key).apply();
    }

    public void destroy() {
        preferences.edit().clear().apply();
    }

    private Type getTypeForDefaultValue(Object defaultValue) {
        if (defaultValue instanceof ArrayList) {
            return new TypeToken<ArrayList<String>>() {}.getType();
        } else if (defaultValue instanceof List) {
            return new TypeToken<List<String>>() {}.getType();
        } else if (defaultValue instanceof HashMap) {
            return new TypeToken<HashMap<String, String>>() {}.getType();
        } else if (defaultValue instanceof Map) {
            return new TypeToken<Map<String, String>>() {}.getType();
        } else {
            return defaultValue.getClass();
        }
    }
}
