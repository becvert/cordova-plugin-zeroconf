# Cordova ZeroConf Plugin

This plugin allows you to browse and publish ZeroConf/Bonjour/mDNS services from applications developed using PhoneGap/Cordova 3.0 or newer.

This is not a background service. When the cordova view is destroyed/terminated, publish and watch operations are stopped.

## Changelog ##

#### 1.2.4
- plugin.xml: moving js clobbers from global to only supported platforms
- [iOS] fix for empty txtRecords

#### 1.2.3
- [Android] listening on all multicast and non-loopback interfaces instead of first found IPv4 and first found IPv6
- [Android] fixing java.util.concurrent.RejectedExecutionException (BrowserManager.serviceAdded)

#### 1.2.2
- [Android] re-registering while DNS entry is still cached causes IllegalStateException

#### 1.2.1
- [Android] check that NetworkInterface.supportsMulticast
- [iOS] add Hostname.m to the target

#### 1.2.0

- new getHostname function
- added parameter for domain
- added success/failure callbacks
- normalized service object
- more ipv6 support

## Installation ##

In your application project directory:

```bash
cordova plugin add cordova-plugin-zeroconf
```

## Usage ##

```javascript
var zeroconf = cordova.plugins.zeroconf;
```

#### `getHostname(success, failure)`
Returns this device's hostname.

```javascript
zeroconf.getHostname(function success(hostname){
    console.log(hostname); // ipad-of-becvert.local.
});
```

#### `register(type, domain, name, port, txtRecord, success, failure)`
Publishes a new service.

```javascript
zeroconf.register('_http._tcp.', 'local.', 'Becvert\'s iPad', 80, {
    'foo' : 'bar'
}, function success(result){
    var action = result.action; // 'registered'
    var service = result.service;
});
```

#### `unregister(type, domain, name, success, failure)`
Unregisters a service.

```javascript
zeroconf.unregister('_http._tcp.', 'local.', 'Becvert\'s iPad');
```

#### `stop(success, failure)`
Unregisters all published services.

```javascript
zeroconf.stop();
```

#### `watch(type, domain, success, failure)`
Starts watching for services of the specified type.

```javascript
zeroconf.watch('_http._tcp.', 'local.', function(result) {
    var action = result.action;
    var service = result.service;
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
        }
    } */
    if (action == 'added') {
        console.log('service added', service);
    } else {
        console.log('service removed', service);
    }
});
```

#### `unwatch(type, domain, success, failure)`
Stops watching for services of the specified type.

```javascript
zeroconf.unwatch('_http._tcp.', 'local.')
```

#### `close(success, failure)`
Closes the service browser and stops watching.

```javascript
zeroconf.close()
```

## Credits

#### Android
It depends on [the JmDNS library](http://jmdns.sourceforge.net/)

Many thanks to [cambiocreative](https://github.com/cambiocreative/cordova-plugin-zeroconf) that got me started

#### iOS
Implements https://developer.apple.com/bonjour/

## Licence ##

The MIT License
