/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */

'use strict';
var exec = require('cordova/exec');

var fail = function(o) {
    console.error("Error " + JSON.stringify(o));
}

var ZeroConf = {

    register : function(type, name, port, props) {
        return exec(null, fail, "ZeroConf", "register", [ type, name, port, props ]);
    },

    unregister : function(type, name) {
        return exec(null, fail, "ZeroConf", "unregister", [ type, name ]);
    },

    stop : function(type, name) {
        return exec(null, fail, "ZeroConf", "stop", []);
    },

    watch : function(type, success) {
        return exec(success, fail, "ZeroConf", "watch", [ type ]);
    },

    unwatch : function(type) {
        return exec(null, fail, "ZeroConf", "unwatch", [ type ]);
    },

    close : function() {
        return exec(null, fail, "ZeroConf", "close", []);
    }

};

module.exports = ZeroConf;
