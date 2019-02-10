import Vue from "vue";
import Vuex from "vuex";
import Connect4state from "./components/games/Connect4state";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  state: {
    loginName: null,
    lobby: {}, // key: gameType, value: array of players (names)
    invites: [],
    games: [] // includes both playing and observing
  },
  modules: { Connect4: Connect4state },
  getters: {
    activeGames: state => {
      return state.games.length;
    }
  },
  mutations: {
    setPlayerName(state, name) {
      state.loginName = name;
    },
    setLobbyUsers(state, data) {
      state.lobby = data;
    },
    addInvite(state, invite) {
      state.count++;
    }
  },
  actions: {
    onSocketMessage(context, data) {
      console.log(data);
      if (data.type == "Auth") {
        context.commit("setPlayerName", data.name);
      }
      if (data.type == "Lobby") {
        context.commit("setLobbyUsers", data.users);
      }
      if (data.gameType === "Connect4") {
        context.dispatch("Connect4/onSocketMessage", data);
      }
    }
  }
});

export default store;
