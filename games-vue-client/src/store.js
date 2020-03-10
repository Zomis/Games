import Vue from "vue";
import Vuex from "vuex";
import supportedGames from "@/supportedGames"
import Socket from "@/socket";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  state: {
    loginName: null,
    lobby: {}, // key: gameType, value: array of players (names)
    invites: [],
    games: [] // includes both playing and observing
  },
  modules: {
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
    addInvite(state) { // other parameter: invite
      state.count++;
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
      console.log("STORE IN", data);
      if (data.type == "Auth") {
        context.commit("setPlayerName", data.name);
      }
      if (data.type == "Lobby") {
        context.commit("setLobbyUsers", data.users);
      }
      if (data.type === "LobbyChange") {
        context.commit("changeLobby", data);
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
