import Vue from "vue";

import Socket from "../../socket";
import supportedGames from "@/supportedGames"

const gameStore = {
  namespaced: true,
  state: {
    actionPrevious: [],
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

      function resolveActionKey(actionName, type, value) {
        let actionKeys = supportedGame.actions[actionName]
        if (typeof actionKeys === 'object') {
          actionKeys = actionKeys[type]
        }
        return actionKeys(value)
      }

      let actions = {}
      data.actions.forEach(e => {
        let ca = {}
        let actionName = e.first
        actions[actionName] = ca

        console.log("ACTION INFO FOR", actionName, state.actionPrevious)
        let actionInfo = e.second
        console.log("ACTION INFO", actionInfo)
        actionInfo.nextOptions.forEach(next => {
          let key = resolveActionKey(actionName, "next", next)
          console.log("POSSIBLE NEXT", next, key)
          ca[key] = { next: next }
        })
        actionInfo.parameters.forEach(actionParam => {
          let key = resolveActionKey(actionName, "parameter", actionParam)
          console.log("POSSIBLE PARAM", actionParam, key)
          ca[key] = { parameter: actionParam }
        })
      })
      game.actions = actions
    }
  },
  actions: {
    action(context, data) {
      let json = `{ "gameType": "${data.gameInfo.gameType}", "gameId": "${
        data.gameInfo.gameId
      }", "type": "move", "moveType": "${data.name}", "move": ${JSON.stringify(
        data.data
      )} }`;
      Socket.send(json);
    },
    requestView(context, data) {
      Socket.send(`{ "type": "ViewRequest", "gameType": "${data.gameType}", "gameId": "${data.gameId}" }`);
    },
    requestActions(context, data) {
      let game = context.state.games[data.gameInfo.gameId]
      let moveTypeOptional = data.actionType ? `"moveType": "${data.actionType}",` : ""
      if (!data.chosen) {
        data.chosen = [];
      }
      Socket.send(`{
        "type": "ActionListRequest",
        "gameType": "${data.gameInfo.gameType}",
        "gameId": "${data.gameInfo.gameId}",
        ${moveTypeOptional}
        "playerIndex": ${game.gameInfo.yourIndex},
        "chosen": ${JSON.stringify(data.chosen)}
      }`);
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
        this.dispatch('DslGameState/requestActions', { gameInfo: data })
      }
    }
  }
};

export default gameStore;
