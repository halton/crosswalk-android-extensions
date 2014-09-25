var Error = function(error) {
  document.title = "Fail";
  console.log(error.message);
};

function enumerateAllProps(obj) {
  var msg = '';
  var props = Object.getOwnPropertyNames(obj);
  for (var j = 0; j < props.length; ++j) {
    msg += props[j] + ': ' + obj[props[j]] + '\n';
  }
  return msg;
}

var successCount = 0;
function onSuccess() {
  // Only count the success get operation, the event part need manual interaction.
  ++successCount;
  if(successCount >= 5) {
    document.title = "Pass";
  }
}

window.onload = function() {
  if (typeof window.ardrone === "undefined") {
    document.title = "Fail";
    console.log("Ardrone not supported!!!");
    return;
  }

  try {
    document.getElementById("connect").onclick = function () {
      ardrone.connect().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("quit").onclick = function () {
      ardrone.quit().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("takeoff").onclick = function () {
      ardrone.takeoff().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("landing").onclick = function () {
      ardrone.landing().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("ftrim").onclick = function () {
      ardrone.ftrim().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("hover").onclick = function () {
      ardrone.hover().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("pitch_plus").onclick = function () {
      ardrone.pitch_plus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("pitch_minus").onclick = function () {
      ardrone.pitch_minus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("roll_plus").onclick = function () {
      ardrone.roll_plus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("roll_minus").onclick = function () {
      ardrone.roll_minus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("yaw_plus").onclick = function () {
      ardrone.yaw_plus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
    document.getElementById("yaw_minus").onclick = function () {
      ardrone.yaw_minus().then(function(msg) {
        document.getElementById('message').innerHTML += enumerateAllProps(msg);
        onSuccess();
      }, Error);
    };
  } catch(e) {
    console.log(e);
  }
};
