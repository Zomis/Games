/*
Edit just one file instead of editing all the following files:
- route/index.js
- StartScreen.vue
- Invites.vue
- store.js
- etc...
*/

import PlayGame from "@/components/PlayGame";

import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import UTTT from "@/components/games/UTTT";
import ECSGame from "@/components/ecs/ECSGame";
import DSLTTT from "@/components/games/DSLTTT";
import TTT3D from "@/components/games/TTT3D";

import URstate from "./components/RoyalGameOfURstate";
import DslGameState from "./components/games/DslGameState";

const defaultRouteProps = route => ({
    gameInfo: route.params.gameInfo,
    showRules: true
})

const supportedGames = {
    "UR": {
        enabled: false,
        displayName: "Royal Game of UR",
        routeName: "RoyalGameOfUR",
        dsl: false,
        store: URstate,
        component: RoyalGameOfUR,
        routeProps: defaultRouteProps
    },
    "DSL-UR": {
        enabled: false,
        displayName: "Royal Game of UR",
        dsl: true,
//        component: DSLRoyalGameOfUR,
        routeProps: defaultRouteProps
    },
    "DSL-Connect4": {
        dsl: true,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "DSL-Reversi": {
        dsl: true,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "DSL-TTT3D": {
        dsl: true,
        component: TTT3D,
        routeProps: defaultRouteProps
    },
    "DSL-UTTT": {
        dsl: true,
        component: UTTT,
        routeProps: defaultRouteProps
    },
    "UTTT-ECS": {
        enabled: false,
        dsl: false,
        store: null,
        component: ECSGame,
        routeProps: defaultRouteProps
    },
    "DSL-TTT": {
        dsl: true,
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
                component: PlayGame,
                props: route => ({
                    ...game.routeProps(route)
                })
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
