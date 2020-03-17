import Vue from "vue";
import Vuex from "vuex";
import supportedGames from "@/supportedGames"
import Socket from "@/socket";
import lobbyStore from "./components/lobby/lobbyStore";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  modules: {
    lobby: lobbyStore,
    ...supportedGames.storeModules()
  },
  getters: {
    activeGames: state => {
      let modules = supportedGames.stateModules(state); // TODO: What about DSL-games where multiple game types can have the same store? GameId Collision?
      return modules
        .flatMap(m => m.games)
        .map(i => Object.values(i))
        .flat();
    }
  },
  actions: {
    observe(context, gameInfo) {
      context.commit(`${gameInfo.gameType}/createGame`, gameInfo);
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
    onSocketMessage(context, data) {
      if (data.type == "Auth") {
        context.commit("lobby/setPlayer", data);
      }
      if (data.type.startsWith("Invite") || data.type.startsWith("Lobby")) {
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
