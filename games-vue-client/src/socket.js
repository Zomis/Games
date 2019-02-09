import Vue from "vue";
import store from "./store";

const emitter = new Vue({
  data() {
    return { socket: null, connected: false, serverURL: null };
  },
  methods: {
    isConnected() {
      if (this.socket == null) {
        return false;
      }
      if (this.socket.readyState != 1) {
        return false;
      }
      return this.connected;
    },
    getServerURL() {
      return this.serverURL;
    },
    connect(url) {
      console.log("Connecting to " + url);
      this.serverURL = url;
      this.socket = new WebSocket(url);
      this.socket.onopen = e => {
        this.connected = true;
        this.$emit("connected", e);
      };
      this.socket.onmessage = msg => {
        console.log(" IN: " + msg.data);
        this.$emit("message", msg.data);

        let obj = JSON.parse(msg.data);
        this.$emit(`type:${obj.type}`, obj);
        store.dispatch("onSocketMessage", obj);
      };
      this.socket.onerror = err => {
        this.$emit("error", err);
      };
    },
    disconnect() {
      this.socket.close();
      this.socket = null;
    },
    send(message) {
      if (this.socket === null) {
        throw "No socket.";
      }
      if (1 === this.socket.readyState) {
        console.log("OUT: " + message);
        this.socket.send(message);
      }
    }
  }
});

export default emitter;
