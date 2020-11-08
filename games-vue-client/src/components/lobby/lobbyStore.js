import Socket from "@/socket";
import router from "@/router/index";
import supportedGames from "@/supportedGames"
import Vue from "vue";

const lobbyStore = {
  namespaced: true,
  state: {
    yourPlayer: { name: "(UNKNOWN)", playerId: "UNKNOWN", picture: "UNKNOWN", loggedIn: false },
    inviteViews: {},
    lobby: {} // key: gameType, value: array of players (names)
  },
  getters: {},
  mutations: {
    setPlayer(state, player) {
      state.yourPlayer = {
        name: player.name,
        playerId: player.playerId,
        picture: player.picture,
        loggedIn: true
      }
    },
    logout(state) {
      state.yourPlayer.loggedIn = false;
    },
    setInviteView(state, data) {
      Vue.set(state.inviteViews, data.inviteId, data);
    },
    setLobbyUsers(state, data) {
        state.lobby = data;
    },
    changeLobby(state, e) {
      function updateOrAddPlayer(list, player) {
        let index = list.findIndex(e => e.id === player.id);
        if (index >= 0) {
          list[index] = player;
        } else {
          list.push(player)
        }
      }

      // client, action, gameTypes
      let player = e.player; // { id, name, picture }
      if (e.action === "joined") {
        let gameTypes = e.gameTypes;
        gameTypes.forEach(gt => {
          let list = state.lobby[gt];
          if (list == null) throw "No list for gameType " + gt;
        });
        gameTypes.map(gt => state.lobby[gt]).forEach(list => updateOrAddPlayer(list, player));
      } else if (e.action === "left") {
        let gameTypes = Object.keys(state.lobby);
        gameTypes.map(gt => state.lobby[gt]).forEach(list => {
          let index = list.findIndex(e => e.id === player.id);
          if (index >= 0) {
            list.splice(index, 1);
          }
        });
      } else {
        throw "Unknown action: " + e.action;
      }
    },
    toggleAIUsers(state, hidden) {
      state.lobby = Object.entries(state.lobby).reduce((acc, [gameType, players]) => {
        const updatedPlayers = Object.values(players).map((player) => {
          if (player.name.includes('#AI_')) {
            return { ...player, hidden: !!hidden };
          }
          return player;
        });

        return { ...acc, [gameType]: updatedPlayers };
      }, {});
    },
  },
  actions: {
    inviteView(context, data) {
      Socket.route(`invites/${data.inviteId}/view`, {})
    },
    joinAndList() {
      Socket.route(`lobby/join`, { gameTypes: supportedGames.enabledGameKeys(), maxGames: 1 })
      Socket.route(`lobby/list`, {});
    },

    cancelInvite(context) {
      let inviteId = context.state.inviteWaiting.inviteId
      Socket.route(`invites/${inviteId}/cancel`, {});
    },
    createServerInvite(context) {
      console.log("Create Server Invite");
      Socket.route("invites/start", { gameType: context.state.inviteWaiting.gameType });
    },
    onSocketMessage(context, data) {
      if (data.type == "Lobby") {
        context.commit("setLobbyUsers", data.users);
      }
      if (data.type === "LobbyChange") {
        context.commit("changeLobby", data);
      }

      if (data.type === "GameStarted") {
        let game = supportedGames.games[data.gameType]
        let routeName = game.routeName || data.gameType;
        router.push({
          name: routeName,
          params: {
            gameId: data.gameId
          }
        });
      }
      if (data.type === "InviteView") {
        let existingInvite = context.state.inviteViews[data.inviteId];
        context.commit("setInviteView", data)
        if (!existingInvite) {
          router.push({
            name: "InviteScreen",
            params: { inviteId: data.inviteId }
          });
        }
      }
      if (data.type == "InvitePrepare") {
        console.log("LobbyStory InvitePrepare Push", data);
        router.push({
          name: "InvitePrepare",
          params: {
            gameType: data.gameType,
            defaultConfig: data.config
          }
        });
      }
    }
  }
};

export default lobbyStore;
