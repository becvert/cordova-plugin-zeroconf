# Cordova ZeroConf Plugin

This plugin allows you to browse and publish ZeroConf/Bonjour/mDNS services from applications developed using PhoneGap/Cordova 3.0 or newer.

This is not a background service. When the cordova view is destroyed/terminated, publish and watch operations are stopped.

[CHANGELOG](https://github.com/becvert/cordova-plugin-zeroconf/blob/master/CHANGELOG.md)

## Installation ##

In your application project directory:

```bash
cordova plugin add cordova-plugin-zeroconf
```

## Usage ##

```javascript
var zeroconf = cordova.plugins.zeroconf;
```

For Android, you may want set the following options to speed discovery up:
 
```javascript 
zeroconf.registerAddressFamily = 'ipv4'; // or 'ipv6' ('any' by default)
zeroconf.watchAddressFamily = 'ipv4'; // or 'ipv6' ('any' by default)
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
    if (action == 'added') {
        console.log('service added', service);
    } else if (action == 'resolved') {
        console.log('service resolved', service);
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

#### `reInit(success, failure)`
Re-initializes the entire plugin, which resets the browsers and services. Use this if the WiFi network has changed while the app is running.

```javascript
zeroconf.reInit()
```

## Credits

#### Android
It depends on [the JmDNS library](https://github.com/jmdns/jmdns)

#### iOS
Implements [Apple's Bonjour](https://developer.apple.com/bonjour/)

## Licence ##

The MIT License
