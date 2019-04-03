package net.becvert.cordova;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.druk.dnssd.BrowseListener;
import com.github.druk.dnssd.DNSSD;
import com.github.druk.dnssd.DNSSDBindable;
import com.github.druk.dnssd.DNSSDException;
import com.github.druk.dnssd.DNSSDRegistration;
import com.github.druk.dnssd.DNSSDService;
import com.github.druk.dnssd.NSClass;
import com.github.druk.dnssd.NSType;
import com.github.druk.dnssd.QueryListener;
import com.github.druk.dnssd.RegisterListener;
import com.github.druk.dnssd.ResolveListener;
import com.github.druk.dnssd.TXTRecord;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class ZeroConf extends CordovaPlugin {

    private static final String TAG = "ZeroConf";

    private DNSSD dnssd;
    private String hostname;
    private RegistrationManager registrationManager;
    private BrowserManager browserManager;

    public static final String ACTION_GET_HOSTNAME = "getHostname";
    // publisher
    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_UNREGISTER = "unregister";
    public static final String ACTION_STOP = "stop";
    // browser
    public static final String ACTION_WATCH = "watch";
    public static final String ACTION_UNWATCH = "unwatch";
    public static final String ACTION_CLOSE = "close";
    // Re-initialize
    public static final String ACTION_REINIT = "reInit";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        Context context = this.cordova.getActivity().getApplicationContext();
        dnssd = new DNSSDBindable(context);

        try {
            hostname = getHostName(cordova);
            Log.d(TAG, "Hostname " + hostname);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        Log.v(TAG, "Initialized");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (registrationManager != null) {
            try {
                registrationManager.stop();
            } catch (RuntimeException e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                registrationManager = null;
            }
        }
        if (browserManager != null) {
            try {
                browserManager.close();
            } catch (RuntimeException e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                browserManager = null;
            }
        }
        dnssd = null;

        Log.v(TAG, "Destroyed");
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {

        if (ACTION_GET_HOSTNAME.equals(action)) {

            if (hostname != null) {

                Log.d(TAG, "Hostname: " + hostname);

                callbackContext.success(hostname);

            } else {
                callbackContext.error("Error: undefined hostname");
                return false;
            }

        } else if (ACTION_REGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);
            final int port = args.optInt(3);
            final JSONObject props = args.optJSONObject(4);
            @SuppressWarnings("unused")
            final String addressFamily = args.optString(5);

            Log.d(TAG, "Register " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (registrationManager == null) {
                            registrationManager = new RegistrationManager();
                        }
                        registrationManager.register(name, type, domain, port, props, callbackContext);

                    } catch (DNSSDException e) {
                        Log.e(TAG, e.getMessage(), e);
                        callbackContext.error("Error: " + e.getMessage());
                    } catch (RuntimeException e) {
                        Log.e(TAG, e.getMessage(), e);
                        callbackContext.error("Error: " + e.getMessage());
                    }
                }
            });

        } else if (ACTION_UNREGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);

            Log.d(TAG, "Unregister " + type + domain);

            if (registrationManager != null) {
                final RegistrationManager rm = registrationManager;
                cordova.getThreadPool().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            rm.unregister(name, type, domain);
                            callbackContext.success();
                        } catch (DNSSDException e) {
                            Log.e(TAG, e.getMessage(), e);
                            callbackContext.error("Error: " + e.getMessage());
                        }
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_STOP.equals(action)) {

            Log.d(TAG, "Stop");

            if (registrationManager != null) {
                final RegistrationManager rm = registrationManager;
                registrationManager = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        rm.stop();
                        callbackContext.success();
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_WATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            @SuppressWarnings("unused")
            final String addressFamily = args.optString(2);

            Log.d(TAG, "Watch " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (browserManager == null) {
                            browserManager = new BrowserManager();
                        }
                        browserManager.watch(type, domain, callbackContext);

                    } catch (DNSSDException e) {
                        Log.e(TAG, e.getMessage(), e);
                        callbackContext.error("Error: " + e.getMessage());
                    } catch (RuntimeException e) {
                        Log.e(TAG, e.getMessage(), e);
                        callbackContext.error("Error: " + e.getMessage());
                    }
                }
            });

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (ACTION_UNWATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d(TAG, "Unwatch " + type + domain);

            if (browserManager != null) {
                final BrowserManager bm = browserManager;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        bm.unwatch(type, domain);
                        callbackContext.success();
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_CLOSE.equals(action)) {

            Log.d(TAG, "Close");

            if (browserManager != null) {
                final BrowserManager bm = browserManager;
                browserManager = null;
                cordova.getThreadPool().execute(new Runnable() {

                    @Override
                    public void run() {
                        bm.close();
                        callbackContext.success();
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_REINIT.equals(action)) {
            Log.e(TAG, "Re-Initializing");

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    onDestroy();
                    initialize(cordova, webView);
                    callbackContext.success();

                    Log.e(TAG, "Re-Initialization complete");
                }
            });

        } else {
            Log.e(TAG, "Invalid action: " + action);
            callbackContext.error("Invalid action: " + action);
            return false;
        }

        return true;
    }

    private class Publisher implements RegisterListener {

        private DNSSDService publisher = null;

        private CallbackContext callbackContext = null;

        public Publisher(CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
        }

        public void register(String name, String type, String domain, int port, JSONObject props) throws DNSSDException {
            TXTRecord txtRecord = null;
            if (props != null) {
                try {
                    txtRecord = new TXTRecord();
                    Iterator<String> iter = props.keys();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        txtRecord.set(key, props.getString(key));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            this.publisher = dnssd.register(0, DNSSD.ALL_INTERFACES, name, type, domain, null, port, txtRecord, this);
        }

        public void unregister() {
            this.publisher.stop();
        }

        @Override
        public void serviceRegistered(DNSSDRegistration registration, int flags, String serviceName, String regType, String domain) {
            if (dnssd == null) {
                // invalid state
                return;
            }
            try {
                String fullName = dnssd.constructFullName(serviceName, regType, domain);
                this.sendCallback("registered", jsonifyService(fullName, null, -1, null));
            } catch (DNSSDException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void operationFailed(DNSSDService service, int errorCode) {
            Log.d(TAG, "Operation failed " + errorCode);
        }

        public void sendCallback(String action, JSONObject service) {
            JSONObject status = new JSONObject();
            try {
                status.put("action", action);
                status.put("service", service);

                Log.d(TAG, "Sending result: " + status.toString());

                PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                callbackContext.sendPluginResult(result);

            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error("Error: " + e.getMessage());
            }
        }

    }

    private class RegistrationManager {

        private Map<String, Publisher> publishers = new HashMap<String, Publisher>();

        public void register(String name, String type, String domain, int port, JSONObject props, CallbackContext callbackContext) throws DNSSDException {
            Publisher publisher = new Publisher(callbackContext);
            publisher.register(name, type, domain, port, props);
            String fullName = dnssd.constructFullName(name, type, domain);
            publishers.put(fullName, publisher);
        }

        public void unregister(String name, String type, String domain) throws DNSSDException {
            String fullName = dnssd.constructFullName(name, type, domain);
            Publisher publisher = publishers.remove(fullName);
            if (publisher != null) {
                publisher.unregister();
            }
        }

        public void stop() {
            for (Publisher publisher : publishers.values()) {
                publisher.unregister();
            }
            publishers.clear();
        }

    }

    private class Browser implements BrowseListener, ResolveListener, QueryListener {

        private DNSSDService browser = null;

        private CallbackContext callbackContext = null;

        private Map<String, JSONObject> services = new HashMap<String, JSONObject>();

        public Browser(CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
        }

        private void watch(String type, String domain) throws DNSSDException {
            this.browser = dnssd.browse(0, DNSSD.ALL_INTERFACES, type, domain, this);
        }

        private void unwatch() {
            this.browser.stop();
        }

        @Override
        public void serviceFound(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
            Log.d(TAG, "Added");

            if (dnssd == null) {
                // invalid state
                return;
            }

            try {
                String fullName = dnssd.constructFullName(serviceName, regType, domain);
                services.put(fullName, jsonifyService(fullName, null, -1, null));
                this.sendCallback("added", fullName);
                dnssd.resolve(0, ifIndex, serviceName, regType, domain, this);
            } catch (DNSSDException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void serviceResolved(DNSSDService resolver, int flags, int ifIndex, String fullName, String hostName, int port, Map<String, String> txtRecord) {
            Log.d(TAG, "Resolved");

            if (dnssd == null) {
                // invalid state
                return;
            }

            try {
                services.put(fullName, jsonifyService(fullName, hostName, port, txtRecord));
                dnssd.queryRecord(0, ifIndex, hostName, NSType.A, NSClass.IN, this);
                dnssd.queryRecord(0, ifIndex, hostName, NSType.A6, NSClass.IN, this);
            } catch (DNSSDException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void queryAnswered(DNSSDService query, int flags, int ifIndex, String hostName, int rrtype, int rrclass, byte[] rdata, int ttl) {
            Log.d(TAG, "Queried");

            int family = rdata.length == 4 ? 4 : 6;
            String address = null;
            try {
                if (family == 4) {
                    address = Inet4Address.getByAddress(rdata).getHostAddress();
                } else {
                    address = Inet6Address.getByAddress(rdata).getHostAddress();
                }
                for (JSONObject service : services.values()) {
                    if (hostName.equals(service.optString("hostname"))) {
                        if (family == 4) {
                            JSONArray addresses = service.getJSONArray("ipv4Addresses");
                            addresses.put(address);
                        } else {
                            JSONArray addresses = service.getJSONArray("ipv6Addresses");
                            addresses.put(address);
                        }
                        this.sendCallback("resolved", service.getString("fullname"));
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (UnknownHostException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void serviceLost(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
            Log.d(TAG, "Removed");

            if (dnssd == null) {
                // invalid state
                return;
            }

            try {
                String fullName = dnssd.constructFullName(serviceName, regType, domain);
                this.sendCallback("removed", fullName);
            } catch (DNSSDException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void operationFailed(DNSSDService service, int errorCode) {
            Log.d(TAG, "Operation failed " + errorCode);
        }

        public void sendCallback(String action, String fullName) {
            JSONObject service = services.get(fullName);
            if (service == null) {
                return;
            }

            JSONObject status = new JSONObject();
            try {
                status.put("action", action);
                status.put("service", service);

                Log.d(TAG, "Sending result: " + status.toString());

                PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);

            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error("Error: " + e.getMessage());
            }
        }

    }

    private class BrowserManager {

        private Map<String, Browser> browsers = new HashMap<String, Browser>();

        private void watch(String type, String domain, CallbackContext callbackContext) throws DNSSDException {
            Browser browser = new Browser(callbackContext);
            browser.watch(type, domain);
            browsers.put(type + domain, browser);
        }

        private void unwatch(String type, String domain) {
            Browser browser = browsers.remove(type + domain);
            if (browser != null) {
                browser.unwatch();
            }
        }

        private void close() {
            for (Browser browser : browsers.values()) {
                browser.unwatch();
            }
            browsers.clear();
        }

    }

    private static JSONObject jsonifyService(String fullName, String hostName, int port, Map<String, String> txtRecord) throws JSONException {
        String[] split = fullName.split("\\.");
        String name = split[0];
        String type = split[1] + "." + split[2] + ".";
        String domain = split[3] + ".";

        JSONObject obj = new JSONObject();
        obj.put("fullname", fullName);
        obj.put("name", name);
        obj.put("type", type);
        obj.put("domain", domain);
        obj.put("hostname", hostName);
        obj.put("port", port);
        obj.put("ipv4Addresses", new JSONArray());
        obj.put("ipv6Addresses", new JSONArray());

        JSONObject props = new JSONObject();
        if (txtRecord != null) {
            for (Map.Entry<String, String> entry : txtRecord.entrySet()) {
                props.put(entry.getKey(), entry.getValue());
            }
        }
        obj.put("txtRecord", props);

        return obj;
    }

    // http://stackoverflow.com/questions/21898456/get-android-wifi-net-hostname-from-code
    public static String getHostName(CordovaInterface cordova) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method getString = Build.class.getDeclaredMethod("getString", String.class);
        getString.setAccessible(true);
        String hostName = getString.invoke(null, "net.hostname").toString();
        if (TextUtils.isEmpty(hostName)) {
            // API 26+ :
            // Querying the net.hostname system property produces a null result
            String id = Settings.Secure.getString(cordova.getActivity().getContentResolver(), Settings.Secure.ANDROID_ID);
            hostName = "android-" + id;
        }
        return hostName;
    }

}
