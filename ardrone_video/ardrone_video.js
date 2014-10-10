// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

var g_async_calls = [];
var g_next_async_call_id = 0;

var g_listeners = [];
var g_next_listener_id = 0;

var ARDroneVideoCodec = {
  // A MJPG-like codec, which was the default one until 1.6.4.
  VLIB: 1,
  // A h264-like codec, which should be the default one after 1.6.4.
  P264: 2
};

window.ARDroneVideoCodec = ARDroneVideoCodec;

var ARDroneVideoChannel = {
  ZAP_CHANNEL_HORI : 1,
  ZAP_CHANNEL_VERT : 2,
  ZAP_CHANNEL_LARGE_HORI_SMALL_VERT : 3,
  ZAP_CHANNEL_LARGE_VERT_SMALL_HORI : 4
};

window.ARDroneVideoChannel = ARDroneVideoChannel;

function ARDroneVideoOption (){
  this.ipAddress = '192.168.1.1';
  this.port = 5555;
  this.latency = 1000; // how much duration for the pipeline, in millisecond
  this.bitrate = 15000; // also called fps, in millisecond
  this.codec = ARDroneVideoCodec.P264;
  this.channel = ARDroneVideoChannel.ZAP_CHANNEL_HORI;
};

window.ARDroneVideoOption = ARDroneVideoOption;

exports.isPlaying = false;

exports.play = function(option) {
  if (exports.isPlaying) {
    console.log('Video is playing, please stop first.');
    return;
  }
  if (_isARDroneVideoOption(option)) {
    exports.option = option;
  } else if (!_isARDroneVideoOption(exports.option)) {
    exports.option = new ARDroneVideoOption();
  }

  var msg = {
    'cmd': 'play',
    'option': exports.option
  };

  return _createPromise(msg);
};

exports.stop = function() {
  if (!exports.isPlaying) {
    console.log('Video is not playing, nothing to do.');
    return;
  }

  var msg = {
    'cmd': 'stop'
  };
  return _createPromise(msg);
};

window.ARDroneVideoEvent = function(data) {
  _addConstProperty(this, 'videoFile', _createConstClone(data));
  this.prototype = new Event('ARDroneVideoEvent');
};

extension.setMessageListener(function(json) {
  var msg = JSON.parse(json);

  // Handle events
  if (msg.reply == 'newvideoready') {
    for (var id in g_listeners) {
      var event = new ARDroneVideoEvent(msg.data);
      g_listeners[id](event);
    }
  }

  // Handle promises
  if (msg.data.error) {
    g_async_calls[msg.asyncCallId].reject(msg.data.error);
  } else {
    g_async_calls[msg.asyncCallId].resolve(msg.data); 
    exports.isPlaying = g_async_calls[msg.asyncCallId].type === 'play';
  }

  delete g_async_calls[msg.asyncCallId];
});

exports.addEventListener = function(eventName, callback) {
  if (eventName != 'newvideoready') {
    console.log('Unsupportted event: ' + eventName);
    return;
  }
  g_listeners[++g_next_listener_id] = callback;
};

function _AsyncCall(type, resolve, reject) {
  this.type = type;
  this.resolve = resolve;
  this.reject = reject;
}

function _createPromise(msg) {
  var promise = new Promise(function(resolve, reject) {
    g_async_calls[g_next_async_call_id] = new _AsyncCall(msg.cmd, resolve, reject);
  });

  msg.asyncCallId = g_next_async_call_id;
  extension.postMessage(JSON.stringify(msg));
  ++g_next_async_call_id;
  return promise;
}

function _defineReadOnlyProperty(object, key, value) {
  Object.defineProperty(object, key, {
    configurable: false,
    writable: false,
    value: value
  });
}

function _isARDroneVideoOption(option) {
  if (!option) return false;

  var tmp_option = new ARDroneVideoOption();
  for (var key in tmp_option)
    if (!option.hasOwnProperty(key))
      return false;

  return true;
}
