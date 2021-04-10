<template>
  <div>
    <v-card
      v-if="display"
      width="256"
      class="chat"
    >
      <v-chip-group
        v-for="chat in chats"
        :key="chat.chatId"
        class="active-chat"
      >
        <v-chip
          close
          close-icon="mdi-close"
          @click:close="leaveChat(chat.chatId)"
          @click="activeChat(chat.chatId)"
        >
          {{ chat.chatId }}
        </v-chip>
      </v-chip-group>
      
      <v-divider />

      <v-card-title>
        <span class="title font-weight-light">Chat {{ currentChat.chatId }}</span>

        <v-col class="text-right">
          <v-icon @click="toggleDisplay">
            mdi-close
          </v-icon>
        </v-col>
      </v-card-title>

      <v-card-text class="message-body">
        <div class="card">
          <div class="card-body">
            <div
              v-for="message in currentChat.messages"
              :key="message.message"
            >
              <span class="from">{{ message.from }}</span>
              <span class="message">
                {{ message.message }}
              </span>
            </div>
          </div>
        </div>

        <input
          v-model="newMessage"
          type="text"
        >
        <v-btn @click="sendMessage">
          Send
        </v-btn>
      </v-card-text>
    </v-card>
    <v-btn
      v-else
      elevation="2"
      fab
      class="open-chat"
      @click="toggleDisplay"
    >
      Chat
    </v-btn>
  </div>
</template>
<script>
export default {
  name: "Chat",
  props: [],
  data() {
    return {
      display: true,
      currentChat: {},
      newMessage: '',
      messages: [],
    };
  },
  methods: {
    toggleDisplay() {
      this.display = !this.display;
    },
    sendMessage() {
      this.$store.dispatch("chat/message", { chat: { chatId: this.currentChat.chatId, message: this.newMessage } });
    },
    activeChat(chatId) {
      this.currentChat = this.chats.find(c => c.chatId === chatId);
    },
    leaveChat(chatId) {
      this.$store.dispatch("chat/leave", { chat: { chatId } });
    }
  },
  mounted() {
    this.currentChat = this.chats[0];
  },
  computed: {
    chats: {
      get() {
        return this.$store.state.chat.chats;
      },
    },
  },
}
</script>
<style>
.open-chat {
  position: fixed !important;
  bottom: 33px;
  right: 20px;
}
.chat {
  position: fixed !important;
  bottom: 33px;
  right: 20px;
  
  border-top-left-radius: 4px;
  border-top-right-radius: 4px;
  border-top: 1px solid black;
  border-left: 1px solid black;
  border-right: 1px solid black;
}

h3 {
  font-size: 30px;
  text-align: center;
}

.message {
  font-size: 14px;
}
.from {
  color: teal;
  font-weight: bold;
}
.message-body input {
  width: 80%;
  border-radius: 4px;
  border: 1px solid rgb(156, 156, 156);
  height: 6vh;
  padding: 2px;
}
.card {
  width: 100%;
  height: 40vh;
}
.card-body {
  min-height: 40vh;
  overflow-y: scroll;
}
</style>
