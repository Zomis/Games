import Vue from "vue";
import Router from "vue-router";
import StartScreen from "@/components/StartScreen";
import TestScreen from "@/components/TestScreen";
import PlayLocalGame from "@/components/PlayLocalGame";
import PlayLocalGameMenu from "@/components/PlayLocalGameMenu";
import StatsScreen from "@/components/stats/StatsScreen";
import TVScreen from "@/components/TVScreen";
import ServerSelection from "@/components/ServerSelection";
import InviteByURL from "@/components/InviteByURL";

import VueAxios from "vue-axios";
import VueAuthenticate from "vue-authenticate";
import axios from "axios";
import Clipboard from "v-clipboard";
import supportedGames from "../supportedGames";
import ReplayScreen from "@/components/replays/GameReplay"

Vue.use(VueAxios, axios);
let authConfig = {
  baseUrl: process.env.VUE_APP_AUTH_API_URL,
  providers: {
    github: {
      clientId: process.env.VUE_APP_AUTH_CLIENT_ID_GITHUB,
      redirectUri: process.env.VUE_APP_AUTH_REDIRECT_URL
    }
  }
}

Vue.use(VueAuthenticate, authConfig);

Vue.use(Router);
Vue.use(Clipboard);

export default new Router({
  routes: [
    {
      path: "/login",
      name: "ServerSelection",
      props: route => ({
        logout: route.params.logout,
        redirect: route.params.redirect
      }),
      component: ServerSelection
    },
    {
      path: "/",
      name: "StartScreen",
      component: StartScreen
    },
    {
      path: "/test",
      name: "TestScreen",
      component: TestScreen
    },
    {
      path: "/stats/games/:gameId/replay",
      name: "ReplayScreen",
      component: ReplayScreen,
      props: route => ({
        gameUUID: route.params.gameId
      })
    },
    {
      path: "/stats",
      name: "StatsScreen",
      component: StatsScreen,
      props: route => ({
        players: route.query.players,
        tags: route.query.tags
      })
    },
    {
      path: "/local",
      name: "LocalPlayMenu",
      component: PlayLocalGameMenu,
      props: route => ({
        gameInfo: { gameType: route.query.gameType },
        showRules: true
      })
    },
    {
      path: "/local/:gameType",
      name: "LocalPlay",
      component: PlayLocalGame,
      props: route => {
        let playerCount = 2;
        if (route.params.gameType === 'Artax') {
          playerCount = 2 + Math.floor(Math.random() * 3);
        }
        let players = new Array(playerCount).fill(0).map((_, index) => index).map(i => ({
          index: i,
          name: "Player " + (i + 1),
          picture: null
        }));
        return {
          gameInfo: { gameType: route.params.gameType, players: players },
          showRules: true
        }
      }
    },
    {
      path: "/tv",
      name: "TVScreen",
      component: TVScreen
      // use more specific path to avoid re-using client component?
    },
    {
      path: "/invite/:inviteId/",
      name: "InviteByURL",
      component: InviteByURL,
      props: route => ({
        inviteId: route.params.inviteId,
        server: route.query.server
      })
    },
    ...supportedGames.routes()
  ]
});
