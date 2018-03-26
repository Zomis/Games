import Vue from "vue";
import Router from "vue-router";
import HelloWorld from "@/components/HelloWorld";
import VueWebsocket from "vue-websocket";
import StartScreen from "@/components/StartScreen";
import RoyalGameOfUR from "@/components/RoyalGameOfUR";

Vue.use(Router);
//Vue.use(VueWebsocket, "ws://127.0.0.1:8081");

export default new Router({
  routes: [
    {
      path: "/",
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
        yourIndex: route.query.playerIndex
      })
    }
  ]
});
