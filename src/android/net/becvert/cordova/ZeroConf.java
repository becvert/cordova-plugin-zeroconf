/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */

package net.becvert.cordova;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class ZeroConf extends CordovaPlugin implements ServiceListener {

    private static final String TAG = "ZeroConf";

    WifiManager.MulticastLock lock;
    private JmDNS publisher;
    private JmDNS browser;
    private String hostname;
    private Map<String, CallbackContext> callbacks = new HashMap<String, CallbackContext>();

    // publisher
    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_UNREGISTER = "unregister";
    public static final String ACTION_STOP = "stop";
    // browser
    public static final String ACTION_WATCH = "watch";
    public static final String ACTION_UNWATCH = "unwatch";
    public static final String ACTION_CLOSE = "close";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        WifiManager wifi = (WifiManager) this.cordova.getActivity()
                .getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("ZeroConfPluginLock");
        lock.setReferenceCounted(true);
        lock.acquire();

        hostname = getHostName("android");

        Log.v(TAG, "Initialized");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (publisher != null) {
            try {
                publisher.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                publisher = null;
            }
        }
        if (browser != null) {
            try {
                browser.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                browser = null;
                callbacks = null;
            }
        }
        if (lock != null) {
            lock.release();
            lock = null;
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {

        if (ACTION_REGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);
            final int port = args.optInt(3);
            final JSONObject props = args.optJSONObject(4);

            Log.d(TAG, "Register " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ServiceInfo service = register(type, domain, name, port, props);

                        JSONObject status = new JSONObject();
                        status.put("action", "registered");
                        status.put("service", jsonifyService(service));

                        Log.d(TAG, "Sending result: " + status.toString());

                        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                        callbackContext.sendPluginResult(result);

                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                        callbackContext.error("Registration of " + type + domain + " with name " + name + " failed");
                    }
                }
            });

        } else if (ACTION_UNREGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);

            Log.d(TAG, "Unregister " + type + domain);

            if (publisher != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        unregister(type, domain, name);
                        callbackContext.success("Unregistered " + type + domain + " with name " + name);
                    }
                });
            }

        } else if (ACTION_STOP.equals(action)) {

            Log.d(TAG, "Stop");

            if (publisher != null) {
                final JmDNS p = publisher;
                publisher = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p.close();
                            callbackContext.success("Publisher did stop");
                        } catch (IOException e) {
                            e.printStackTrace();
                            callbackContext.error("Publisher did not stop properly");
                        }
                    }
                });
            }

        } else if (ACTION_WATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d(TAG, "Watch " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        watch(type, domain);
                    } catch (IOException e) {
                        e.printStackTrace();
                        callbackContext.error("Could not watch " + type + domain);
                    }
                }
            });

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);
            callbacks.put(type + domain, callbackContext);

        } else if (ACTION_UNWATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d(TAG, "Unwatch " + type + domain);

            if (browser != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        unwatch(type, domain);
                        callbackContext.success("Stopped watching " + type + domain);
                    }
                });

                PluginResult result = new PluginResult(Status.NO_RESULT);
                result.setKeepCallback(false);
                callbacks.remove(type + domain);
            }

        } else if (ACTION_CLOSE.equals(action)) {

            Log.d(TAG, "Close");

            if (browser != null) {
                final JmDNS b = browser;
                browser = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            b.close();
                            callbackContext.success("Browser did close");
                        } catch (IOException e) {
                            e.printStackTrace();
                            callbackContext.error("Browser did not close properly");
                        }
                    }
                });

                callbacks.clear();
            }

        } else {
            Log.e(TAG, "Invalid action: " + action);
            callbackContext.error("Invalid action: " + action);
            return false;
        }

        return true;
    }

    private ServiceInfo register(String type, String domain, String name, int port, JSONObject props)
            throws IOException, JSONException {
        if (publisher == null) {
            publisher = JmDNS.create(null, hostname);
        }

        HashMap<String, String> txtRecord = new HashMap<String, String>();
        if (props != null) {
            Iterator<String> iter = props.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                txtRecord.put(key, props.getString(key));
            }
        }

        ServiceInfo service = ServiceInfo.create(type + domain, name, port, 0, 0, txtRecord);
        publisher.registerService(service);
        return service;
    }

    private void unregister(String type, String domain, String name) {
        if (publisher == null) {
            return;
        }

        ServiceInfo serviceInfo = publisher.getServiceInfo(type + domain, name);
        if (serviceInfo != null) {
            publisher.unregisterService(serviceInfo);
        }
    }

    private void watch(String type, String domain) throws IOException {
        if (browser == null) {
            browser = JmDNS.create(null, hostname);
        }

        browser.addServiceListener(type + domain, this);

        ServiceInfo[] services = browser.list(type + domain);
        for (ServiceInfo service : services) {
            sendCallback("added", service);
        }
    }

    private void unwatch(String type, String domain) {
        if (browser != null) {
            browser.removeServiceListener(type + domain, this);
        }
    }

    @Override
    public void serviceResolved(ServiceEvent ev) {
        Log.d(TAG, "Resolved");

        sendCallback("added", ev.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent ev) {
        Log.d(TAG, "Removed");

        sendCallback("removed", ev.getInfo());
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.d(TAG, "Added");

        // Force serviceResolved to be called again
        if (browser != null) {
            browser.requestServiceInfo(event.getType(), event.getName(), 1);
        }
    }

    public void sendCallback(String action, ServiceInfo service) {
        CallbackContext callbackContext = callbacks.get(service.getType());
        if (callbackContext == null) {
            return;
        }

        JSONObject status = new JSONObject();
        try {
            status.put("action", action);
            status.put("service", jsonifyService(service));
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.error("Error while jsonifying service " + service.getType());
            return;
        }

        Log.d(TAG, "Sending result: " + status.toString());

        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    public static JSONObject jsonifyService(ServiceInfo service) throws JSONException {
        JSONObject obj = new JSONObject();

        String domain = service.getDomain() + ".";
        obj.put("domain", domain);
        obj.put("type", service.getType().replace(domain, ""));
        obj.put("name", service.getName());
        obj.put("port", service.getPort());
        obj.put("hostname", service.getServer());

        JSONArray addresses = new JSONArray();
        String[] add = service.getHostAddresses();
        for (int i = 0; i < add.length; i++) {
            addresses.put(add[i]);
        }
        obj.put("addresses", addresses);
        
        JSONObject props = new JSONObject();
        Enumeration<String> names = service.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            props.put(name, service.getPropertyString(name));
        }
        obj.put("txtRecord", props);

        return obj;

    }

    // http://stackoverflow.com/questions/21898456/get-android-wifi-net-hostname-from-code
    public static String getHostName(String defValue) {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, "net.hostname").toString();
        } catch (Exception ex) {
            return defValue;
        }
    }

}
