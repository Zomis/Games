import Vue from "vue";

const socket = new WebSocket("ws://127.0.0.1:8081");

const emitter = new Vue({
  methods: {
    send(message) {
      if (1 === socket.readyState) {
        console.log("OUT: " + message);
        socket.send(message);
      }
    }
  }
});

socket.onopen = function(event) {
  socket.send("VUEJS");
};

socket.onmessage = function(msg) {
  console.log(" IN: " + msg.data);
  emitter.$emit("message", msg.data);

  let obj = JSON.parse(msg.data);
  emitter.$emit(`type:${obj.type}`, obj);
};
socket.onerror = function(err) {
  emitter.$emit("error", err);
};

export default emitter;
