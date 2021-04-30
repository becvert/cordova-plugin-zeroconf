// Type definitions for Apache Cordova Zeroconf (mDNS) plugin
// Project: https://github.com/becvert/cordova-plugin-zeroconf

declare namespace ZeroConfPlugin {
    interface TxtRecord {
        [key: string]: string
    }
    
    /* service : {
        'domain' : 'local.',
        'type' : '_http._tcp.',
        'name': 'Becvert\'s iPad',
        'port' : 80,
        'hostname' : 'ipad-of-becvert.local',
        'ipv4Addresses' : [ '192.168.1.125' ], 
        'ipv6Addresses' : [ '2001:0:5ef5:79fb:10cb:1dbf:3f57:feb0' ],
        'txtRecord' : {
            'foo' : 'bar'
    } */
    interface Service {
        domain: string;
        type: string;
        name: string;
        port: number;
        hostname: string;
        ipv4Addresses: Array<string>;
        ipv6Addresses: Array<string>;
        txtRecord: TxtRecord;
    }
    
    interface Result {
        /** added, resolved, registered */
        action: string;
        service: Service;
    }
    
    interface ZeroConf {
        /** This plugin allows you to browse and publish ZeroConf/Bonjour/mDNS services from applications developed using PhoneGap/Cordova 3.0 or newer and Ionic's Capacitor. */
    
        /** any, ipv6 or ipv4 */
        registerAddressFamily: string;
        /** any, ipv6 or ipv4 */
        watchAddressFamily: string;
    
        /**
         * Returns this device's hostname.
         * @param successCallback   The callback that is called when the plugin returns the hostname.
         * @param errorCallback     A callback that is called when errors happen.
         */
        getHostname(
            successCallback: (hostname: string) => void,
            errorCallback?: (error: string) => void): void;
    
        /**
         * Publishes a new service.
         * @param type 
         * @param domain
         * @param name
         * @param port
         * @param txtRecord
         * @param successCallback   The callback that is called when the plugin completes successully.
         * @param errorCallback     A callback that is called when errors happen.
         */
        register(
            type: string,
            domain: string,
            name: string,
            port: number,
            txtRecord: TxtRecord,
            successCallback: (result: Result) => void,
            errorCallback?: (error: string) => void): void;
    
        /**
        * Unregisters a service.
        * @param type 
        * @param domain
        * @param name
        * @param successCallback   The callback that is called when the plugin completes successully.
        * @param errorCallback     A callback that is called when errors happen.
        */
        unregister(
            type: string,
            domain: string,
            name: string,
            successCallback: () => void,
            errorCallback?: (error: string) => void): void;
    
        /**
        * Unregisters all published services.
        * @param successCallback   The callback that is called when the plugin completes successully.
        * @param errorCallback     A callback that is called when errors happen.
        */
        stop(
            successCallback: () => void,
            errorCallback?: (error: string) => void): void;
    
        /**
        * Starts watching for services of the specified type.
        * @param type 
        * @param domain
        * @param successCallback   The callback that is called when the plugin completes successully.  Also called whenever a new service is discovered or resolved.
        * @param errorCallback     A callback that is called when errors happen.
        */
        watch(
            type: string,
            domain: string,
            successCallback: (result: Result) => void,
            errorCallback?: (error: string) => void): void;
    
        /**
        * Stops watching for services of the specified type.
        * @param type 
        * @param domain
        * @param successCallback   The callback that is called when the plugin completes successully.  Also called whenever a new service is discovered or resolved.
        * @param errorCallback     A callback that is called when errors happen.
        */
        unwatch(
            type: string,
            domain: string,
            successCallback: () => void,
            errorCallback?: (error: string) => void): void;
    
    
        /**
        * Closes the service browser and stops watching.
        * @param successCallback   The callback that is called when the plugin completes successully.
        * @param errorCallback     A callback that is called when errors happen.
        */
        close(
            successCallback: () => void,
            errorCallback?: (error: string) => void): void;
    
        /**
        * Re-initializes the entire plugin, which resets the browsers and services. Use this if the WiFi network has changed while the app is running.
        * @param successCallback   The callback that is called when the plugin completes successully.
        * @param errorCallback     A callback that is called when errors happen.
        */
        reInit(
            successCallback: () => void,
            errorCallback?: (error: string) => void): void;
    }
}
