<template>
  <div class="server-selection">
    <select v-model="chosenServer">
      <option v-for="server in serverOptions" :value="server.url">{{ server.name }}</option>
    </select>
    <button @click="connect()">Connect</button>
  </div>
</template>

<script>
import Socket from "../socket";

const serverOptions = [
  {
    url: "ws://gbg.zomis.net:8082",
    name: "zomis"
  },
  {
    url: "ws://127.0.0.1:8081",
    name: "localhost-development"
  }
];

export default {
  name: "ServerSelection",
  data() {
    return {
      serverOptions: serverOptions,
      chosenServer: "zomis"
    };
  },
  methods: {
    connect: function() {
      Socket.connect(this.chosenServer);
    },
    connected: function(e) {
      this.$router.push("/connected");
    }
  },
  created() {
    Socket.$on("connected", this.connected);
  },
  beforeDestroy() {
    Socket.$off("connected", this.connected);
  }
};
</script>

<style scoped>
</style>
