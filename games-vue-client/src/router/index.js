import Vue from "vue";
import Router from "vue-router";
import StartScreen from "@/components/StartScreen";
import ServerSelection from "@/components/ServerSelection";
import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import Connect4 from "@/components/games/Connect4";
import UTTT from "@/components/games/UTTT";

import VueAxios from "vue-axios";
import VueAuthenticate from "vue-authenticate";
import axios from "axios";

Vue.use(VueAxios, axios);
Vue.use(VueAuthenticate, {
  baseUrl: "http://games.zomis.net:42638", // Your API domain
  providers: {
    github: {
      clientId: "ec9c694603f523bc6de8",
      redirectUri: "http://games.zomis.net/"
    }
  }
});

Vue.use(Router);

export default new Router({
  routes: [
    {
      path: "/",
      name: "ServerSelection",
      component: ServerSelection
    },
    {
      path: "/connected",
      name: "StartScreen",
      component: StartScreen
    },
    {
      path: "/games/UR/:gameId/",
      name: "RoyalGameOfUR",
      component: RoyalGameOfUR,
      props: route => ({
        game: "UR",
        gameId: route.params.gameId,
        players: route.params.players,
        yourIndex: route.params.playerIndex
      })
    },
    {
      path: "/games/Connect4/:gameId/",
      name: "Connect4",
      component: Connect4,
      props: route => ({
        game: "Connect4",
        gameId: route.params.gameId,
        players: route.params.players,
        yourIndex: route.params.playerIndex
      })
    },
    {
      path: "/games/UTTT/:gameId/",
      name: "UTTT",
      component: UTTT,
      props: route => ({
        game: "UTTT",
        gameId: route.params.gameId,
        players: route.params.players,
        yourIndex: route.params.playerIndex
      })
    }
  ]
});
