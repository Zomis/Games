import Vue from "vue";
import Vuex from "vuex";
import supportedGames from "@/supportedGames"
import Socket from "@/socket";
import lobbyStore from "./components/lobby/lobbyStore";
import router from "@/router/index";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  modules: {
    lobby: lobbyStore,
    ...supportedGames.storeModules()
  },
  state: {
    connection: { name: null, url: null, connected: false }
  },
  getters: {
    activeGames: state => {
      let modules = supportedGames.stateModules(state);
      return modules
        .flatMap(m => m.games)
        .map(i => Object.values(i))
        .flat();
    }
  },
  mutations: {
    connected(state, data) {
      if (data) {
        state.connection = { ...data, connected: true };
      } else {
        state.connection.connected = false;
      }
    }
  },
  actions: {
    wall() {
      if (!Socket.isConnected()) {
        return new Promise((resolve) => {
          console.log("NOT CONNECTED, REDIRECTING TO LOGIN")
          let currentRoute = router.currentRoute
          console.log(currentRoute)
          router.push({
            name: "ServerSelection",
            params: { logout: true, redirect: { route: currentRoute, resolve: resolve } }
          });
        })
      }
      return Promise.resolve()
    },
    viewRequest(context, gameInfo) {
      Socket.send(
        `{ "type": "ViewRequest", "gameType": "${
          gameInfo.gameType
        }", "gameId": "${gameInfo.gameId}" }`
      );
    },
    makeMove(context, data) {
      let json = `{ "gameType": "${data.gameInfo.gameType}", "gameId": "${
        data.gameInfo.gameId
      }", "type": "move", "moveType": "${data.moveType}", "move": ${JSON.stringify(
        data.move
      )} }`;
      Socket.send(json);
    },
    onSocketOpened(context, data) {
      context.commit("connected", data);
    },
    onSocketClosed(context) {
      context.commit("connected", false);
    },
    onSocketError(context) {
      context.commit("connected", false);
    },
    onSocketMessage(context, data) {
      if (data.type == "Auth") {
        context.commit("lobby/setPlayer", data);
      }
      if (data.type.startsWith("Invite") || data.type.startsWith("Lobby") || data.type === 'GameStarted') {
        context.dispatch("lobby/onSocketMessage", data);
      }
      if (data.gameType) {
        let game = supportedGames.games[data.gameType]
        if (game.dsl) {
          context.dispatch("DslGameState/onSocketMessage", data);
        } else if (game.store) {
          context.dispatch(data.gameType + "/onSocketMessage", data);
        }
      }
    }
  }
});

export default store;
