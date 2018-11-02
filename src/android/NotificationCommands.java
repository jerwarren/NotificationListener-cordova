package net.coconauts.notificationListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import org.apache.cordova.PluginResult;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

public class NotificationCommands extends CordovaPlugin {

  private static final String TAG = "NotificationCommands";

  static final int ACTION_NOTIFICATION_LISTENER_SETTINGS_REQUEST = 1;

  private static final String LISTEN = "listen";

  // note that webView.isPaused() is not Xwalk compatible, so tracking it poor-man style
  private boolean isPaused;

  private static CallbackContext listener;
  private static CallbackContext notificationAccessRequestCallback;

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {

    Log.i(TAG, "Received action " + action);

    if (LISTEN.equals(action)) {
      setListener(callbackContext);
      return true;
    } else if (action.equals("requestNotificationAccess")) {
      requestNotificationAccess(callbackContext);
      return true;
    } else if (action.equals("hasNotificationAccess")) {
      sendHasNotificationAccessResult(callbackContext);
      return true;
    } else {
      callbackContext.error(TAG + ". " + action + " is not a supported function.");
      return false;
    }
  }

  @Override
  public void onPause(boolean multitasking) {
    this.isPaused = true;
  }

  @Override
  public void onResume(boolean multitasking) {
    this.isPaused = false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ACTION_NOTIFICATION_LISTENER_SETTINGS_REQUEST && notificationAccessRequestCallback != null) {
      sendHasNotificationAccessResult(notificationAccessRequestCallback);
      notificationAccessRequestCallback = null;
    }
  }

  public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
    notificationAccessRequestCallback = callbackContext;
  }

  public void sendHasNotificationAccessResult(CallbackContext callbackContext) {
    final boolean hasNotificationAccess = hasNotificationAccess();

    JSONObject result = new JSONObject();

    try {
      result.put("hasNotificationAccess", hasNotificationAccess);
    } catch (JSONException err) {
      callbackContext.error(err.getMessage());
      return;
    }

    callbackContext.success(result);
  }

  public boolean hasNotificationAccess() {
    Context context = cordova.getContext();
    ComponentName cn = new ComponentName(context, NotificationService.class);
    String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
    return flat != null && flat.contains(cn.flattenToString());
  }

  public void requestNotificationAccess(CallbackContext callbackContext) {
    notificationAccessRequestCallback = callbackContext;
    cordova.startActivityForResult(this, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        ACTION_NOTIFICATION_LISTENER_SETTINGS_REQUEST);

    // Send a plugin result with NO_RESULT and set KeepCallback as true
    PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
    r.setKeepCallback(true);
    notificationAccessRequestCallback.sendPluginResult(r);
  }

  public void setListener(CallbackContext callbackContext) {
    Log.i("Notification", "Attaching callback context listener " + callbackContext);
    listener = callbackContext;

    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
    result.setKeepCallback(true);
    callbackContext.sendPluginResult(result);
  }

  public static void notifyListener(StatusBarNotification n, String notificationType) {
    if (listener == null) {
      Log.e(TAG, "Must define listener first. Call notificationListener.listen(success,error) first");
      return;
    }
    try {

      JSONObject json = parse(n);

      json.put("notificationType", notificationType);

      PluginResult result = new PluginResult(PluginResult.Status.OK, json);

      Log.i(TAG, "Sending notification to listener " + json.toString());
      result.setKeepCallback(true);

      listener.sendPluginResult(result);
    } catch (Exception e) {
      Log.e(TAG, "Unable to send notification " + e);
      listener.error(TAG + ". Unable to send message: " + e.getMessage());
    }
  }

  private static JSONObject parse(StatusBarNotification n) throws JSONException {

    JSONObject json = new JSONObject();

    Bundle extras = n.getNotification().extras;

    json.put("title", getExtra(extras, "android.title"));
    json.put("package", n.getPackageName());
    json.put("notificationId", n.getId());
    json.put("notificationTag", n.getTag());
    json.put("postTime", n.getPostTime());
    json.put("text", getExtra(extras, "android.text"));
    json.put("textLines", getExtraLines(extras, "android.textLines"));

    return json;
  }

  private static String getExtraLines(Bundle extras, String extra) {
    try {
      CharSequence[] lines = extras.getCharSequenceArray(extra);
      return lines[lines.length - 1].toString();
    } catch (Exception e) {
      Log.d(TAG, "Unable to get extra lines " + extra);
      return "";
    }
  }

  private static String getExtra(Bundle extras, String extra) {
    try {
      return extras.get(extra).toString();
    } catch (Exception e) {
      return "";
    }
  }
}
