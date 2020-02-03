import Vue from "vue";

import Socket from "../../socket";
import supportedGames from "@/supportedGames"

const gameStore = {
  namespaced: true,
  state: {
    games: {}
  },
  getters: {},
  mutations: {
    createGame(state, data) {
      Vue.set(state.games, data.gameId, {
        gameInfo: {
          gameType: data.gameType,
          gameId: data.gameId,
          yourIndex: data.yourIndex,
          players: data.players
        },
        gameData: {
          view: {},
          actions: {}
        }
      });
    },
    updateView(state, data) {
      let game = state.games[data.gameId].gameData;
      game.view = data.view
    },
    updateActions(state, data) {
      let game = state.games[data.gameId].gameData;
      let supportedGame = supportedGames.games[data.gameType]

      let actions = {}
      data.actions.forEach(e => {
        let ca = {}
        let actionName = e.first
        actions[actionName] = ca
        e.second.parameters.forEach(actionParam => {
          let key = supportedGame.actions[actionName](actionParam)
          ca[key] = true
        })
        e.second.nextOptions.forEach(actionParam => { // TODO: This might need to be handled differently.
          let key = supportedGame.actions[actionName](actionParam)
          ca[key] = true
        })
      })
      game.actions = actions
    }
  },
  actions: {
    requestView(context, data) {
      Socket.send(`{ "type": "ViewRequest", "gameType": "${data.gameType}", "gameId": "${data.gameId}" }`);
    },
    requestActions(context, data) {
      let game = context.state.games[data.gameId]
      Socket.send(`{ "type": "ActionListRequest", "gameType": "${data.gameType}", "gameId": "${data.gameId}", "playerIndex": ${game.gameInfo.yourIndex} }`);
    },
    onSocketMessage(context, data) {
      if (data.type === "GameStarted") {
        context.commit("createGame", data);
      }
      if (data.type === "GameView") {
        context.commit("updateView", data);
      }
      if (data.type === "ActionList") {
        context.commit("updateActions", data);
      }
      if (data.type === "GameMove") {
        this.dispatch('DslGameState/requestView', data)
        this.dispatch('DslGameState/requestActions', data)
      }
    }
  }
};

export default gameStore;
