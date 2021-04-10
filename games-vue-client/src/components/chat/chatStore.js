import Socket from "@/socket";

// TODO make state.chats into an object like DslGameState
const chatStore = {
  namespaced: true,
  state: {
    chats: [
      {
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
      {
        chatId: 'game/UR',
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
      {
        chatId: 'game/Dixit',
        users: [
          { "playerId": "123456", "name": "JKR" },
          { "playerId": "123", "name": "Zomis" },
        ],
        messages: [
          { message: 'Hello Zomis', from: "JKR", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
          { message: 'Hello you!', from: "Zomis", date: Date.now() },
        ],
      }
    ]
  },
  getters: {},
  mutations: {
    addChat(state, data) {
      const { chatId, users } = data;
      state.chats = [
        ...state.chats,
        { chatId, users }
      ];
    },
    removeChat(state, data) {
      const { chatId } = data;
      state.chats = state.chats.filter(chat => chat.chatId !== chatId);
    },
    addUserToChat(state, data) {
      const { chatId, user } = data;
      const currentChat = state.chats.find(chat => chat.chatId === chatId);
      state.chats = [
        ...state.chats,
        {
          ...currentChat,
          users: [...currentChat.users, user],
        }
      ]
    },
    removeUserFromChat(state, data) {
      const { chatId, user } = data;
      const currentChat = state.chats.find(chat => chat.chatId === chatId);
      state.chats = [
        ...state.chats,
        {
          ...currentChat,
          users: currentChat.users.filter(existingUser => existingUser.playerId !== user.playerId),
        }
      ]
    },
    addMessageToChat(state, data) {
      const { chatId, message, from } = data;
      const currentChat = state.chats.find(chat => chat.chatId === chatId);
      state.chats = [
        ...state.chats,
        {
          ...currentChat,
          messages: [...currentChat.messages, { message, from }],
        }
      ]
    },
  },
  actions: {
    join(context, data) {
      console.log('CHAT - JOIN - data', data);
      const { chatId } = data.chat;
      Socket.route(`chat/${chatId}/join`, {});
    },
    leave(context, data) {
      console.log('CHAT - LEAVE - data', data);
      const { chatId } = data.chat;
      Socket.route(`chat/${chatId}/leave`, {});
    },
    message(context, data) {
      console.log('CHAT - MESSAGE - data', data);
      const { chatId, message } = data.chat;
      Socket.route(`chat/${chatId}/message`, { message });
    },
    onSocketMessage(context, data) {
      if (data.type === "Chat") {
        if (data.chat === "join") {
          const { chatId, user, users } = data;
          if (user) {
            context.commit("addUserToChat", { chatId, user });
            // { type="Chat", chat="join", chatId="<chatId>", user: { "playerId": "<uuid>", "name": "RandomCat79" } }
          } else if (users) {
            context.commit("addChat", { chatId, users });
            // { type: "Chat", chat: "join", chatId: "<chatId>", users: [{ "playerId": "<uuid>", "name": "RandomCat79" }, { "playerId": "<uuid>", "name": "Zomis" }] }
          }
        }
        if (data.chat === "leave") {
          const { chatId } = data;
          context.commit("removeChat", { chatId });
          // { type: "Chat", chat: "leave", chatId: "<chatId>" }
        }
        if (data.chat === "left") {
          const { chatId, user } = data;
          context.commit("removeUserFromChat", { chatId, user });
          // { type: "Chat", chat: "left", chatId: "<chatId>", user: { "playerId": "<uuid>", "name": "RandomCat79" } }
        }
        if (data.chat === "message") {
          const { chatId, from, message } = data;
          context.commit("addMessageToChat", { chatId, from, message });
          // { type: "Chat", chat: "message", chatId:"<chatId>", from: "<playerId>", message: "<text>" }
        }
      }
    }
  }
};

export default chatStore;
