import Vue from "vue";
import Router from "vue-router";
import StartScreen from "@/components/StartScreen";
import TestScreen from "@/components/TestScreen";
import PlayLocalGame from "@/components/PlayLocalGame";
import PlayLocalGameMenu from "@/components/PlayLocalGameMenu";
import StatsScreen from "@/components/stats/StatsScreen";
import TVScreen from "@/components/TVScreen";
import ServerSelection from "@/components/ServerSelection";
import InviteScreen from "@/components/invites/InviteScreen";
import InviteCreateNew from "@/components/invites/InviteCreateNew";

import VueAxios from "vue-axios";
import VueAuthenticate from "vue-authenticate";
import axios from "axios";
import Clipboard from "v-clipboard";
import supportedGames from "../supportedGames";
import ReplayScreen from "@/components/replays/GameReplay"
import md5 from "md5"

Vue.use(VueAxios, axios);
let authConfig = {
  baseUrl: process.env.VUE_APP_AUTH_API_URL,
  providers: {
    github: {
      clientId: process.env.VUE_APP_AUTH_CLIENT_ID_GITHUB,
      redirectUri: process.env.VUE_APP_AUTH_REDIRECT_URL
    },
    google: {
      clientId: process.env.VUE_APP_AUTH_CLIENT_ID_GOOGLE,
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
      path: "/games/:gameTypeIgnored/:gameId/replay",
      name: "ReplayScreen",
      component: ReplayScreen,
      props: route => ({
        gameUUID: route.params.gameId
      })
    },
    {
      path: "/stats/games/:gameId/replay", // For backwards compatibility
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
        let game = supportedGames.games[route.params.gameType];
        if (game.dsl && game.dsl !== true) {
          let setup = new supportedGames.gamejs.net.zomis.games.dsl.impl.GameSetupImpl(game.dsl)
          let min = setup.playersCount.first
          let max = setup.playersCount.last
          playerCount = min + Math.floor(Math.random() * (max - min + 1));
          console.log(`Random from ${min} to ${max} = ${playerCount}`)
        }
        let players = new Array(playerCount).fill(0).map((_, index) => index).map(i => ({
          index: i,
          id: `local:00000000-0000-0000-0000-00000000000` + i.toString(16),
          name: "Player " + (i + 1),
          picture: `https://www.gravatar.com/avatar/${md5(i)}?s=128&d=identicon`
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
      path: "/games/:gameType/new",
      name: "InvitePrepare",
      component: InviteCreateNew,
      props: route => {
        console.log(route)
        return {
          gameType: route.params.gameType,
          defaultConfig: route.params.defaultConfig
        }
      }
    },
    {
      path: "/invites/:inviteId/",
      name: "InviteScreen",
      component: InviteScreen,
      props: route => ({
        inviteId: route.params.inviteId
      })
    },
    ...supportedGames.routes()
  ]
});
