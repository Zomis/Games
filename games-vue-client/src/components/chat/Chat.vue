<template>
  <div>
    <v-card
      v-if="display"
      width="256"
      class="chat"
    >
      <v-chip-group
        active-class="active-chat"
        column
      >
        <v-chip
          v-for="chat in chats"
          :key="chat.chatId"
          close
          close-icon="mdi-close"
          @click:close="leaveChat(chat.chatId)"
          @click="activeChat(chat.chatId)"
        >
          {{ chat.chatName }}
        </v-chip>
      </v-chip-group>
      
      <v-divider />

      <v-card-title>
        <span class="title font-weight-light">Chat {{ currentChat.chatName }}</span>

        <v-col class="text-right">
          <v-icon @click="toggleDisplay">
            mdi-close
          </v-icon>
        </v-col>
      </v-card-title>

      <v-card-text>
        <div class="messages">
          <div
            ref="container"
            class="messages-body"
          >
            <div
              v-for="message in currentChat.messages"
              :key="message.message + message.from + message.date"
            >
              <span class="from">{{ message.from }}</span>
              <span class="message">
                {{ message.message }}
              </span>
            </div>
          </div>
        </div>

        <v-textarea
          v-model="newMessage"
          label="Message"
          rows="2"
          counter
          maxlength="100"
          dense
          full-width
          autofocus
          no-resize
          hint="Please be respectful"
          append-outer-icon="mdi-send"
          @click:append-outer="sendMessage"
          @keyup.enter="sendMessage"
        />
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
import { mapState } from "vuex"

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
    scrollToEnd () {
      var content = this.$refs.container;
      content.scrollTop = content.scrollHeight;
    },
    toggleDisplay() {
      this.display = !this.display;
    },
    sendMessage() {
      if (this.newMessage.trim().length > 0) {
        this.$store.dispatch("chat/message", { chat: { chatId: this.currentChat.chatId, message: this.newMessage.trim() } });
        this.scrollToEnd();
      }
      this.newMessage = '';
    },
    activeChat(chatId) {
      this.currentChat = this.chats[chatId];
    },
    leaveChat(chatId) {
      this.$store.dispatch("chat/leave", { chat: { chatId } });
    }
  },
  mounted() {
    this.currentChat = this.chats.server;
  },
  computed: {
    ...mapState("chat", {
      chats(state) {
        return state.chats;
      },
    }),
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
  border-bottom-left-radius: 0;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 0;
  border-top: 1px solid black;
  border-left: 1px solid black;
  border-right: 1px solid black;
}

.chat .v-card__title {
  padding: 2px;
}
.chat .v-card__text {
  padding-right: 0;
  padding-bottom: 0;
  padding-left: 2px;
}
.chat .active-chat {
  font-weight: bold;
}
.chat h3 {
  font-size: 30px;
  text-align: center;
}
.chat .message {
  font-size: 14px;
}
.chat .from {
  color: teal;
  font-weight: bold;
}
.chat .messages {
  display: flex;
  height: 40vh;
}
.chat .messages-body {
  flex-direction: column;
  width: 100%;
  overflow-y: scroll;
}
</style>
