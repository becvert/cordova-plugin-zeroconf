/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap
 * by Sylvain Brejeon
 */

'use strict';
var exec = require('cordova/exec');

var ZeroConf = {

    register: function(type, domain, name, port, props, success, failure) {
        return exec(success, failure, "ZeroConf", "register", [type, name, port, props]);
    },

    unregister: function(type, domain, name, success, failure) {
        return exec(success, failure, "ZeroConf", "unregister", [type, name]);
    },

    stop: function(success, failure) {
        return exec(success, failure, "ZeroConf", "stop", []);
    },

    watch: function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "watch", [type]);
    },

    unwatch: function(type, domain, success, failure) {
        return exec(success, failure, "ZeroConf", "unwatch", [type]);
    },

    close: function(success, failure) {
        return exec(success, failure, "ZeroConf", "close", []);
    }

};

module.exports = ZeroConf;
