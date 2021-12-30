<template>
  <v-card>
    <v-text-field v-model="message" />
    <v-btn @click="connect">
      Connect
    </v-btn>
    <v-btn @click="login">
      Login
    </v-btn>
    <v-btn @click="joinLobby">
      Join Lobby
    </v-btn>
    <v-btn @click="chat">
      Chat
    </v-btn>
    <v-btn @click="disconnect">
      Disconnect
    </v-btn>
  </v-card>
</template>

<script>
export default {
  name: "AWSTestScreen",
  components: { },
  data() {
    return {
      message: "Hello World!",
      ws: null
    }
  },
  methods: {
    connect() {
      console.log("connect");
      let ws = new WebSocket("wss://dfftey92j9.execute-api.eu-central-1.amazonaws.com/production");
      ws.onclose = e => console.log(e);
      ws.onopen = e => console.log(e);
      ws.onmessage = e => console.log(e, JSON.parse(e.data));
      ws.onerror = e => console.error(e);
      this.ws = ws;
    },
    login() {
      this.send({ route: "auth/guest" });
    },
    joinLobby() {
      this.send({ route: "lobby/Minesweeper/join" });
    },
    chat() {
      this.send({ route: "lobby/Minesweeper/chat", message: this.message });
    },
    disconnect() {
      this.ws.close();
    },
    send(data) {
      console.log("SEND:", data);
      this.ws.send(JSON.stringify(data));
    }
  }
}
</script>
