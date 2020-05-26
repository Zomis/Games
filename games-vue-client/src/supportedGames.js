let gamejs = require("../../games-js/web/games-js");
if (typeof gamejs["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  gamejs = gamejs["games-js"];
}
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
import Hanabi from "@/components/games/Hanabi";
import HanabiConfig from "@/components/games/hanabi/HanabiConfig";
import Splendor from "@/components/games/splendor/Splendor";
import TreeViewGame from "@/components/games/TreeViewGame";
import ECSGame from "@/components/ecs/ECSGame";
import DSLTTT from "@/components/games/DSLTTT";
import TTT3D from "@/components/games/TTT3D";

import DslGameState from "./components/games/DslGameState";
const defaultRouteProps = route => ({
    gameInfo: route.params.gameInfo,
    showRules: true
})

const xyAction = (data) => data.x + ',' + data.y
const tttActions = {
    play: xyAction
}
const tttMoveActions = {
    move: (source) => ({
        key: source.x + ',' + source.y,
        next: (target) => xyAction(target)
    })
}

const splendorActions = {
    buy: (card) => 'buy-' + card,
    buyReserved: (card) => 'buyReserved-' + card,
    discardMoney: (money) => 'discardMoney-' + money,
    takeMoney: (target1) => ({
        key: 'take-' + target1,
        next: (target2) => ({
            key: 'take-' + target2,
            next: (target3) => 'take-' + target3
        })
    }),
    reserve: (card) => 'reserve-' + card
}

const setActions = {
    set: (target1) => ({
        key: 'set-' + target1,
        next: (target2) => ({
            key: 'set-' + target2,
            next: (target3) => 'set-' + target3
        })
    })
}

const supportedGames = {
    "DSL-UR": {
        displayName: "Royal Game of UR",
        dsl: new gamejs.net.zomis.games.dsl.DslUR().gameUR,
        actions: {
            roll: () => "roll",
            move: (i) => `${i}`
        },
        component: RoyalGameOfUR,
        routeProps: defaultRouteProps
    },
    "Hanabi": {
        displayName: "Hanabi",
        dsl: gamejs.net.zomis.games.impl.HanabiGame.game,
        configComponent: HanabiConfig,
        actions: {
            Play: (index) => "play-" + index,
            Discard: (index) => "discard-" + index,
            GiveClue: (playerIndex) => ({
                key: 'player-' + playerIndex,
                next: (mode) => ({
                    key: mode,
                    next: (hintValue) => `${mode}-${hintValue}`
                })
            })
        },
        component: Hanabi,
        routeProps: defaultRouteProps
    },
    "Set": {
        dsl: true,
        enabled: false,
        actions: setActions,
        component: TreeViewGame,
        routeProps: defaultRouteProps
    },
    "Splendor": {
        dsl: gamejs.net.zomis.games.dsl.DslSplendor.splendorGame,
        enabled: true,
        actions: splendorActions,
        component: Splendor,
        routeProps: defaultRouteProps
    },
    "DSL-Connect4": {
        displayName: "Connect Four",
        dsl: new gamejs.net.zomis.games.dsl.DslTTT().gameConnect4,
        actions: tttActions,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "Quixo": {
        displayName: "Quixo",
        dsl: new gamejs.net.zomis.games.dsl.sourcedest.TTSourceDestinationGames().gameQuixo,
        actions: tttMoveActions,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "Skull": {
        dsl: gamejs.net.zomis.games.impl.SkullGame.game,
        enabled: false,
        actions: {
            play: (index) => "play-" + index,
            bet: (index) => "bet-" + index,
            pass: () => "pass",
            choose: (index) => "choose-" + index
        },
        component: TreeViewGame
    },
    "Dungeon Mayhem": {
        dsl: true,
        enabled: false,
        actions: {
            play: (index) => "play-" + index,
            target: (index) => "target-" + index
        },
        component: TreeViewGame
    },
    "Artax": {
        displayName: "Artax",
        dsl: gamejs.net.zomis.games.dsl.sourcedest.ArtaxGame.gameArtax,
        actions: tttMoveActions,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "DSL-Reversi": {
        displayName: "Reversi",
        dsl: new gamejs.net.zomis.games.dsl.DslTTT().gameReversi,
        actions: tttActions,
        component: DSLTTT,
        routeProps: defaultRouteProps
    },
    "DSL-TTT3D": {
        displayName: "3D Tic-Tac-Toe / Connect Four",
        dsl: new gamejs.net.zomis.games.dsl.DslTTT3D().game,
        actions: tttActions,
        component: TTT3D,
        routeProps: defaultRouteProps
    },
    "DSL-UTTT": {
        displayName: "Tic-Tac-Toe Ultimate",
        dsl: new gamejs.net.zomis.games.dsl.DslTTT().gameUTTT,
        actions: tttActions,
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
        displayName: "Tic-Tac-Toe",
        dsl: new gamejs.net.zomis.games.dsl.DslTTT().game,
        actions: tttActions,
        component: DSLTTT,
        routeProps: defaultRouteProps
    }
}

function enabledGames() {
    return Object.keys(supportedGames).filter(key => supportedGames[key].enabled !== false)
}

export default {
    gamejs: gamejs,
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
            let routeProps = game.routeProps || defaultRouteProps
            return {
                path: `/games/${key}/:gameId`,
                name: game.routeName || key,
                component: PlayGame,
                props: route => ({
                    gameType: key,
                    gameId: route.params.gameId,
                    ...routeProps(route)
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
    actionInfo(supportedGame, actionName, actionInfo, actionChoice) {
        console.log("actionInfo", supportedGame, actionName, actionInfo, actionChoice)
        function resolveActionPath(actionName, actionChoice, value) {
            let actionKeys = supportedGame.actions[actionName]
            if (actionChoice === null) {
                actionChoice = { choices: [] };
            }
            let choices = [...actionChoice.choices]
            let a = actionKeys;
            for (let i = 0; i < choices.length; i++) {
                a = a(choices[i]); // Returns either string or { key: string, next: function }
                if (typeof a === 'object' && a.next) {
                    a = a.next
                }
            }
            if (typeof a !== 'function') {
                console.error("'a' is not a function", a, actionName, actionInfo, actionChoice)
                //return a;
                throw a;
            }
            a = a(value)
            if (typeof a === 'object') return { ...a, value: value, direct: false }
            return { key: a, value: value, direct: choices.length === 0 }
        }
    
        if (actionChoice && actionName != actionChoice.actionName) {
            return null
        }

        let ca = {}
        console.log("ACTION INFO FOR", actionName, actionChoice)
        console.log("ACTION INFO", actionInfo)
        actionInfo.nextOptions.forEach(value => {
            let key = resolveActionPath(actionName, actionChoice, value)
            ca[key.key] = key
            console.log("POSSIBLE NEXT", key)
        })
        if (actionInfo.nextOptions.length > 0) {
            console.log("RETURN OPTIONS", ca)
            return ca;
        }

        actionInfo.parameters.forEach(value => {
            let key = resolveActionPath(actionName, actionChoice, value)
            ca[key.key] = { ...key, final: true }
            console.log("POSSIBLE PARAM", key)
        })
        console.log("RETURN PARAM", ca)
        return ca
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
