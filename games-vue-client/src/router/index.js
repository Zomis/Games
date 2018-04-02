import Vue from "vue";
import Router from "vue-router";
import HelloWorld from "@/components/HelloWorld";
import StartScreen from "@/components/StartScreen";
import ServerSelection from "@/components/ServerSelection";
import RoyalGameOfUR from "@/components/RoyalGameOfUR";

import VueAxios from "vue-axios";
import VueAuthenticate from "vue-authenticate";
import axios from "axios";

Vue.use(VueAxios, axios);
Vue.use(VueAuthenticate, {
  baseUrl: "http://gbg.zomis.net:42638", // Your API domain
  providers: {
    github: {
      clientId: "ec9c694603f523bc6de8",
      redirectUri: "http://gbg.zomis.net:42637/"
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
    }
  ]
});
