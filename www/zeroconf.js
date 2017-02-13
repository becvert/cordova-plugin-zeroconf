'use strict';
var exec = require('cordova/exec');

var ZeroConf = {

    getHostname : function(success, failure) {
        return exec(success, failure, "ZeroConf", "getHostname", []);
    },

    register : function(type, domain, name, port, props, success, failure) {
        return exec(success, failure, "ZeroConf", "register", [ type, domain, name, port, props ]);
    },

    unregister : function(type, domain, name, success, failure) {
        return exec(success, failure, "ZeroConf", "unregister", [ type, domain, name ]);
    },

    stop : function(success, failure) {
        return exec(success, failure, "ZeroConf", "stop", []);
    },

    watch : function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "watch", [ type, domain ]);
    },

    unwatch : function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "unwatch", [ type, domain ]);
    },

    close : function(success, failure) {
        return exec(success, failure, "ZeroConf", "close", []);
    }

};

module.exports = ZeroConf;