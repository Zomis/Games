import Vue from "vue";
import Router from "vue-router";
import StartScreen from "@/components/StartScreen";
import TestScreen from "@/components/TestScreen";
import TVScreen from "@/components/TVScreen";
import ServerSelection from "@/components/ServerSelection";
import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import Connect4 from "@/components/games/Connect4";
import UTTT from "@/components/games/UTTT";
import ECSGame from "@/components/ecs/ECSGame";
import InviteByURL from "@/components/InviteByURL";

import VueAxios from "vue-axios";
import VueAuthenticate from "vue-authenticate";
import axios from "axios";
import Clipboard from "v-clipboard";

Vue.use(VueAxios, axios);
Vue.use(VueAuthenticate, {
  baseUrl: "http://localhost:42638", // Your API domain
  providers: {
    github: {
      clientId: "2388ed73e48da00d9894",
      redirectUri: "http://localhost:42637/"
    }
  }
});

Vue.use(Router);
Vue.use(Clipboard);

export default new Router({
  routes: [
    {
      path: "/login",
      name: "ServerSelection",
      props: route => ({
        logout: route.params.logout
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
    {
      path: "/games/UR/:gameId/",
      name: "RoyalGameOfUR",
      component: RoyalGameOfUR,
      props: route => ({
        gameInfo: route.params.gameInfo,
        showRules: true
      })
    },
    {
      path: "/games/Connect4/:gameId/",
      name: "Connect4",
      component: Connect4,
      props: route => ({
        gameInfo: route.params.gameInfo,
        showRules: true
      })
    },
    {
      path: "/games/ECSGame/:gameId/",
      name: "ECSGame",
      component: ECSGame,
      props: route => ({
        gameInfo: route.params.gameInfo,
        showRules: true
      })
    },
    {
      path: "/games/UTTT/:gameId/",
      name: "UTTT",
      component: UTTT,
      props: route => ({
        gameInfo: route.params.gameInfo,
        showRules: true
      })
    }
  ]
});
