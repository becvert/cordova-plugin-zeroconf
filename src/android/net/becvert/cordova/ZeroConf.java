/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap
 * by Sylvain Brejeon
 */

package net.becvert.cordova;

import android.os.Build;
import android.util.Log;

import com.github.druk.rxdnssd.BonjourService;
import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdBindable;
import com.github.druk.rxdnssd.RxDnssdEmbedded;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class ZeroConf extends CordovaPlugin {

    private RegistrationManager publisher;
    private BrowserManager browser;
    private Map<String, BonjourService> registerServices = new HashMap<String, BonjourService>();
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

        final RxDnssd rxDnssd = createDnssd();

        this.publisher = new RegistrationManager(rxDnssd);
        this.browser = new BrowserManager(rxDnssd);

        Log.v("ZeroConf", "Initialized");
    }

    private RxDnssd createDnssd() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return new RxDnssdEmbedded();
        }
        if (Build.VERSION.RELEASE.contains("4.4.2") && Build.MANUFACTURER.toLowerCase().contains("samsung")) {
            return new RxDnssdEmbedded();
        }
        return new RxDnssdBindable(this.cordova.getActivity());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        this.publisher.close();
        this.registerServices.clear();

        this.browser.close();
        this.callbacks.clear();
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {

        if (ACTION_REGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);
            final int port = args.optInt(3);
            final JSONObject props = args.optJSONObject(4);

            Log.d("ZeroConf", "Register " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    register(type, domain, name, port, props, callbackContext);
                }
            });
        } else if (ACTION_UNREGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);

            Log.d("ZeroConf", "Unregister " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    unregister(type, domain, name);
                }
            });
        } else if (ACTION_STOP.equals(action)) {

            Log.d("ZeroConf", "Stop");
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    publisher.close();
                    registerServices.clear();
                }
            });
        } else if (ACTION_WATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d("ZeroConf", "Watch " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    watch(type, domain, callbackContext);
                }
            });
        } else if (ACTION_UNWATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d("ZeroConf", "Unwatch " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    unwatch(type, domain);
                }
            });
        } else if (ACTION_CLOSE.equals(action)) {

            Log.d("ZeroConf", "Close");
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    browser.close();
                    callbacks.clear();
                }
            });
        } else {
            Log.e("ZeroConf", "Invalid action: " + action);
            callbackContext.error("Invalid action: " + action);
            return false;
        }

        return true;
    }

    private void register(String type, String domain, String name, int port, JSONObject props, final CallbackContext callbackContext) {
        unregister(type, domain, name);
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
        BonjourService service = new BonjourService.Builder(0, 0, name, type, domain)
                .port(port)
                .dnsRecords(txtRecord)
                .build();
        publisher.register(service)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BonjourService>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        callbackContext.error("Register sevice error: " + e);
                    }

                    @Override
                    public void onNext(BonjourService bonjourService) {
                        Log.d("ZeroConf", "Register " + bonjourService);
                        JSONObject status = new JSONObject();
                        try {
                            status.put("action", "register_success");
                            status.put("service", jsonifyService(bonjourService));

                            Log.d("ZeroConf", "Sending result: " + status.toString());

                            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        registerServices.put(type + "@@@" + domain + "@@@" + name, service);
    }

    private void unregister(String type, String domain, String name) {
        if (domain == null) {
            domain = "";
        }
        if (name == null) {
            name = "";
        }
        BonjourService service = registerServices.remove(type + "@@@" + domain + "@@@" + name);
        if (service != null) {
            publisher.unregister(service);
        }
    }

    private void watch(String type, String domain, final CallbackContext callbackContext) {
        final String key = type + "@@@" + domain;
        callbacks.put(key, callbackContext);
        browser.watch(type, domain)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BonjourService>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        callbackContext.error("Watch error: " + e);
                    }

                    @Override
                    public void onNext(BonjourService bonjourService) {
                        Log.d("ZeroConf", "Watch " + bonjourService);
                        if (!bonjourService.isLost()) {
                            sendCallback("added", key, bonjourService);
                        } else {
                            sendCallback("removed", key, bonjourService);
                        }
                    }
                });
    }

    private void unwatch(String type, String domain) {
        callbacks.remove(type + "@@@" + domain);
        browser.unwatch(type, domain);
    }

    public void sendCallback(String action, String key, BonjourService info) {
        if (callbacks == null || callbacks.get(key) == null) {
            return;
        }

        JSONObject status = new JSONObject();
        try {
            status.put("action", action);
            status.put("service", jsonifyService(info));
            Log.d("ZeroConf", "Sending result: " + status.toString());

            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
            callbacks.get(key).sendPluginResult(result);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject jsonifyService(BonjourService info) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("domain", info.getDomain());
            obj.put("port", info.getPort());
            obj.put("name", info.getServiceName());
            obj.put("type", info.getRegType());

            JSONObject props = new JSONObject();
            Map<String, String> txtRecords = info.getTxtRecords();
            for (Map.Entry<String, String> txtRecord : txtRecords.entrySet()) {
                props.put(txtRecord.getKey(), txtRecord.getValue());
            }
            obj.put("txtRecord", props);

            JSONArray addresses = new JSONArray();
            InetAddress addV4 = info.getInet4Address();
            if (addV4 != null) {
                addresses.put(addV4.getHostAddress());
            }
            InetAddress addV6= info.getInet6Address();
            if (addV6 != null) {
                addresses.put(addV6.getHostAddress());
            }
            obj.put("addresses", addresses);
            JSONArray urls = new JSONArray();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return obj;

    }

    private static class RegistrationManager {

        private final Map<BonjourService, Subscription> registrations = new HashMap<BonjourService, Subscription>();
        private final RxDnssd rxDnssd;

        public RegistrationManager(RxDnssd rxDnssd) {
            this.rxDnssd = rxDnssd;
        }

        public Observable<BonjourService> register(BonjourService bonjourService) {
            PublishSubject<BonjourService> subject = PublishSubject.create();
            final Subscription[] subscriptions = new Subscription[1];
            subscriptions[0] = this.rxDnssd.register(bonjourService)
                    .doOnNext(new Action1<BonjourService>() {
                        public void call(BonjourService service) {
                            registrations.put(service, subscriptions[0]);
                        }
                    })
                    .subscribe(subject);
            return subject;
        }

        public void unregister(BonjourService service) {
            Subscription subscription = registrations.remove(service);
            if (subscription != null) {
                subscription.unsubscribe();
            }
        }

        public void close() {
            for (Subscription subscription : registrations.values()) {
                subscription.unsubscribe();
            }
            registrations.clear();
        }
    }

    private static class BrowserManager {

        private Map<String, Subscription> browsers = new HashMap<String, Subscription>();
        private final RxDnssd rxDnssd;

        public BrowserManager(RxDnssd rxDnssd) {
            this.rxDnssd = rxDnssd;
        }

        public Observable<BonjourService> watch(String type, String domain) {
            unwatch(type, domain);
            PublishSubject<BonjourService> subject = PublishSubject.create();
            Subscription browser = rxDnssd.browse(type, domain)
                    .compose(rxDnssd.resolve())
                    .compose(rxDnssd.queryRecords())
                    .subscribeOn(Schedulers.io())
                    .subscribe(subject);

            browsers.put(type + "@@@" + domain, browser);

            return subject;
        }

        public void unwatch(String type, String domain) {
            if (domain == null) {
                domain = "";
            }
            Subscription browser = browsers.remove(type + "@@@" + domain);
            if (browser != null) {
                browser.unsubscribe();
            }
        }

        public void close() {
            for (Subscription browser : browsers.values()) {
                browser.unsubscribe();
            }
            browsers.clear();
        }
    }
}
