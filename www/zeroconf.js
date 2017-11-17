'use strict';
var exec = require('cordova/exec');

var ZeroConf = {

    registerAddressFamily : 'any', /* or ipv6 or ipv4 */
    watchAddressFamily : 'any', /* or ipv6 or ipv4 */

    getHostname : function(success, failure) {
        return exec(success, failure, "ZeroConf", "getHostname", []);
    },

    register : function(type, domain, name, port, props, success, failure) {
        return exec(success, failure, "ZeroConf", "register", [ type, domain, name, port, props, this.registerAddressFamily ]);
    },

    unregister : function(type, domain, name, success, failure) {
        return exec(success, failure, "ZeroConf", "unregister", [ type, domain, name ]);
    },

    stop : function(success, failure) {
        return exec(success, failure, "ZeroConf", "stop", []);
    },

    watch : function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "watch", [ type, domain, this.watchAddressFamily ]);
    },

    unwatch : function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "unwatch", [ type, domain ]);
    },

    close : function(success, failure) {
        return exec(success, failure, "ZeroConf", "close", []);
    },

    reInit : function(success, failure) {
        return exec(success, failure, "ZeroConf", "reInit", []);
    }

};

module.exports = ZeroConf;