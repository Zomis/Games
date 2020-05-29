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
    connect(server) {
      let url = server.url;
      console.log("Connecting to " + url);
      this.serverURL = url;
      this.socket = new WebSocket(url);
      this.socket.onopen = e => {
        this.connected = true;
        this.$emit("connected", e);
        console.log("connected", e, url);
        store.dispatch("onSocketOpened", server)
      };
      this.socket.onmessage = msg => {
        let obj = JSON.parse(msg.data);
        console.log(" IN:", obj, msg.data);
        store.dispatch("onSocketMessage", obj);
        this.$emit(`type:${obj.type}`, obj);
      };
      this.socket.onclose = () => {
        console.log("Websocket closed");
        store.dispatch("onSocketClosed")
      };
      this.socket.onerror = err => {
        console.log("Websocket error");
        this.$emit("error", err);
        store.dispatch("onSocketError", err)
      };
    },
    disconnect() {
      if (this.socket != null) {
        this.socket.close();
      }
      this.socket = null;
    },
    route(path, data) {
      this.send(JSON.stringify({ route: path, ...data }))
    },
    send(message) {
      if (this.socket === null) {
        throw "No socket.";
      }
      if (1 === this.socket.readyState) {
        console.log("OUT:", message);
        this.socket.send(message);
      }
    }
  }
});

export default emitter;
