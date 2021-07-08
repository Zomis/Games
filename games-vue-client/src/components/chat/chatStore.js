import Vue from "vue";

import Socket from "@/socket";

const chatStore = {
  namespaced: true,
  state: {
    chats: {
      'server': {
        chatId: 'server',
        chatName: 'server',
        users: [],
        messages: [],
      },
    }
  },
  getters: {
    chats: state => { return state.chats }
  },
  mutations: {
    addChat(state, data) {
      const { chatId, users } = data;
      Vue.set(state.chats, chatId, {
        chatId,
        chatName: chatId.replace("game-", ""),
        users,
        messages: [],
      });
    },
    removeChat(state, data) {
      const { chatId } = data;
      Vue.delete(state.chats, chatId);
    },
    addUserToChat(state, data) {
      const { chatId, user } = data;
      const chat = state.chats[chatId];
      chat.users.push(user);
    },
    removeUserFromChat(state, data) {
      const { chatId, user } = data;
      const chat = state.chats[chatId];
      chat.users = chat.users.filter(existingUser => existingUser.playerId !== user.playerId)
    },
    addMessageToChat(state, data) {
      const { chatId, message, from, date } = data;
      const chat = state.chats[chatId];
      chat.messages.push({ message, from, date });
    },
  },
  actions: {
    join(context, data) {
      const { chatId } = data.chat;
      // context.commit("addChat", { chatId, users: [] });
      Socket.route(`chat/${chatId}/join`, {});
    },
    leave(context, data) {
      const { chatId } = data.chat;
      // context.commit("removeChat", { chatId });
      Socket.route(`chat/${chatId}/leave`, {});
    },
    message(context, data) {
      const { chatId, message } = data.chat;
      // context.commit("addMessageToChat", { chatId, from: 'Santa', message, date: Date.now() });
      Socket.route(`chat/${chatId}/message`, { message });
    },
    onSocketMessage(context, data) {
      if (data.type === "Chat") {
        if (data.chat === "join") {
          const { chatId, user, users } = data;
          if (user) {
            context.commit("addUserToChat", { chatId, user });
          } else if (users) {
            context.commit("addChat", { chatId, users });
          }
        }
        if (data.chat === "leave") {
          context.commit("removeChat", data);
        }
        if (data.chat === "left") {
          context.commit("removeUserFromChat", data);
        }
        if (data.chat === "message") {
          context.commit("addMessageToChat", data);
        }
      }
    }
  }
};

export default chatStore;
