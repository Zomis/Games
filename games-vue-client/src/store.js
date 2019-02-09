import Vue from "vue";
import Vuex from "vuex";

// const debug = process.env.NODE_ENV !== "production";
Vue.use(Vuex);

const store = new Vuex.Store({
  state: {
    playerName: null,
    lobby: [], // key: gameType, value: players

    invites: [],
    activeGame: null,
    games: [] // includes both playing and observing
  },
  getters: {
    activeGames: state => {
      return state.games.length;
    }
  },
  mutations: {
    addInvite(state, invite) {
      state.count++;
    }
  },
  actions: {
    onSocketMessage(context, data) {
      console.log(data);
      if (data.type == "") {
      }
      // context.commit("increment");
    }
  }
});

export default store;
