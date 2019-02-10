import Vue from "vue";

const gameStore = {
  namespaced: true,
  state: {
    games: {}
  },
  getters: {},
  mutations: {
    createGame(state, data) {
      Vue.set(state.games, data.gameId, {
        yourIndex: data.yourIndex,
        players: data.players,
        gameData: {
          movesMade: 0,
          gamePieces: []
        }
      });
    },
    makeMove(state, data) {
      let game = state.games[data.gameId].gameData;
      game.movesMade++;
      game.gamePieces.push({
        x: data.move.x,
        y: data.move.y,
        player: data.player
      });
    }
  },
  actions: {
    onSocketMessage(context, data) {
      if (data.type === "GameStarted") {
        context.commit("createGame", data);
      }
      if (data.type === "GameMove") {
        console.log(`Recieved move: ${data.moveType}: ${data.move}`);
        context.commit("makeMove", data);
      }
    }
  }
};

export default gameStore;
