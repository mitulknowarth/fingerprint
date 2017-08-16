var obj = {};

obj.authenticate = function(message, successCallback, errorCallback) {
    cordova.exec(
      successCallback, errorCallback,
      "FingerprintHelper", "authenticate", [message]);
};

obj.isAuthAvailable = function(message, successCallback, errorCallback) {
  cordova.exec(
    successCallback, errorCallback,
    "FingerprintHelper", "isAuthAvailable", [message]);
};

module.exports = obj;
