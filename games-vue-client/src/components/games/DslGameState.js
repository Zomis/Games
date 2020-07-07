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
        highlights: [],
        actionLog: [],
        gameData: {
          eliminations: [],
          view: {},
          actionChoice: null, // actionName, choices
          actionTypes: [],
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
    setHighlight(state, data) {
      let game = state.games[data.gameInfo.gameId];
      game.highlights = data.highlights;
    },
    addActionLog(state, data) {
      let game = state.games[data.gameId];
      game.actionLog.push(data);
    },
    updateActions(state, data) {
      let game = state.games[data.gameId].gameData;
      let supportedGame = supportedGames.games[data.gameType]
      let actions = {}
      let actionTypes = []
      console.log("UPDATE ACTIONS DSLGAMESTATE", data.actions, supportedGame)
      Object.keys(data.actions).forEach(actionKey => {
        let actionDataList = data.actions[actionKey]
        actionDataList.forEach(actionData => {
          actions[supportedGames.resolveActionKey(supportedGame, actionData, game.actionChoice)] = actionData
          actionTypes.push(actionData.actionType);
        })
      });
      console.log("UPDATE ACTIONS RESULT", actions)
      game.actionTypes = actionTypes;
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
    performChosenAction(context, data) {
      let game = context.state.games[data.gameInfo.gameId];
      let gameData = game.gameData
      let obj = {
        moveType: gameData.actionChoice.actionName,
        playerIndex: data.gameInfo.yourIndex,
        chosen: gameData.actionChoice.choices,
        perform: true
      }
      Socket.route(`games/${data.gameInfo.gameType}/${data.gameInfo.gameId}/action`, obj);
    },
    requestView(context, data) {
      Socket.route(`games/${data.gameType}/${data.gameId}/view`, {});
    },
    resetActionsTo(context, data) {
      context.commit("resetActions", { gameInfo: data.gameInfo });
      context.dispatch("nextAction", data);
    },
    resetActions(context, data) { // TODO: Rename to clearActions
      context.commit("resetActions", { gameInfo: data.gameInfo });
      context.dispatch("requestActions", { gameInfo: data.gameInfo });
    },
    joinGame(context, data) {
      Socket.route(`games/${data.gameType}/${data.gameId}/join`, {})
    },
    requestActions(context, data) {
      let game = context.state.games[data.gameInfo.gameId];
      let actionChoice = game.gameData.actionChoice
      let obj = {
        playerIndex: game.gameInfo.yourIndex,
        chosen: actionChoice !== null ? actionChoice.choices : []
      };
      if (obj.playerIndex < 0) {
        return // Observers don't need actions
      }

      if (actionChoice && actionChoice.actionName) { obj.moveType = actionChoice.actionName }

      Socket.route(`games/${data.gameInfo.gameType}/${data.gameInfo.gameId}/actionList`, obj);
    },
    highlight(context, data) {
      context.commit("setHighlight", data);
    },
    onSocketMessage(context, data) {
      if (data.type === "GameStarted") {
        context.commit("createGame", data);
      }
      if (data.type == "PlayerEliminated") {
        context.commit("elimination", data);
      }
      if (data.type === "GameInfo") {
        // GameStarted also does other things (changes route for example), which is why this is separate.
        context.commit("createGame", data);
      }
      if (data.type === "GameView") {
        context.commit("updateView", data);
      }
      if (data.type === "ActionLog") {
        context.commit("addActionLog", data);
      }
      if (data.type === "ActionList") {
        context.commit("updateActions", data);
      }
      if (data.type === "GameInfo" || data.type === "GameMove") {
        context.commit("resetActions", { gameInfo: data })
        context.dispatch('requestView', data)
        context.dispatch('requestActions', { gameInfo: data })
      }
    }
  }
};

export default gameStore;
