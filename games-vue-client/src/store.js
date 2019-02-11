import Vue from "vue";
import Vuex from "vuex";
import Connect4state from "./components/games/Connect4state";
import URstate from "./components/RoyalGameOfURstate";
import UTTTstate from "./components/games/UTTTstate";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  state: {
    loginName: null,
    lobby: {}, // key: gameType, value: array of players (names)
    invites: [],
    games: [] // includes both playing and observing
  },
  modules: { Connect4: Connect4state, UR: URstate, UTTT: UTTTstate },
  getters: {
    activeGames: state => {
      let modules = [state.Connect4, state.UR, state.UTTT];
      return modules
        .flatMap(m => m.games)
        .map(i => Object.values(i))
        .flat();
    }
  },
  mutations: {
    setPlayerName(state, name) {
      state.loginName = name;
    },
    setLobbyUsers(state, data) {
      state.lobby = data;
    },
    changeLobby(state, e) {
      // client, action, gameTypes
      let user = e.client;
      if (e.action === "joined") {
        let gameTypes = e.gameTypes;
        gameTypes.forEach(gt => {
          let list = state.lobby[gt];
          if (list == null) throw "No list for " + gt;
        });
        gameTypes.map(gt => state.lobby[gt]).forEach(list => list.push(user));
      } else if (e.action === "left") {
        let gameTypes = Object.keys(state.lobby);
        gameTypes.map(gt => state.lobby[gt]).forEach(list => {
          let index = list.indexOf(user);
          if (index >= 0) {
            list.splice(index, 1);
          }
        });
      } else {
        throw "Unknown action: " + e.action;
      }
    },
    addInvite(state, invite) {
      state.count++;
    }
  },
  actions: {
    observe(context, gameInfo) {
      context.commit(`${gameInfo.gameType}/createGame`, gameInfo);
    },
    onSocketMessage(context, data) {
      console.log(data);
      if (data.type == "Auth") {
        context.commit("setPlayerName", data.name);
      }
      if (data.type == "Lobby") {
        context.commit("setLobbyUsers", data.users);
      }
      if (data.type === "LobbyChange") {
        context.commit("changeLobby", data);
      }
      if (data.gameType === "Connect4") {
        context.dispatch("Connect4/onSocketMessage", data);
      }
      if (data.gameType === "UTTT") {
        context.dispatch("UTTT/onSocketMessage", data);
      }
      if (data.gameType === "UR") {
        context.dispatch("UR/onSocketMessage", data);
      }
    }
  }
});

export default store;
