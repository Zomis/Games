import Socket from "@/socket";

const chatStore = {
  namespaced: true,
  state: {
    chats: {
      'server': {
        chatId: 'server',
        users: [
          { "playerId": "431", "name": "RandomCat79" },
          { "playerId": "123", "name": "Zomis" }
        ],
        messages: [
          { message: 'Hello', from: "RandomCat79", date: Date.now() },
          { message: 'Hello there!', from: "Zomis", date: Date.now() },
        ],
      },
      'game-UR': {
        chatId: 'game-UR',
        users: [
          { "playerId": "431", "name": "RandomCat79" },
          { "playerId": "123", "name": "Zomis" },
          { "playerId": "444", "name": "Urmakaren" },
        ],
        messages: [
          { message: 'Hello UR', from: "RandomCat79", date: Date.now() },
          { message: 'Hello you!', from: "Urmakaren", date: Date.now() },
        ],
      },
      'game-Dixit': {
        chatId: 'game-Dixit',
        users: [
          { "playerId": "123456", "name": "JKR" },
          { "playerId": "123", "name": "Zomis" },
        ],
        messages: [
          { message: 'Hello Zomis', from: "JKR", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you2!', from: "Zomis", date: Date.now() },
          { message: 'Hello you3!', from: "Zomis", date: Date.now() },
          { message: 'Hello you4!', from: "Zomis", date: Date.now() },
          { message: 'Hello you5!', from: "Zomis", date: Date.now() },
          { message: 'Hello you6!', from: "Zomis", date: Date.now() },
          { message: 'Hello you7!', from: "Zomis", date: Date.now() },
          { message: 'Hello you8!', from: "Zomis", date: Date.now() },
          { message: 'Hello you9!', from: "Zomis", date: Date.now() },
          { message: 'Hello you10!', from: "Zomis", date: Date.now() },
          { message: 'Hello you11!', from: "Zomis", date: Date.now() },
          { message: 'Hello you12!', from: "Zomis", date: Date.now() },
        ],
      }
    }
  },
  getters: {
    chats: state => { return state.chats }
  },
  mutations: {
    addChat(state, data) {
      const { chatId, users } = data;
      state.chats[chatId] = { chatId, users }
    },
    removeChat(state, data) {
      const { chatId } = data;
      delete state.chats[chatId];
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
