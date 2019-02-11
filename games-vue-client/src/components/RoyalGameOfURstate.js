import Vue from "vue";

var games = require("../../../games-js/web/games-js");
if (typeof games["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  games = games["games-js"];
}

function globalToBoardTile(position) {
  return {
    boardIndex: Math.floor(position.x / 3) + Math.floor(position.y / 3) * 3,
    tileIndex: Math.floor(position.x % 3) + Math.floor(position.y % 3) * 3
  };
}

function mapping(position, playerIndex) {
  let y = playerIndex == 0 ? 0 : 2;
  if (position > 4 && position < 13) {
    y = 1;
  }
  let x = 0;
  if (y == 1) {
    x = position - 5;
  } else {
    x = position <= 4 ? 4 - position : 4 + 8 + 8 - position;
  }
  return {
    x: x,
    y: y,
    player: playerIndex,
    key: playerIndex + "_" + position,
    position: position
  };
}
function piecesToObjects(array, playerIndex) {
  let playerPieces = array[playerIndex].filter(i => i > 0 && i < 15);
  return Array.from(playerPieces).map(e => mapping(e, playerIndex));
}

function determinePlayerPieces(ur) {
  let pieces = ur.piecesCopy;
  let gamePieces = ur.piecesCopy;
  let obj0 = piecesToObjects(pieces, 0);
  let obj1 = piecesToObjects(pieces, 1);
  let result = [];
  for (var i = 0; i < obj0.length; i++) {
    result.push(obj0[i]);
  }
  for (var i = 0; i < obj1.length; i++) {
    result.push(obj1[i]);
  }
  return { gamePieces: gamePieces, playerPieces: result };
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
        component: "RoyalGameOfUR",
        gameInfo: {
          gameType: data.gameType,
          gameId: data.gameId,
          yourIndex: data.yourIndex,
          players: data.players
        },
        gameData: {
          lastRoll: 0,
          gamePieces: [],
          playerPieces: [],
          ur: new games.net.zomis.games.ur.RoyalGameOfUr_init()
        }
      });
    },
    state(state, event) {
      let game = state.games[event.gameId].gameData;
      if (typeof event.roll !== "undefined") {
        let rollValue = event.roll;
        game.ur.doRoll_za3lpa$(rollValue);
        game.lastRoll = rollValue;
        console.log("AfterState: " + game.ur.toString());
      }
    },
    calcPlayerPieces(state, pieces) {
      let game = state.games[pieces.gameId].gameData;
      game.playerPieces = pieces.playerPieces;
      game.gamePieces = pieces.gamePieces;
    },
    makeMove(state, event) {
      let game = state.games[event.gameId].gameData;
      if (event.moveType == "move") {
        game.ur.move_qt1dr2$(game.ur.currentPlayer, event.move, game.ur.roll);
      }
    }
  },
  actions: {
    calcPlayerPieces(context, gameId) {
      let game = context.state.games[gameId].gameData;
      let pieces = determinePlayerPieces(game.ur);
      pieces.gameId = gameId;
      context.commit("calcPlayerPieces", pieces);
    },
    onSocketMessage(context, data) {
      if (data.type === "GameStarted") {
        context.commit("createGame", data);
      }
      if (data.type === "GameMove") {
        console.log(`Recieved move: ${data.moveType}: ${data.move}`);
        context.commit("makeMove", data);
        context.dispatch("calcPlayerPieces", data.gameId);
      }
      if (data.type === "GameState") {
        context.commit("state", data);
      }
    }
  }
};

export default gameStore;
