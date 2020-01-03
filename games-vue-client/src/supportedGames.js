/*
Edit just one file instead of editing all the following files:
- route/index.js
- StartScreen.vue
- Invites.vue
- store.js
- etc...
*/

import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import Connect4 from "@/components/games/Connect4";
import UTTT from "@/components/games/UTTT";
import ECSGame from "@/components/ecs/ECSGame";
import DSLTTT from "@/components/games/DSLTTT";

import Connect4state from "./components/games/Connect4state";
import URstate from "./components/RoyalGameOfURstate";
import UTTTstate from "./components/games/UTTTstate";
import DslGameState from "./components/games/DslGameState";

const defaultRouteProps = route => ({
    gameInfo: route.params.gameInfo,
    showRules: true
})

const supportedGames = {
    "UR": {
        displayName: "Royal Game of UR",
        routeName: "RoyalGameOfUR",
        dsl: false,
        store: URstate,
        component: RoyalGameOfUR,
        routeProps: defaultRouteProps
    },
    "Connect4": {
        dsl: false,
        store: Connect4state,
        component: Connect4,
        routeProps: defaultRouteProps
    },
    "UTTT": {
        dsl: false,
        store: UTTTstate,
        component: UTTT,
        routeProps: defaultRouteProps
    },
    "UTTT-ECS": {
        dsl: false,
        store: null,
        component: ECSGame,
        routeProps: defaultRouteProps
    },
    "DSL-TTT": {
        dsl: true,
        store: DslGameState,
        component: DSLTTT,
        routeProps: defaultRouteProps
    }
}

function enabledGames() {
    return Object.keys(supportedGames).filter(key => supportedGames[key].enabled !== false)
}

export default {
    storeModules() {
        let modules = {}
        for (let key of enabledGames()) {
            let game = supportedGames[key]
            if (game.dsl) {
                modules["DslGameState"] = DslGameState
            } else if (game.store) {
                modules[key] = game.store
            }
        }
        return modules
    },
    routes() {
        return enabledGames().map(key => {
            let game = supportedGames[key]
            return {
                path: `/games/${key}/:gameId`,
                name: game.routeName || key,
                component: game.component,
                props: game.routeProps
            }
        })
    },
    components() {
        let components = {}
        for (let key of enabledGames()) {
            let game = supportedGames[key]
            let componentName = game.component.name
            components[componentName] = game.component
        }
        return components
    },
    stateModules(state) {
        let modules = this.storeModules()
        return Object.keys(modules).map(moduleName => state[moduleName]);
    },
    enabledGameKeys() {
        return enabledGames()
    },
    games: supportedGames
}
