import Vue from "vue";

let uttt = window["uttt-js"];

function globalToBoardTile(position) {
  return {
    boardIndex: Math.floor(position.x / 3) + Math.floor(position.y / 3) * 3,
    tileIndex: Math.floor(position.x % 3) + Math.floor(position.y % 3) * 3
  };
}

const gameStore = {
  namespaced: true,
  state: {
    games: {}
  },
  getters: {},
  mutations: {
    createGame(state, data) {
      Vue.set(state.games, data.gameId, {
        component: "UTTT",
        props: {
          game: data.gameType, // Deprecated
          gameType: data.gameType,
          gameId: data.gameId,
          yourIndex: data.yourIndex,
          players: data.players
        },
        gameData: {
          board: new uttt.net.zomis.tttultimate.games.TTControllers.ultimateTTT(),
          movesMade: 0,
          gamePieces: []
        }
      });
    },
    makeMove(state, event) {
      let game = state.games[event.gameId].gameData;
      game.movesMade++;

      let boardTile = globalToBoardTile(event.move);

      game.board.play_vux9f0$(event.move.x, event.move.y);
      game.gamePieces.push({
        x: event.move.x,
        y: event.move.y,
        player: event.player,
        boardIndex: boardTile.boardIndex,
        tileIndex: boardTile.tileIndex
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
