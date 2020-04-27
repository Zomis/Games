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
          eliminations: [],
          view: {},
          actionChoice: null, // actionName, choices
          actions: {}
        }
      });
    },
    elimination(state, data) {
      let game = state.games[data.gameId].gameData;
      game.eliminations.push(data);
    },
    updateView(state, data) {
      let game = state.games[data.gameId].gameData;
      game.view = data.view
    },
    resetActions(state, data) {
      let game = state.games[data.gameInfo.gameId].gameData;
      game.actionChoice = null;
    },
    addChoice(state, data) {
      let game = state.games[data.gameInfo.gameId].gameData;
      game.actionChoice.choices.push(data.action);
    },
    setChoice(state, data) {
      let game = state.games[data.gameInfo.gameId].gameData;
      game.actionChoice = { actionName: data.name, choices: [data.action] }
    },
    updateActions(state, data) {
      let game = state.games[data.gameId].gameData;
      let supportedGame = supportedGames.games[data.gameType]
      let actions = {}
      data.actions.forEach(e => {
        actions[e.first] = supportedGames.actionInfo(supportedGame, e.first, e.second, game.actionChoice);
      });
      game.actions = actions;
    }
  },
  actions: {
    action(context, data) {
      Socket.route(`games/${data.gameInfo.gameType}/${data.gameInfo.gameId}/move`, {
        moveType: data.name,
        move: data.data
      });
    },
    nextAction(context, data) {
      let game = context.state.games[data.gameInfo.gameId];
      let gameData = game.gameData
      if (gameData.actionChoice !== null && gameData.actionChoice.actionName === data.name) {
        context.commit("addChoice", data)
      } else {
        context.commit("setChoice", data)
      }
      let obj = {
        moveType: data.name,
        playerIndex: data.gameInfo.yourIndex,
        chosen: gameData.actionChoice.choices
      }
      Socket.route(`games/${data.gameInfo.gameType}/${data.gameInfo.gameId}/action`, obj);
    },
    requestView(context, data) {
      Socket.route(`games/${data.gameType}/${data.gameId}/view`, {});
    },
    resetActions(context, data) {
      context.commit("resetActions", { gameInfo: data.gameInfo });
      this.dispatch("DslGameState/requestActions", { gameInfo: data.gameInfo });
    },
    requestActions(context, data) {
      let game = context.state.games[data.gameInfo.gameId];
      let actionChoice = game.gameData.actionChoice
      let obj = {
        playerIndex: game.gameInfo.yourIndex,
        chosen: actionChoice !== null ? actionChoice.choices : []
      };
      if (actionChoice && actionChoice.actionName) { obj.moveType = actionChoice.actionName }

      Socket.route(`games/${data.gameInfo.gameType}/${data.gameInfo.gameId}/actionList`, obj);
    },
    onSocketMessage(context, data) {
      if (data.type === "GameStarted") {
        context.commit("createGame", data);
      }
      if (data.type == "PlayerEliminated") {
        context.commit("elimination", data);
      }
      if (data.type === "GameView") {
        context.commit("updateView", data);
      }
      if (data.type === "ActionList") {
        context.commit("updateActions", data);
      }
      if (data.type === "GameMove") {
        context.commit("resetActions", { gameInfo: data })
        this.dispatch('DslGameState/requestView', data)
        this.dispatch('DslGameState/requestActions', { gameInfo: data })
      }
    }
  }
};

export default gameStore;
