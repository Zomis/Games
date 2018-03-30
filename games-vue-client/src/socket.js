import Vue from "vue";

const emitter = new Vue({
  data() {
    return { socket: null };
  },
  methods: {
    connect(url) {
      console.log("Connecting to " + url);
      this.socket = new WebSocket(url);
      this.socket.onopen = e => {
        this.$emit("connected", e);
      };
      this.socket.onmessage = msg => {
        console.log(" IN: " + msg.data);
        this.$emit("message", msg.data);

        let obj = JSON.parse(msg.data);
        this.$emit(`type:${obj.type}`, obj);
      };
      this.socket.onerror = err => {
        this.$emit("error", err);
      };
    },
    send(message) {
      if (1 === this.socket.readyState) {
        console.log("OUT: " + message);
        this.socket.send(message);
      }
    }
  }
});

export default emitter;
