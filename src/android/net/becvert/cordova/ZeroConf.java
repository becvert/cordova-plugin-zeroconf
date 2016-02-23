/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */

package net.becvert.cordova;

import android.net.wifi.WifiManager;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class ZeroConf extends CordovaPlugin implements ServiceListener {

    WifiManager.MulticastLock lock;
    private JmDNS publisher = null;
    private JmDNS browser = null;
    private String hostname = UUID.randomUUID().toString();
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

        WifiManager wifi = (WifiManager) this.cordova.getActivity().getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("ZeroConfPluginLock");
        lock.setReferenceCounted(true);
        lock.acquire();

        Log.v("ZeroConf", "Initialized");
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
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {

        if (ACTION_REGISTER.equals(action)) {

            final String type = args.optString(0);
            final String name = args.optString(1);
            final int port = args.optInt(2);
            final JSONObject props = args.optJSONObject(3);

            Log.d("ZeroConf", "Register " + type);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    register(type, name, port, props);
                }
            });

        } else if (ACTION_UNREGISTER.equals(action)) {

            final String type = args.optString(0);
            final String name = args.optString(1);

            Log.d("ZeroConf", "Unregister " + type);

            if (publisher != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        unregister(type, name);
                    }
                });
            }

        } else if (ACTION_STOP.equals(action)) {

            Log.d("ZeroConf", "Stop");

            if (publisher != null) {
                final JmDNS p = publisher;
                publisher = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

        } else if (ACTION_WATCH.equals(action)) {

            final String type = args.optString(0);

            Log.d("ZeroConf", "Watch " + type);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    watch(type);
                }
            });

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);
            callbacks.put(type, callbackContext);

        } else if (ACTION_UNWATCH.equals(action)) {

            final String type = args.optString(0);

            Log.d("ZeroConf", "Unwatch " + type);

            if (browser != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        unwatch(type);
                    }
                });

                PluginResult result = new PluginResult(Status.NO_RESULT);
                result.setKeepCallback(false);
                callbacks.remove(type);
            }

        } else if (ACTION_CLOSE.equals(action)) {

            Log.d("ZeroConf", "Close");

            if (browser != null) {
                final JmDNS b = browser;
                browser = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            b.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                callbacks.clear();
            }

        } else {
            Log.e("ZeroConf", "Invalid action: " + action);
            callbackContext.error("Invalid action: " + action);
            return false;
        }

        return true;
    }

    private void register(String type, String name, int port, JSONObject props) {
        if (publisher == null) {
            try {
                publisher = JmDNS.create(ZeroConf.getIPAddress(),
                        /* need a hostname to work! */ hostname);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        HashMap<String, String> txtRecord = new HashMap<String, String>();
        if (props != null) {
            Iterator<String> iter = props.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    txtRecord.put(key, props.getString(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            ServiceInfo service = ServiceInfo.create(type, name, port, 0, 0, txtRecord);
            publisher.registerService(service);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void unregister(String type, String name) {
        ServiceInfo serviceInfo = publisher.getServiceInfo(type, name);
        if (serviceInfo != null) {
            publisher.unregisterService(serviceInfo);
        }
    }

    private void watch(String type) {
        if (browser == null) {
            try {
                browser = JmDNS.create(ZeroConf.getIPAddress(),
                        /* need a hostname to work! */ hostname);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        browser.addServiceListener(type, this);

        ServiceInfo[] services = browser.list(type);
        for (ServiceInfo service : services) {
            sendCallback("added", service);
        }
    }

    private void unwatch(String type) {
        browser.removeServiceListener(type, this);
    }

    @Override
    public void serviceResolved(ServiceEvent ev) {
        Log.d("ZeroConf", "Resolved");

        sendCallback("added", ev.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent ev) {
        Log.d("ZeroConf", "Removed");

        sendCallback("removed", ev.getInfo());
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.d("ZeroConf", "Added");

        // Force serviceResolved to be called again
        if (browser != null) {
            browser.requestServiceInfo(event.getType(), event.getName(), 1);
        }
    }

    public void sendCallback(String action, ServiceInfo info) {
        if (callbacks == null || callbacks.get(info.getType()) == null) {
            return;
        }

        JSONObject status = new JSONObject();
        try {
            status.put("action", action);
            status.put("service", jsonifyService(info));
            Log.d("ZeroConf", "Sending result: " + status.toString());

            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
            callbacks.get(info.getType()).sendPluginResult(result);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public static JSONObject jsonifyService(ServiceInfo info) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("application", info.getApplication());
            obj.put("domain", info.getDomain());
            obj.put("port", info.getPort());
            obj.put("name", info.getName());
            obj.put("server", info.getServer());
            obj.put("description", info.getNiceTextString());
            obj.put("protocol", info.getProtocol());
            obj.put("qualifiedname", info.getQualifiedName());
            obj.put("type", info.getType());

            JSONObject props = new JSONObject();
            Enumeration<String> names = info.getPropertyNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                props.put(name, info.getPropertyString(name));
            }
            obj.put("txtRecord", props);

            JSONArray addresses = new JSONArray();
            String[] add = info.getHostAddresses();
            for (int i = 0; i < add.length; i++) {
                addresses.put(add[i]);
            }
            obj.put("addresses", addresses);
            JSONArray urls = new JSONArray();

            String[] url = info.getURLs();
            for (int i = 0; i < url.length; i++) {
                urls.put(url[i]);
            }
            obj.put("urls", urls);

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return obj;

    }

    /**
     * Returns the first found IP4 address.
     * 
     * @return the first found IP4 address
     */
    public static InetAddress getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        if (addr instanceof Inet4Address) {
                            return addr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
